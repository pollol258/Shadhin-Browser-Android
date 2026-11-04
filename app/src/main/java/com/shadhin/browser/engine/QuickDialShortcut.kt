package com.shadhin.browser.engine

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuickDialShortcut(
    val id: String,
    val title: String,
    val url: String,
    val faviconBase64: String? = null,
    val isDefault: Boolean = false
)
