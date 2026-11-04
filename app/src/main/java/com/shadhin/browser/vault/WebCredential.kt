package com.shadhin.browser.vault

data class WebCredential(
    val id: Long = 0L,
    val origin: String,
    val username: String,
    val decryptedPassword: String,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)
