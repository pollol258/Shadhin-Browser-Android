package com.shadhin.browser.engine

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface abstracting TabManager logic so business rules (max 3 live tabs,
 * hibernation, bundle saving on eviction, and tab switching) can be unit-tested
 * without requiring a real native Chromium WebView instance.
 */
interface TabManagerContract {
    val tabs: StateFlow<List<BrowserTab>>
    val activeTabId: StateFlow<String>

    fun openNewTab(url: String = "", title: String = "New Tab"): String
    fun switchTab(tabId: String)
    fun closeTab(tabId: String)
    fun closeAllTabs()
    fun updateTabMetadata(tabId: String, title: String? = null, url: String? = null)
    fun incrementTrackerCount(tabId: String)
    fun setCosmeticRulesCount(tabId: String, count: Int)
    fun setMediaPlaying(tabId: String, isPlaying: Boolean)
    fun setFullScreen(tabId: String, isFullScreen: Boolean)

    // WebView-binding operations (ignored in mocks)
    fun getOrCreateLiveWebView(tabId: String, context: Context): WebView?
    fun detachActiveWebView(): WebView?
    fun recycleInactiveTabs()
    fun pauseAll()
    fun resumeAll()
    fun destroy()
}
