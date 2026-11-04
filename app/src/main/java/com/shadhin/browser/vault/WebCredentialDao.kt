package com.shadhin.browser.vault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WebCredentialDao {

    @Query("SELECT * FROM web_credentials WHERE origin = :origin ORDER BY lastUsedAt DESC")
    fun getCredentialsForOrigin(origin: String): Flow<List<WebCredentialEntity>>

    @Query("SELECT * FROM web_credentials WHERE origin = :origin ORDER BY lastUsedAt DESC")
    suspend fun getCredentialsForOriginDirect(origin: String): List<WebCredentialEntity>

    @Query("SELECT * FROM web_credentials ORDER BY origin ASC, username ASC")
    fun getAllCredentials(): Flow<List<WebCredentialEntity>>

    @Query("SELECT * FROM web_credentials WHERE origin = :origin AND username = :username LIMIT 1")
    suspend fun findCredential(origin: String, username: String): WebCredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(credential: WebCredentialEntity): Long

    @Query("UPDATE web_credentials SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM web_credentials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM web_credentials")
    suspend fun deleteAll()
}
