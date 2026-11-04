package com.shadhin.browser.engine

import android.os.Bundle

data class BrowserTab(
    val id: String,
    val title: String = "New Tab",
    val url: String = "",
    val historyBundle: Bundle? = null,
    val isAlive: Boolean = false,
    val isPlayingMedia: Boolean = false,
    val isFullScreen: Boolean = false,
    val blockedTrackerCount: Int = 0,
    val cosmeticRulesCount: Int = 0
)
