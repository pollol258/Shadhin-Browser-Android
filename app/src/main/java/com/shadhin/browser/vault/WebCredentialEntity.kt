package com.shadhin.browser.vault

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "web_credentials",
    indices = [
        Index(value = ["origin"]),
        Index(value = ["origin", "username"], unique = true)
    ]
)
data class WebCredentialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val origin: String,
    val username: String,
    val encryptedPassword: String,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)
