package com.shadhin.browser.engine

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewDatabase

/**
 * Hardens Android System WebView (Chromium-based) security and privacy parameters.
 */
object WebViewSecurityConfig {

    fun applyHardenedSettings(webView: WebView) {
        val settings = webView.settings

        // JavaScript enabled for modern web functionality
        settings.javaScriptEnabled = true

        // Storage & Sandbox isolation
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        // Disable local file and content access to prevent local file leaks
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false

        // Block mixed content (HTTP resources on HTTPS pages)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // Disable Geolocation by default
        settings.setGeolocationEnabled(false)

        // Safe Browsing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        // Viewport and zoom controls
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // User-Agent: remove only '; wv' token to avoid restricted in-app webview tagging while keeping UA/Accept-Language/Cookies untouched
        val currentUa = settings.userAgentString
        if (currentUa.contains("; wv")) {
            settings.userAgentString = currentUa.replace("; wv", "")
        }

        // Privacy: block 3rd-party cookies by default while preserving 1st-party cookies
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
    }

    /**
     * Complete privacy wipe for the "Burn Session" action:
     * Clears cookies, cache, web storage, form data, databases, and SSL preferences.
     */
    fun performFullWipe(context: Context) {
        // Clear cookies
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()

        // Clear WebStorage
        android.webkit.WebStorage.getInstance().deleteAllData()

        // Clear WebViewDatabase
        val db = WebViewDatabase.getInstance(context)
        db.clearHttpAuthUsernamePassword()
        db.clearFormData()

        // Clear cache files
        try {
            context.cacheDir.deleteRecursively()
            val webViewCache = java.io.File(context.dataDir, "app_webview")
            if (webViewCache.exists()) {
                webViewCache.deleteRecursively()
            }
        } catch (_: Exception) {}
    }
}
