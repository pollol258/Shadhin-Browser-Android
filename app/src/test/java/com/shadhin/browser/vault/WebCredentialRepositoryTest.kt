package com.shadhin.browser.vault

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebCredentialRepositoryTest {

    private lateinit var fakeDao: FakeWebCredentialDao
    private lateinit var cryptoManager: CryptoManager
    private lateinit var repository: WebCredentialRepository

    @Before
    fun setUp() {
        fakeDao = FakeWebCredentialDao()
        cryptoManager = CryptoManager(keyAlias = "test_repo_key")
        repository = WebCredentialRepository(fakeDao, cryptoManager)
    }

    @Test
    fun testOriginNormalization() {
        assertEquals("github.com", repository.normalizeOrigin("https://github.com/login"))
        assertEquals("github.com", repository.normalizeOrigin("http://github.com/session"))
        assertEquals("router.local", repository.normalizeOrigin("http://router.local:8080/admin/settings"))
        assertEquals("sub.domain.org", repository.normalizeOrigin("https://sub.domain.org/path?q=1#frag"))
        assertEquals("google.com", repository.normalizeOrigin("google.com"))
    }

    @Test
    fun testSaveAndRetrieveCredential() = runBlocking {
        val saveId = repository.saveCredential(
            origin = "https://accounts.google.com/signin",
            username = "alice@example.com",
            password = "SecureSuperPassword!123"
        )
        assertTrue("Save ID should be positive", saveId > 0)

        val creds = repository.getCredentialsForOrigin("https://accounts.google.com")
        assertEquals(1, creds.size)

        val cred = creds.first()
        assertEquals("accounts.google.com", cred.origin)
        assertEquals("alice@example.com", cred.username)
        assertEquals("SecureSuperPassword!123", cred.decryptedPassword)

        // Verify that in the DAO, password is encrypted
        val rawEntity = fakeDao.entities.firstOrNull { it.id == saveId }
        assertNotNull(rawEntity)
        assertTrue(rawEntity!!.encryptedPassword != "SecureSuperPassword!123")
    }

    @Test
    fun testUpdatingExistingCredentialUpdatesPassword() = runBlocking {
        repository.saveCredential(
            origin = "https://reddit.com",
            username = "reddit_user",
            password = "OldPassword1"
        )

        // Save again with same origin and username but new password
        repository.saveCredential(
            origin = "reddit.com",
            username = "reddit_user",
            password = "NewPassword2"
        )

        val creds = repository.getCredentialsForOrigin("reddit.com")
        assertEquals("Should still have only 1 credential entry for compound unique key", 1, creds.size)
        assertEquals("NewPassword2", creds.first().decryptedPassword)
    }

    @Test
    fun testFlowObservability() = runBlocking {
        repository.saveCredential("siteA.com", "user1", "pass1")
        repository.saveCredential("siteB.com", "user2", "pass2")

        val list = repository.allCredentials.first()
        assertEquals(2, list.size)
        assertTrue(list.any { it.username == "user1" && it.decryptedPassword == "pass1" })
        assertTrue(list.any { it.username == "user2" && it.decryptedPassword == "pass2" })
    }

    @Test
    fun testDeleteCredential() = runBlocking {
        val id1 = repository.saveCredential("site.com", "userA", "passA")
        val id2 = repository.saveCredential("site.com", "userB", "passB")

        repository.deleteCredential(id1)

        val remaining = repository.getCredentialsForOrigin("site.com")
        assertEquals(1, remaining.size)
        assertEquals("userB", remaining.first().username)

        repository.deleteAll()
        val emptyList = repository.getCredentialsForOrigin("site.com")
        assertTrue(emptyList.isEmpty())
    }

    // In-memory fake DAO for fast JVM testing without requiring Room SQLite driver on JVM
    private class FakeWebCredentialDao : WebCredentialDao {
        val entities = mutableListOf<WebCredentialEntity>()
        private val stateFlow = MutableStateFlow<List<WebCredentialEntity>>(emptyList())
        private var idCounter = 1L

        private fun updateFlow() {
            stateFlow.value = entities.toList()
        }

        override fun getAllCredentials(): Flow<List<WebCredentialEntity>> = stateFlow.asStateFlow()

        override fun getCredentialsForOrigin(origin: String): Flow<List<WebCredentialEntity>> {
            return stateFlow.map { list -> list.filter { it.origin == origin } }
        }

        override suspend fun getCredentialsForOriginDirect(origin: String): List<WebCredentialEntity> {
            return entities.filter { it.origin == origin }
        }

        override suspend fun findCredential(origin: String, username: String): WebCredentialEntity? {
            return entities.firstOrNull { it.origin == origin && it.username == username }
        }

        override suspend fun insertOrUpdate(credential: WebCredentialEntity): Long {
            val existingIndex = entities.indexOfFirst {
                it.id == credential.id || (it.origin == credential.origin && it.username == credential.username)
            }
            return if (existingIndex >= 0) {
                val existing = entities[existingIndex]
                val updated = credential.copy(id = existing.id)
                entities[existingIndex] = updated
                updateFlow()
                existing.id
            } else {
                val newId = if (credential.id > 0) credential.id else idCounter++
                val newEntity = credential.copy(id = newId)
                entities.add(newEntity)
                updateFlow()
                newId
            }
        }

        override suspend fun updateLastUsed(id: Long, timestamp: Long) {
            val idx = entities.indexOfFirst { it.id == id }
            if (idx >= 0) {
                entities[idx] = entities[idx].copy(lastUsedAt = timestamp)
                updateFlow()
            }
        }

        override suspend fun deleteById(id: Long) {
            entities.removeAll { it.id == id }
            updateFlow()
        }

        override suspend fun deleteAll() {
            entities.clear()
            updateFlow()
        }
    }
}
