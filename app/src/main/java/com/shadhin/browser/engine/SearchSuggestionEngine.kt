package com.shadhin.browser.engine

import com.shadhin.browser.network.DohResolver
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Real-time search suggestion engine fetching live query completions from Google's Suggest API.
 * Endpoint: https://suggestqueries.google.com/complete/search?client=firefox&q={query}
 */
class SearchSuggestionEngine(
    private val dohResolver: DohResolver? = null,
    customClient: OkHttpClient? = null
) {
    private val httpClient: OkHttpClient = customClient ?: OkHttpClient.Builder().apply {
        if (dohResolver != null) {
            dns(dohResolver)
        }
        connectTimeout(3, TimeUnit.SECONDS)
        readTimeout(3, TimeUnit.SECONDS)
    }.build()

    private val moshi: Moshi = Moshi.Builder().build()
    private val listAdapter = moshi.adapter(List::class.java)

    /**
     * Fetches live search suggestions for a given user query.
     * Returns an empty list if query is blank, network is unreachable, or response is invalid.
     */
    suspend fun fetchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()

                parseSuggestionsJson(body)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parses the Google Suggest JSON array format:
     * ["query", ["suggestion 1", "suggestion 2", ...], ...]
     */
    fun parseSuggestionsJson(jsonString: String): List<String> {
        return try {
            val parsed = listAdapter.fromJson(jsonString) as? List<*> ?: return emptyList()
            if (parsed.size > 1) {
                val suggestionsList = parsed[1] as? List<*> ?: return emptyList()
                suggestionsList
                    .mapNotNull { it?.toString() }
                    .filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
