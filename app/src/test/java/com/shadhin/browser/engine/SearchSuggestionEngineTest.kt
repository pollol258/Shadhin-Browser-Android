package com.shadhin.browser.engine

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchSuggestionEngineTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var suggestionEngine: SearchSuggestionEngine

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        val client = OkHttpClient.Builder().build()
        suggestionEngine = SearchSuggestionEngine(customClient = client)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `test parseSuggestionsJson parses standard Google Suggest payload`() {
        val sampleJson = """
            ["android", ["android", "android studio", "android auto", "android 17"], [], {"google:suggestsubtypes":[[512,433]]}]
        """.trimIndent()

        val suggestions = suggestionEngine.parseSuggestionsJson(sampleJson)
        assertEquals(4, suggestions.size)
        assertEquals("android", suggestions[0])
        assertEquals("android studio", suggestions[1])
        assertEquals("android auto", suggestions[2])
        assertEquals("android 17", suggestions[3])
    }

    @Test
    fun `test parseSuggestionsJson handles empty and malformed json gracefully`() {
        assertTrue(suggestionEngine.parseSuggestionsJson("").isEmpty())
        assertTrue(suggestionEngine.parseSuggestionsJson("[]").isEmpty())
        assertTrue(suggestionEngine.parseSuggestionsJson("[\"test\"]").isEmpty())
        assertTrue(suggestionEngine.parseSuggestionsJson("{ \"error\": \"not an array\" }").isEmpty())
        assertTrue(suggestionEngine.parseSuggestionsJson("not even json").isEmpty())
    }

    @Test
    fun `test fetchSuggestions returns empty list immediately for blank query`() = runBlocking {
        val emptyResult = suggestionEngine.fetchSuggestions("   ")
        assertTrue(emptyResult.isEmpty())
    }
}
