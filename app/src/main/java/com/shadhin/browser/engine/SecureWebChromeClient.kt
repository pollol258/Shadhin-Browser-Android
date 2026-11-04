package com.shadhin.browser.engine

import android.content.Context
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * WebChromeClient managing progress indicators, page titles, full-screen video,
 * window.open popups, and explicit user permission prompts.
 */
class SecureWebChromeClient(
    private val tabId: String,
    private val onProgressUpdate: (tabId: String, progress: Float) -> Unit,
    private val onTitleUpdate: (tabId: String, title: String) -> Unit,
    private val onShowCustomVideoView: (view: View, callback: CustomViewCallback) -> Unit,
    private val onHideCustomVideoView: () -> Unit,
    private val onRequestUserPermission: (origin: String, resources: Array<String>, grant: () -> Unit, deny: () -> Unit) -> Unit,
    private val onPopupRequested: (targetUrl: String) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressUpdate(tabId, (newProgress / 100f).coerceIn(0f, 1f))
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        if (!title.isNullOrBlank()) {
            onTitleUpdate(tabId, title)
        }
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        super.onShowCustomView(view, callback)
        android.util.Log.d("ShadhinMedia", "WebChromeClient: onShowCustomView for tab $tabId")
        onShowCustomVideoView(view, callback)
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
        android.util.Log.d("ShadhinMedia", "WebChromeClient: onHideCustomView for tab $tabId")
        onHideCustomVideoView()
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val origin = request.origin.toString()
        val resources = request.resources
        onRequestUserPermission(
            origin,
            resources,
            { request.grant(resources) },
            { request.deny() }
        )
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        // Geolocation denied by default for privacy hardening
        callback.invoke(origin, false, false)
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        if (!isUserGesture) return false // Block unsolicited background popups

        val transport = resultMsg?.obj as? WebView.HitTestResult
        val extra = transport?.extra
        if (!extra.isNullOrBlank()) {
            onPopupRequested(extra)
        }
        return false // Handled in parent tab / new tab
    }
}
