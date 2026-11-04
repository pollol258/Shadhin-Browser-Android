package com.shadhin.browser.engine

import android.webkit.WebViewClient

object ErrorPageMapper {

    data class ErrorDetails(
        val title: String,
        val description: String,
        val isCleartextBlocked: Boolean = false,
        val isHttpsDowngradeCandidate: Boolean = false
    )

    fun mapError(errorCode: Int, description: String?, failingUrl: String?): ErrorDetails {
        val lowerDesc = description?.lowercase().orEmpty()
        val isHttps = failingUrl?.startsWith("https://", ignoreCase = true) == true

        if (lowerDesc.contains("cleartext") || lowerDesc.contains("err_cleartext_not_permitted")) {
            return ErrorDetails(
                title = "Cleartext Connection Blocked",
                description = "Shadhin Browser keeps unencrypted connections disabled by default to protect your privacy and credentials.",
                isCleartextBlocked = true,
                isHttpsDowngradeCandidate = false
            )
        }

        return when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP -> ErrorDetails(
                title = "Server Not Found",
                description = "The server could not be found (ERR_NAME_NOT_RESOLVED). Check the web address for typos or check your internet connection.",
                isCleartextBlocked = false,
                isHttpsDowngradeCandidate = isHttps
            )
            WebViewClient.ERROR_CONNECT -> ErrorDetails(
                title = "Connection Refused",
                description = "The server actively refused the connection (ERR_CONNECTION_REFUSED). The site might be down or not listening on this port.",
                isCleartextBlocked = false,
                isHttpsDowngradeCandidate = isHttps
            )
            WebViewClient.ERROR_TIMEOUT -> ErrorDetails(
                title = "Connection Timed Out",
                description = "The server took too long to respond (ERR_CONNECTION_TIMED_OUT). Check your network connection or try again later.",
                isCleartextBlocked = false,
                isHttpsDowngradeCandidate = isHttps
            )
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> ErrorDetails(
                title = "Secure Connection Failed",
                description = "This site does not support a secure connection or has an invalid SSL configuration.",
                isCleartextBlocked = false,
                isHttpsDowngradeCandidate = isHttps
            )
            -15 /* ERROR_DISCONNECTED */ -> ErrorDetails(
                title = "No Internet Connection",
                description = "Your device appears to be offline (ERR_INTERNET_DISCONNECTED). Please check your Wi-Fi or mobile data.",
                isCleartextBlocked = false,
                isHttpsDowngradeCandidate = false
            )
            else -> ErrorDetails(
                title = "Unable to Load Page",
                description = if (!description.isNullOrBlank()) description else "An unexpected error occurred while communicating with the server.",
                isCleartextBlocked = false,
                isHttpsDowngradeCandidate = isHttps
            )
        }
    }

    fun mapHttpError(statusCode: Int, reasonPhrase: String?): ErrorDetails {
        return when (statusCode) {
            404 -> ErrorDetails(
                title = "Page Not Found (404)",
                description = "The requested page does not exist on this server. Check the URL for spelling errors."
            )
            403 -> ErrorDetails(
                title = "Access Forbidden (403)",
                description = "You do not have permission to access the requested resource."
            )
            500 -> ErrorDetails(
                title = "Internal Server Error (500)",
                description = "The web server encountered an unexpected condition that prevented it from fulfilling the request."
            )
            502 -> ErrorDetails(
                title = "Bad Gateway (502)",
                description = "The server received an invalid response from the upstream server."
            )
            503 -> ErrorDetails(
                title = "Service Unavailable (503)",
                description = "The server is currently unable to handle the request due to maintenance or overload."
            )
            else -> ErrorDetails(
                title = "HTTP Error $statusCode",
                description = if (!reasonPhrase.isNullOrBlank()) reasonPhrase else "The server responded with an HTTP error code."
            )
        }
    }

    /**
     * Renders a custom Warm Glass error page matching Color.kt:
     * Dark Warm Cocoa gradient #18100C -> #241510, Surface #261914, Border #452C22,
     * Text primary #FFF4EE, Secondary #B59E92, Accent Orange #F28C4E.
     * Pure local HTML/CSS with zero external images, CDNs or fonts.
     */
    fun buildThemedErrorHtml(
        failingUrl: String,
        details: ErrorDetails,
        showHttpFallbackButton: Boolean = false,
        diagnosticsDetails: String? = null
    ): String {
        val safeUrl = failingUrl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val safeTitle = details.title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val safeDesc = details.description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        val diagnosticsSectionHtml = if (!diagnosticsDetails.isNullOrBlank()) {
            val safeDiag = diagnosticsDetails.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            """
            <details style="margin-top: 18px; text-align: left; background: #1E130E; border: 1px solid #452C22; border-radius: 12px; padding: 10px 14px; font-size: 11px; color: #B59E92;">
                <summary style="cursor: pointer; color: #F28C4E; font-weight: bold; outline: none;">Network Diagnostics</summary>
                <div style="margin-top: 8px; font-family: monospace; white-space: pre-wrap; word-break: break-all;">$safeDiag</div>
            </details>
            """.trimIndent()
        } else ""

        val fallbackButtonHtml = if (showHttpFallbackButton && failingUrl.startsWith("https://", ignoreCase = true)) {
            val httpUrl = "http://" + failingUrl.substringAfter("https://")
            """
            <button class="btn btn-secondary" onclick="window.__shadhin_bridge__.continueWithHttp('$safeUrl')">
                Continue with HTTP (not secure)
            </button>
            """.trimIndent()
        } else ""

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        background: linear-gradient(180deg, #18100C 0%, #241510 100%);
                        background-color: #18100C;
                        color: #FFF4EE;
                        font-family: 'Nunito', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        padding: 32px 20px;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        text-align: center;
                        -webkit-user-select: none;
                        user-select: none;
                    }
                    .card {
                        background-color: #261914;
                        border: 1px solid #452C22;
                        border-radius: 24px;
                        padding: 32px 24px;
                        max-width: 440px;
                        width: 100%;
                        box-shadow: 0 12px 32px rgba(12, 6, 4, 0.55);
                    }
                    .icon-wrap {
                        width: 72px;
                        height: 72px;
                        border-radius: 50%;
                        background: linear-gradient(135deg, #35231C 0%, #1E130E 100%);
                        border: 1.5px solid #452C22;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin: 0 auto 18px auto;
                        box-shadow: 0 4px 16px rgba(0,0,0,0.4);
                    }
                    .icon-svg {
                        width: 50px;
                        height: 50px;
                    }
                    h1 {
                        font-size: 20px;
                        font-weight: 700;
                        color: #FFF4EE;
                        margin-bottom: 8px;
                        line-height: 1.3;
                    }
                    .url-badge {
                        background-color: #1E130E;
                        border: 1px solid #452C22;
                        border-radius: 10px;
                        padding: 6px 12px;
                        font-family: monospace;
                        font-size: 12px;
                        color: #F28C4E;
                        word-break: break-all;
                        margin-bottom: 14px;
                        display: inline-block;
                        max-width: 100%;
                    }
                    p {
                        color: #B59E92;
                        font-size: 13.5px;
                        line-height: 1.55;
                        margin-bottom: 24px;
                    }
                    .actions {
                        display: flex;
                        flex-direction: column;
                        gap: 12px;
                        width: 100%;
                    }
                    .btn {
                        padding: 13px 18px;
                        border-radius: 14px;
                        font-size: 14px;
                        font-weight: 600;
                        font-family: inherit;
                        cursor: pointer;
                        border: none;
                        outline: none;
                        width: 100%;
                        min-height: 48px;
                        transition: opacity 0.15s ease, transform 0.1s ease;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .btn:active {
                        opacity: 0.85;
                        transform: scale(0.99);
                    }
                    .btn-primary {
                        background-color: #F28C4E;
                        color: #18100C;
                    }
                    .btn-secondary {
                        background-color: #35231C;
                        color: #FFF4EE;
                        border: 1px solid #452C22;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon-wrap">
                        <svg class="icon-svg" viewBox="0 0 108 108">
                            <path d="M 37,70 C 29,62 27.5,47 34,36 C 40.5,25 54,22 65,27.5" fill="none" stroke="#FFF4EE" stroke-width="2.6" stroke-linecap="round"/>
                            <path d="M 74.5,39 C 79.5,48 78,61 69,69" fill="none" stroke="#FFF4EE" stroke-width="2.6" stroke-linecap="round"/>
                            <circle cx="70.5" cy="33.5" r="3.2" fill="#F28C4E"/>
                            <rect x="38.5" y="72.5" width="29" height="9" rx="4.5" fill="rgba(255,244,238,0.2)" stroke="#FFF4EE" stroke-width="1.8"/>
                            <circle cx="62" cy="77" r="2" fill="#F28C4E"/>
                            <path d="M 48.5,42.5 C 46,38 45.5,35 48.5,33 C 50.5,31.5 53,34 54.5,38.5 C 55.5,41.5 55,44 54,45 Z" fill="#FFF4EE"/>
                            <path d="M 62.5,43.5 C 61,42 59.5,42.5 57.5,44 C 54,40 48.5,33 42,32 C 40,31.8 39.5,33 41.5,35.5 C 44.5,39 46.5,43 47.5,46.5 C 45,47.5 41,51.5 35,53.5 C 33.5,54 34.5,55 36.5,55.5 C 41,56.5 45.5,55.5 48.5,53.5 C 50.5,56 54,58 58.5,56 C 62.5,54.5 64,50.5 64,48 C 65.5,47.5 67,47 68.5,46.5 C 69,46.3 68.5,45.5 67,45.2 C 65.5,45 64.2,44.5 62.5,43.5 Z" fill="#FFF4EE"/>
                        </svg>
                    </div>
                    <h1>$safeTitle</h1>
                    <div class="url-badge">$safeUrl</div>
                    <p>$safeDesc</p>
                    <div class="actions">
                        <button class="btn btn-primary" onclick="if (window.__shadhin_bridge__) { window.__shadhin_bridge__.retry(); } else { location.reload(); }">
                            Retry
                        </button>
                        $fallbackButtonHtml
                        <button class="btn btn-secondary" onclick="if (window.__shadhin_bridge__) { window.__shadhin_bridge__.goToStartPage(); } else { location.href='about:blank'; }">
                            Go to start page
                        </button>
                    </div>
                    $diagnosticsSectionHtml
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
