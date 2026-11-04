package com.shadhin.browser.engine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.shadhin.browser.filter.CosmeticFilterEngine
import com.shadhin.browser.filter.PrivacyFilterEngine
import java.io.ByteArrayInputStream

/**
 * Privacy-hardened WebViewClient:
 *  - Enforces HTTPS-first upgrade mode.
 *  - Intercepts cleartext HTTP: allows only if temporary per-host exception was granted
 *    or if it's local/LAN with allowLocalHttp enabled. Otherwise upgrades to HTTPS.
 *  - NEVER proceeds on SSL errors (always cancels and displays Titanium Industrial error page).
 *  - Handles onReceivedError and onReceivedHttpError for main frame with themed error page.
 *  - Injects cosmetic ad-blocking CSS rules at document start with zero flicker.
 */
class SecureWebViewClient(
    private val context: Context,
    private val tabId: String,
    private val privacyFilterEngine: PrivacyFilterEngine,
    private val cosmeticFilterEngine: CosmeticFilterEngine,
    private val isCosmeticFilteringEnabled: () -> Boolean,
    private val isHttpsOnlyEnabled: () -> Boolean,
    private val isLocalHttpAllowed: () -> Boolean,
    private val isNetworkDiagnosticsEnabled: () -> Boolean,
    private val getDohProviderName: () -> String,
    private val activeHostProvider: () -> String,
    private val isCleartextAllowedForHost: (host: String) -> Boolean,
    private val onCleartextFallbackRequested: (url: String) -> Unit,
    private val onTrackerBlocked: (tabId: String) -> Unit,
    private val onPageTitleOrUrlChanged: (tabId: String, title: String?, url: String?) -> Unit,
    private val onPageLoadFailed: (tabId: String, failingUrl: String) -> Unit,
    private val onExternalSchemeRequested: (intent: Intent) -> Unit,
    private val onSslErrorEncountered: (url: String, errorDescription: String) -> Unit
) : WebViewClient() {

    private var mainFrameStartTimeMs = 0L
    private val retriedUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase().orEmpty()

        // HTTPS-first enforcement
        if (scheme == "http") {
            val isLocal = UrlNormalizer.isLocalOrLanAddress(host)
            val isAllowedLocal = isLocal && isLocalHttpAllowed()
            val hasTemporaryCleartextException = isCleartextAllowedForHost(host)

            if (!isAllowedLocal && !hasTemporaryCleartextException) {
                // Upgrade to HTTPS
                val upgraded = uri.buildUpon().scheme("https").build()
                view.loadUrl(upgraded.toString())
                return true
            }
        }

        if (scheme == "http" || scheme == "https" || scheme == "about") {
            return false // Let WebView load web protocols directly with its own stack
        }

        // Handle external schemes safely
        try {
            val intent = if (scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            onExternalSchemeRequested(intent)
        } catch (_: Exception) {}
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url?.toString() ?: return null

        // Tracker & Telemetry Blocking with First-Party and Media Allowlists
        val docHost = activeHostProvider()
        if (privacyFilterEngine.shouldBlockUrl(url, docHost)) {
            onTrackerBlocked(tabId)
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                200,
                "OK",
                mapOf("Cache-Control" to "no-cache"),
                ByteArrayInputStream(ByteArray(0))
            )
        }

        // Never re-fetch normal subresources or main frame through OkHttp; return null so WebView's stack handles them
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        mainFrameStartTimeMs = System.currentTimeMillis()
        onPageTitleOrUrlChanged(tabId, view.title, url)

        if (isNetworkDiagnosticsEnabled() && !url.isNullOrBlank()) {
            val cookie = android.webkit.CookieManager.getInstance().getCookie(url)
            val hasCookies = !cookie.isNullOrEmpty()
            android.util.Log.d(
                "ShadhinNet",
                "Navigation started: url=$url, DoH=${getDohProviderName()}, hasCookies=$hasCookies, UA=${view.settings.userAgentString.take(45)}..."
            )
        }

        // Inject cosmetic filter styles early
        if (isCosmeticFilteringEnabled() && !url.isNullOrBlank()) {
            val domain = try { Uri.parse(url).host } catch (_: Exception) { null }
            val script = cosmeticFilterEngine.buildInjectionScript(domain)
            if (script.isNotBlank()) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    try {
                        WebViewCompat.addDocumentStartJavaScript(
                            view,
                            script,
                            setOf("*")
                        )
                    } catch (_: Exception) {
                        view.evaluateJavascript(script, null)
                    }
                } else {
                    view.evaluateJavascript(script, null)
                }
            }
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        if (!url.isNullOrBlank()) {
            retriedUrls.remove(url)
        }
        val elapsed = if (mainFrameStartTimeMs > 0) System.currentTimeMillis() - mainFrameStartTimeMs else 0
        if (isNetworkDiagnosticsEnabled() && !url.isNullOrBlank()) {
            android.util.Log.d(
                "ShadhinNet",
                "Navigation finished: url=$url, elapsed=${elapsed}ms, DoH=${getDohProviderName()}"
            )
        }
        onPageTitleOrUrlChanged(tabId, view.title, url)

        // Inject password autofill observer for login detection
        view.evaluateJavascript(com.shadhin.browser.vault.PasswordAutofillBridge.INJECTION_SCRIPT, null)

        // Post-DOM re-verification for dynamic SPAs
        if (isCosmeticFilteringEnabled() && !url.isNullOrBlank()) {
            val domain = try { Uri.parse(url).host } catch (_: Exception) { null }
            val script = cosmeticFilterEngine.buildInjectionScript(domain)
            if (script.isNotBlank()) {
                view.evaluateJavascript(script, null)
            }
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            val failingUrl = request.url?.toString().orEmpty()
            val errorCode = error.errorCode
            val description = error.description?.toString()
            val elapsed = if (mainFrameStartTimeMs > 0) System.currentTimeMillis() - mainFrameStartTimeMs else 0
            val method = request.method ?: "GET"

            if (isNetworkDiagnosticsEnabled()) {
                android.util.Log.d(
                    "ShadhinNet",
                    "Main-frame error: url=$failingUrl, code=$errorCode, desc=$description, elapsed=${elapsed}ms, DoH=${getDohProviderName()}, method=$method"
                )
            }

            // Silent auto-retry once after 600 ms for main-frame GET failures with network errors
            val isRetryable = errorCode == ERROR_CONNECT || errorCode == ERROR_TIMEOUT ||
                errorCode == ERROR_HOST_LOOKUP || errorCode == -15 /* DISCONNECTED */
            if (method.equals("GET", ignoreCase = true) && isRetryable && !retriedUrls.contains(failingUrl)) {
                retriedUrls.add(failingUrl)
                if (isNetworkDiagnosticsEnabled()) {
                    android.util.Log.d("ShadhinNet", "Scheduling silent auto-retry in 600ms for: $failingUrl")
                }
                view.postDelayed({ view.loadUrl(failingUrl) }, 600)
                return
            }

            retriedUrls.remove(failingUrl)
            onPageLoadFailed(tabId, failingUrl)

            val details = ErrorPageMapper.mapError(errorCode, description, failingUrl)
            val showHttpFallback = details.isHttpsDowngradeCandidate || details.isCleartextBlocked
            val diagDetails = if (isNetworkDiagnosticsEnabled()) {
                "Source: Network / WebView Engine\nCode: $errorCode ($description)\nDoH Provider: ${getDohProviderName()}\nElapsed: ${elapsed}ms\nAuto-retried: true"
            } else null

            val errorHtml = ErrorPageMapper.buildThemedErrorHtml(
                failingUrl = failingUrl,
                details = details,
                showHttpFallbackButton = showHttpFallback,
                diagnosticsDetails = diagDetails
            )

            view.loadDataWithBaseURL(failingUrl, errorHtml, "text/html", "UTF-8", null)
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            val failingUrl = request.url?.toString().orEmpty()
            val statusCode = errorResponse.statusCode
            val elapsed = if (mainFrameStartTimeMs > 0) System.currentTimeMillis() - mainFrameStartTimeMs else 0
            val method = request.method ?: "GET"
            val isProxyError = errorResponse.responseHeaders?.containsKey("X-Shadhin-Proxy-Error") == true

            if (isNetworkDiagnosticsEnabled()) {
                android.util.Log.d(
                    "ShadhinNet",
                    "HTTP Error: url=$failingUrl, status=$statusCode, phrase=${errorResponse.reasonPhrase}, elapsed=${elapsed}ms, isProxy=$isProxyError, DoH=${getDohProviderName()}"
                )
            }

            // Silent auto-retry once after 600 ms for main-frame GET failures with 502/503/504
            if (method.equals("GET", ignoreCase = true) && statusCode in listOf(502, 503, 504) && !retriedUrls.contains(failingUrl)) {
                retriedUrls.add(failingUrl)
                if (isNetworkDiagnosticsEnabled()) {
                    android.util.Log.d("ShadhinNet", "Scheduling silent auto-retry in 600ms for HTTP $statusCode on: $failingUrl")
                }
                view.postDelayed({ view.loadUrl(failingUrl) }, 600)
                return
            }

            retriedUrls.remove(failingUrl)

            // If the server sent a body with a 4xx/5xx, let the site's own page show; use our error page only for empty responses
            val hasBody = try {
                errorResponse.data != null && errorResponse.data.available() > 0
            } catch (_: Exception) {
                false
            }
            if (hasBody) {
                return // Let the website's own error response render
            }

            onPageLoadFailed(tabId, failingUrl)

            val details = ErrorPageMapper.mapHttpError(
                statusCode,
                errorResponse.reasonPhrase
            )
            val diagDetails = if (isNetworkDiagnosticsEnabled()) {
                val source = if (isProxyError) "Local DoH Proxy" else "Origin Server"
                "Source: $source\nHTTP Code: $statusCode (${errorResponse.reasonPhrase})\nDoH Provider: ${getDohProviderName()}\nElapsed: ${elapsed}ms\nAuto-retried: true"
            } else null

            val errorHtml = ErrorPageMapper.buildThemedErrorHtml(
                failingUrl = failingUrl,
                details = details,
                showHttpFallbackButton = false,
                diagnosticsDetails = diagDetails
            )
            view.loadDataWithBaseURL(failingUrl, errorHtml, "text/html", "UTF-8", null)
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError
    ) {
        // STRICT PRIVACY: ALWAYS cancel, NEVER proceed()
        handler.cancel()

        val failingUrl = error.url ?: "Unknown URL"
        val desc = when (error.primaryError) {
            SslError.SSL_UNTRUSTED -> "Certificate is not trusted"
            SslError.SSL_EXPIRED -> "Certificate has expired"
            SslError.SSL_IDMISMATCH -> "Host name mismatch"
            SslError.SSL_NOTYETVALID -> "Certificate is not yet valid"
            else -> "SSL security error"
        }

        onSslErrorEncountered(failingUrl, desc)
        onPageLoadFailed(tabId, failingUrl)

        val details = ErrorPageMapper.ErrorDetails(
            title = "Secure Connection Blocked",
            description = "Shadhin Browser cancelled the connection because the SSL certificate could not be securely verified ($desc).",
            isCleartextBlocked = false,
            isHttpsDowngradeCandidate = true
        )

        val errorHtml = ErrorPageMapper.buildThemedErrorHtml(
            failingUrl = failingUrl,
            details = details,
            showHttpFallbackButton = true,
            diagnosticsDetails = if (isNetworkDiagnosticsEnabled()) "SSL Primary Error: $desc\nDoH Provider: ${getDohProviderName()}" else null
        )

        view.loadDataWithBaseURL(failingUrl, errorHtml, "text/html", "UTF-8", null)
    }
}
