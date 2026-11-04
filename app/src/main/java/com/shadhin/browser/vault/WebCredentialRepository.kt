package com.shadhin.browser.vault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository abstracting encrypted credential persistence using Room and CryptoManager.
 */
class WebCredentialRepository(
    private val dao: WebCredentialDao,
    private val cryptoManager: CryptoManager = CryptoManager()
) {

    val allCredentials: Flow<List<WebCredential>> = dao.getAllCredentials().map { list ->
        list.map { entity ->
            WebCredential(
                id = entity.id,
                origin = entity.origin,
                username = entity.username,
                decryptedPassword = cryptoManager.decrypt(entity.encryptedPassword),
                title = entity.title,
                createdAt = entity.createdAt,
                lastUsedAt = entity.lastUsedAt
            )
        }
    }

    suspend fun getCredentialsForOrigin(origin: String): List<WebCredential> = withContext(Dispatchers.IO) {
        val cleanOrigin = normalizeOrigin(origin)
        if (cleanOrigin.isBlank()) return@withContext emptyList()

        dao.getCredentialsForOriginDirect(cleanOrigin).map { entity ->
            WebCredential(
                id = entity.id,
                origin = entity.origin,
                username = entity.username,
                decryptedPassword = cryptoManager.decrypt(entity.encryptedPassword),
                title = entity.title,
                createdAt = entity.createdAt,
                lastUsedAt = entity.lastUsedAt
            )
        }
    }

    suspend fun saveCredential(
        origin: String,
        username: String,
        password: String,
        title: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val cleanOrigin = normalizeOrigin(origin)
        val cleanUsername = username.trim()
        if (cleanOrigin.isBlank() || cleanUsername.isBlank() || password.isEmpty()) {
            return@withContext -1L
        }

        val encrypted = cryptoManager.encrypt(password)
        val existing = dao.findCredential(cleanOrigin, cleanUsername)

        val entity = WebCredentialEntity(
            id = existing?.id ?: 0L,
            origin = cleanOrigin,
            username = cleanUsername,
            encryptedPassword = encrypted,
            title = if (title.isNotBlank()) title else cleanOrigin,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis()
        )
        dao.insertOrUpdate(entity)
    }

    suspend fun markCredentialUsed(id: Long) = withContext(Dispatchers.IO) {
        dao.updateLastUsed(id)
    }

    suspend fun deleteCredential(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    fun normalizeOrigin(rawUrlOrHost: String): String {
        val trimmed = rawUrlOrHost.trim().lowercase()
        return try {
            val withoutScheme = if (trimmed.contains("://")) trimmed.substringAfter("://") else trimmed
            withoutScheme.substringBefore("/").substringBefore(":")
        } catch (_: Exception) {
            trimmed
        }
    }
}
