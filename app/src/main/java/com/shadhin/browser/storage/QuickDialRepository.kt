package com.shadhin.browser.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.shadhin.browser.engine.QuickDialShortcut
import com.shadhin.browser.engine.UrlNormalizer
import com.shadhin.browser.network.DohResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages user quick-dial shortcuts with disk persistence and background favicon fetching.
 */
class QuickDialRepository(
    private val context: Context,
    private val dohResolver: DohResolver,
    customStorageDir: File? = null
) {

    private val storageFile = File(customStorageDir ?: context.filesDir, "quick_dials.json")
    private val httpClient = OkHttpClient.Builder()
        .dns(dohResolver)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val defaultDials = listOf(
        QuickDialShortcut("default-1", "GitHub", "https://github.com", isDefault = true),
        QuickDialShortcut("default-2", "Hacker News", "https://news.ycombinator.com", isDefault = true),
        QuickDialShortcut("default-3", "Wikipedia", "https://wikipedia.org", isDefault = true),
        QuickDialShortcut("default-4", "EFF", "https://www.eff.org", isDefault = true),
        QuickDialShortcut("default-5", "DuckDuckGo", "https://duckduckgo.com", isDefault = true),
        QuickDialShortcut("default-6", "ArXiv", "https://arxiv.org", isDefault = true)
    )

    private val moshi = com.squareup.moshi.Moshi.Builder().build()
    private val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, QuickDialShortcut::class.java)
    private val jsonAdapter = moshi.adapter<List<QuickDialShortcut>>(listType)

    fun loadShortcuts(): List<QuickDialShortcut> {
        if (!storageFile.exists()) {
            saveShortcuts(defaultDials)
            return defaultDials
        }

        return try {
            val jsonStr = storageFile.readText()
            val list = jsonAdapter.fromJson(jsonStr)
            if (list == null) defaultDials else list
        } catch (_: Exception) {
            defaultDials
        }
    }

    fun saveShortcuts(shortcuts: List<QuickDialShortcut>) {
        try {
            storageFile.parentFile?.mkdirs()
            val json = jsonAdapter.toJson(shortcuts)
            storageFile.writeText(json)
        } catch (_: Exception) {}
    }

    suspend fun addShortcut(title: String, url: String): QuickDialShortcut = withContext(Dispatchers.IO) {
        val normalizedUrl = UrlNormalizer.normalizeWebAddress(url)

        val newShortcut = QuickDialShortcut(
            id = UUID.randomUUID().toString(),
            title = if (title.isBlank()) extractHost(normalizedUrl) else title.trim(),
            url = normalizedUrl,
            faviconBase64 = null,
            isDefault = false
        )

        val current = loadShortcuts().toMutableList()
        current.add(newShortcut)
        saveShortcuts(current)

        // Attempt HTTPS favicon fetch in background
        try {
            val favicon = fetchFavicon(newShortcut.id, normalizedUrl)
            if (favicon != null) {
                val updatedWithFavicon = newShortcut.copy(faviconBase64 = favicon)
                val updatedList = current.map { if (it.id == newShortcut.id) updatedWithFavicon else it }
                saveShortcuts(updatedList)
                return@withContext updatedWithFavicon
            }
        } catch (_: Exception) {}

        newShortcut
    }

    suspend fun editShortcut(id: String, newTitle: String, newUrl: String): List<QuickDialShortcut> = withContext(Dispatchers.IO) {
        val normalizedUrl = UrlNormalizer.normalizeWebAddress(newUrl)
        val current = loadShortcuts().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index == -1) return@withContext current

        val existing = current[index]
        val hostChanged = extractHost(existing.url) != extractHost(normalizedUrl)
        val updatedFavicon = if (hostChanged) null else existing.faviconBase64

        val updatedItem = existing.copy(
            title = if (newTitle.isBlank()) extractHost(normalizedUrl) else newTitle.trim(),
            url = normalizedUrl,
            faviconBase64 = updatedFavicon
        )
        current[index] = updatedItem
        saveShortcuts(current)

        if (hostChanged) {
            try {
                val favicon = fetchFavicon(id, normalizedUrl)
                if (favicon != null) {
                    current[index] = updatedItem.copy(faviconBase64 = favicon)
                    saveShortcuts(current)
                }
            } catch (_: Exception) {}
        }

        current
    }

    fun deleteShortcut(id: String): Pair<List<QuickDialShortcut>, QuickDialShortcut?> {
        val current = loadShortcuts()
        val toDelete = current.firstOrNull { it.id == id }
        val updated = current.filterNot { it.id == id }
        saveShortcuts(updated)
        // Also remove cached file in sandbox if exists
        try {
            File(sandboxFaviconDir, "$id.png").delete()
        } catch (_: Exception) {}
        return Pair(updated, toDelete)
    }

    fun restoreShortcut(shortcut: QuickDialShortcut, index: Int = -1): List<QuickDialShortcut> {
        val current = loadShortcuts().toMutableList()
        if (current.any { it.id == shortcut.id }) return current

        if (index in 0..current.size) {
            current.add(index, shortcut)
        } else {
            current.add(shortcut)
        }
        saveShortcuts(current)
        return current
    }

    fun moveShortcut(id: String, moveLeft: Boolean): List<QuickDialShortcut> {
        val current = loadShortcuts().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index == -1) return current

        val targetIndex = if (moveLeft) index - 1 else index + 1
        if (targetIndex in 0 until current.size) {
            val item = current.removeAt(index)
            current.add(targetIndex, item)
            saveShortcuts(current)
        }
        return current
    }

    fun resetToDefaults(): List<QuickDialShortcut> {
        saveShortcuts(defaultDials)
        return defaultDials
    }

    suspend fun refreshFavicons(shortcuts: List<QuickDialShortcut>): List<QuickDialShortcut> = withContext(Dispatchers.IO) {
        var modified = false
        val updated = shortcuts.map { item ->
            if (item.faviconBase64.isNullOrBlank()) {
                val icon = fetchFavicon(item.id, item.url)
                if (icon != null) {
                    modified = true
                    item.copy(faviconBase64 = icon)
                } else {
                    item
                }
            } else {
                item
            }
        }
        if (modified) {
            saveShortcuts(updated)
        }
        updated
    }

    private val sandboxFaviconDir by lazy {
        File(customStorageDir ?: context.filesDir, "sandbox/favicons").apply { mkdirs() }
    }

    private fun fetchFavicon(id: String, siteUrl: String): String? {
        val host = extractHost(siteUrl)
        if (host.isBlank()) return null

        // STRICT SECURITY: Never fetch favicons over cleartext HTTP
        val candidateUrls = listOf(
            "https://$host/favicon.ico",
            "https://www.google.com/s2/favicons?domain=$host&sz=128"
        )

        for (candidate in candidateUrls) {
            if (!candidate.startsWith("https://", ignoreCase = true)) continue
            try {
                val request = Request.Builder()
                    .url(candidate)
                    .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.size in 32..250000) {
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                val baos = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                val compressedBytes = baos.toByteArray()
                                // Cache to sandboxed directory
                                try {
                                    File(sandboxFaviconDir, "$id.png").writeBytes(compressedBytes)
                                } catch (_: Exception) {}

                                return safeBase64Encode(compressedBytes)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun safeBase64Encode(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun extractHost(url: String): String {
        return try {
            val u = java.net.URI(url)
            u.host ?: ""
        } catch (_: Exception) {
            val afterScheme = url.substringAfter("://")
            afterScheme.substringBefore("/").substringBefore(":")
        }
    }
}
