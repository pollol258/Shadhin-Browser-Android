package com.shadhin.browser.engine

import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit test for TabManagerContract logic testing max 3 live tabs, LRU eviction,
 * and state preservation without requiring a real native Chromium WebView.
 */
class TabManagerTest {

    // Test implementation conforming to TabManagerContract without native WebView
    private class MockTabManager(
        private val maxLiveTabs: Int = 3
    ) : TabManagerContract {
        private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
        override val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

        private val _activeTabId = MutableStateFlow<String>("")
        override val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

        private val liveTabIds = mutableSetOf<String>()
        private val lruOrder = mutableListOf<String>()

        init {
            openNewTab("", "New Tab")
        }

        override fun openNewTab(url: String, title: String): String {
            val id = UUID.randomUUID().toString()
            val newTab = BrowserTab(id = id, title = title, url = url, isAlive = false)
            _tabs.update { it + newTab }
            switchTab(id)
            return id
        }

        override fun switchTab(tabId: String) {
            _activeTabId.value = tabId
            lruOrder.remove(tabId)
            lruOrder.add(tabId)
            simulateMakeLive(tabId)
        }

        private fun simulateMakeLive(tabId: String) {
            if (liveTabIds.contains(tabId)) return

            while (liveTabIds.size >= maxLiveTabs) {
                val toEvict = lruOrder.firstOrNull { it != tabId && liveTabIds.contains(it) } ?: break
                hibernate(toEvict)
            }

            liveTabIds.add(tabId)
            _tabs.update { list ->
                list.map { if (it.id == tabId) it.copy(isAlive = true) else it }
            }
        }

        private fun hibernate(tabId: String) {
            liveTabIds.remove(tabId)
            val fakeBundle = Bundle()
            _tabs.update { list ->
                list.map { if (it.id == tabId) it.copy(isAlive = false, historyBundle = fakeBundle) else it }
            }
        }

        override fun closeTab(tabId: String) {
            liveTabIds.remove(tabId)
            lruOrder.remove(tabId)
            _tabs.update { it.filterNot { t -> t.id == tabId } }
        }

        override fun closeAllTabs() {
            liveTabIds.clear()
            lruOrder.clear()
            _tabs.value = emptyList()
            openNewTab("", "New Tab")
        }

        override fun updateTabMetadata(tabId: String, title: String?, url: String?) {
            _tabs.update { list ->
                list.map { if (it.id == tabId) it.copy(title = title ?: it.title, url = url ?: it.url) else it }
            }
        }

        override fun incrementTrackerCount(tabId: String) {
            _tabs.update { list ->
                list.map { if (it.id == tabId) it.copy(blockedTrackerCount = it.blockedTrackerCount + 1) else it }
            }
        }

        override fun setCosmeticRulesCount(tabId: String, count: Int) {
            _tabs.update { list ->
                list.map { if (it.id == tabId) it.copy(cosmeticRulesCount = count) else it }
            }
        }

        override fun setMediaPlaying(tabId: String, isPlaying: Boolean) {
            _tabs.update { list ->
                list.map { if (it.id == tabId) it.copy(isPlayingMedia = isPlaying) else it }
            }
        }

        override fun setFullScreen(tabId: String, isFullScreen: Boolean) {
            _tabs.update { list ->
                list.map { if (it.id == tabId) it.copy(isFullScreen = isFullScreen) else it }
            }
        }

        override fun pauseAll() {}
        override fun resumeAll() {}

        override fun getOrCreateLiveWebView(tabId: String, context: Context): WebView? = null
        override fun detachActiveWebView(): WebView? = null
        override fun recycleInactiveTabs() {}
        override fun destroy() {}

        fun getLiveCount(): Int = liveTabIds.size
    }

    private lateinit var tabManager: MockTabManager

    @Before
    fun setUp() {
        tabManager = MockTabManager(maxLiveTabs = 3)
    }

    @Test
    fun `test initial state starts with 1 tab`() {
        assertEquals(1, tabManager.tabs.value.size)
        assertEquals(1, tabManager.getLiveCount())
    }

    @Test
    fun `test opening tabs up to max cap keeps all alive`() {
        tabManager.openNewTab("https://a.com", "Tab A")
        tabManager.openNewTab("https://b.com", "Tab B")

        assertEquals(3, tabManager.tabs.value.size)
        assertEquals(3, tabManager.getLiveCount())
        assertTrue(tabManager.tabs.value.all { it.isAlive })
    }

    @Test
    fun `test exceeding cap hibernates least recently used tab and saves bundle`() {
        val tab1 = tabManager.tabs.value.first().id
        val tab2 = tabManager.openNewTab("https://two.com", "Tab 2")
        val tab3 = tabManager.openNewTab("https://three.com", "Tab 3")
        val tab4 = tabManager.openNewTab("https://four.com", "Tab 4")

        assertEquals(4, tabManager.tabs.value.size)
        assertEquals(3, tabManager.getLiveCount())

        // Tab 1 was the least recently used, so it must be hibernated
        val tab1State = tabManager.tabs.value.first { it.id == tab1 }
        assertFalse(tab1State.isAlive)
        assertNotNull(tab1State.historyBundle)
    }

    @Test
    fun `test switching back to hibernated tab rehydrates it`() {
        val tab1 = tabManager.tabs.value.first().id
        val tab2 = tabManager.openNewTab("https://two.com", "Tab 2")
        val tab3 = tabManager.openNewTab("https://three.com", "Tab 3")
        val tab4 = tabManager.openNewTab("https://four.com", "Tab 4")

        // Now switch back to tab 1
        tabManager.switchTab(tab1)
        val tab1State = tabManager.tabs.value.first { it.id == tab1 }
        assertTrue(tab1State.isAlive)
        assertEquals(3, tabManager.getLiveCount())
    }
}
