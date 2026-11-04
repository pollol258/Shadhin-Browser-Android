package com.shadhin.browser.engine

import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Manages tabs with strict memory recycling:
 *  - Capped to max 3 live WebViews in memory.
 *  - LRU eviction: saves state via webView.saveState(bundle), calls destroy(),
 *    and flags tab as isAlive = false ("Hibernated").
 *  - Rehydration: creates fresh WebView and calls webView.restoreState(bundle).
 *  - Tab list state is stored safely in-memory without exceeding Activity binder limits.
 */
class TabManager(
    private val maxLiveWebViews: Int = 3
) : TabManagerContract {

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    override val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String>("")
    override val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    // Map of live WebViews currently allocated
    private val liveWebViews = mutableMapOf<String, WebView>()

    // LRU access list of tabIds (most recent at end)
    private val lruOrder = mutableListOf<String>()

    init {
        // Start with an initial tab
        openNewTab("", "New Tab")
    }

    override fun openNewTab(url: String, title: String): String {
        val newId = UUID.randomUUID().toString()
        val newTab = BrowserTab(
            id = newId,
            title = if (title.isBlank()) "New Tab" else title,
            url = url,
            isAlive = false
        )

        _tabs.update { it + newTab }
        switchTab(newId)
        return newId
    }

    override fun switchTab(tabId: String) {
        val currentTabs = _tabs.value
        if (currentTabs.none { it.id == tabId }) return

        _activeTabId.value = tabId
        lruOrder.remove(tabId)
        lruOrder.add(tabId)

        recycleInactiveTabs()
    }

    override fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        val closingIndex = currentTabs.indexOfFirst { it.id == tabId }
        if (closingIndex == -1) return

        // Destroy and dispose live WebView
        destroyWebView(tabId)
        lruOrder.remove(tabId)

        val updatedTabs = currentTabs.filterNot { it.id == tabId }

        if (updatedTabs.isEmpty()) {
            _tabs.value = emptyList()
            openNewTab("", "New Tab")
            return
        }

        _tabs.value = updatedTabs

        // If closed active tab, select adjacent tab
        if (_activeTabId.value == tabId) {
            val nextIndex = closingIndex.coerceAtMost(updatedTabs.size - 1)
            val nextTabId = updatedTabs[nextIndex].id
            switchTab(nextTabId)
        }
    }

    override fun closeAllTabs() {
        for (tabId in liveWebViews.keys.toList()) {
            destroyWebView(tabId)
        }
        liveWebViews.clear()
        lruOrder.clear()
        _tabs.value = emptyList()
        openNewTab("", "New Tab")
    }

    override fun updateTabMetadata(tabId: String, title: String?, url: String?) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        title = title ?: tab.title,
                        url = url ?: tab.url
                    )
                } else tab
            }
        }
    }

    override fun incrementTrackerCount(tabId: String) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(blockedTrackerCount = tab.blockedTrackerCount + 1)
                } else tab
            }
        }
    }

    override fun setCosmeticRulesCount(tabId: String, count: Int) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(cosmeticRulesCount = count)
                } else tab
            }
        }
    }

    override fun setMediaPlaying(tabId: String, isPlaying: Boolean) {
        android.util.Log.d("ShadhinMedia", "setMediaPlaying for tab $tabId: $isPlaying")
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(isPlayingMedia = isPlaying)
                } else tab
            }
        }
    }

    override fun setFullScreen(tabId: String, isFullScreen: Boolean) {
        android.util.Log.d("ShadhinMedia", "setFullScreen for tab $tabId: $isFullScreen")
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(isFullScreen = isFullScreen)
                } else tab
            }
        }
    }

    override fun getOrCreateLiveWebView(tabId: String, context: Context): WebView? {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return null

        val existing = liveWebViews[tabId]
        if (existing != null) {
            lruOrder.remove(tabId)
            lruOrder.add(tabId)
            return existing
        }

        android.util.Log.d("ShadhinMedia", "Creating fresh WebView for tab $tabId")
        // Must allocate fresh WebView; enforce cap
        enforceMemoryCap(tabId)

        val webView = WebView(context)
        // If tab was hibernated and has a history bundle, restore it
        tab.historyBundle?.let { bundle ->
            try {
                webView.restoreState(bundle)
            } catch (_: Exception) {}
        }

        liveWebViews[tabId] = webView
        lruOrder.remove(tabId)
        lruOrder.add(tabId)

        _tabs.update { list ->
            list.map { if (it.id == tabId) it.copy(isAlive = true) else it }
        }

        return webView
    }

    override fun detachActiveWebView(): WebView? {
        return liveWebViews[_activeTabId.value]
    }

    override fun recycleInactiveTabs() {
        enforceMemoryCap(_activeTabId.value)
    }

    private fun enforceMemoryCap(protectedTabId: String) {
        while (liveWebViews.size >= maxLiveWebViews) {
            val candidateTabId = lruOrder.firstOrNull { tabId ->
                if (tabId == protectedTabId) {
                    android.util.Log.d("ShadhinMedia", "Eviction check: skipping active tab $tabId")
                    return@firstOrNull false
                }
                if (!liveWebViews.containsKey(tabId)) return@firstOrNull false
                val tab = _tabs.value.firstOrNull { it.id == tabId }
                if (tab?.isPlayingMedia == true || tab?.isFullScreen == true) {
                    android.util.Log.d("ShadhinMedia", "Eviction check: skipping media/fullscreen tab $tabId")
                    return@firstOrNull false
                }
                true
            } ?: run {
                android.util.Log.d("ShadhinMedia", "Eviction check: all ${liveWebViews.size} live WebViews are protected from eviction")
                break
            }

            android.util.Log.d("ShadhinMedia", "Evicting candidate tab: $candidateTabId")
            hibernateTab(candidateTabId)
        }
    }

    private fun hibernateTab(tabId: String) {
        val webView = liveWebViews[tabId] ?: return
        android.util.Log.d("ShadhinMedia", "Hibernating tab $tabId")
        val bundle = Bundle()
        try {
            webView.saveState(bundle)
        } catch (_: Exception) {}

        destroyWebView(tabId)

        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        historyBundle = bundle,
                        isAlive = false
                    )
                } else tab
            }
        }
    }

    private fun destroyWebView(tabId: String) {
        val webView = liveWebViews.remove(tabId) ?: return
        android.util.Log.d("ShadhinMedia", "Destroying WebView for tab $tabId")
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        } catch (_: Exception) {}

        _tabs.update { list ->
            list.map { if (it.id == tabId) it.copy(isAlive = false) else it }
        }
    }

    override fun pauseAll() {
        android.util.Log.d("ShadhinMedia", "Pausing timers and WebViews for ${liveWebViews.size} tabs")
        for ((_, webView) in liveWebViews) {
            try {
                webView.onPause()
                webView.pauseTimers()
            } catch (_: Exception) {}
        }
    }

    override fun resumeAll() {
        android.util.Log.d("ShadhinMedia", "Resuming timers and WebViews for ${liveWebViews.size} tabs")
        for ((_, webView) in liveWebViews) {
            try {
                webView.onResume()
                webView.resumeTimers()
            } catch (_: Exception) {}
        }
    }

    override fun destroy() {
        for (tabId in liveWebViews.keys.toList()) {
            destroyWebView(tabId)
        }
        liveWebViews.clear()
        lruOrder.clear()
    }

    // Helper for testing assertions
    fun getLiveWebViewCount(): Int = liveWebViews.size
}
