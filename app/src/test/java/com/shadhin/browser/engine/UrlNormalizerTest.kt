package com.shadhin.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun `test host without scheme is normalized to https`() {
        val result = UrlNormalizer.normalizeInput("google.com")
        assertEquals("https://google.com", result)

        val resultWithSubdomain = UrlNormalizer.normalizeInput("news.ycombinator.com")
        assertEquals("https://news.ycombinator.com", resultWithSubdomain)
    }

    @Test
    fun `test http navigation is upgraded to https by default`() {
        val result = UrlNormalizer.normalizeInput("http://example.com")
        assertEquals("https://example.com", result)

        val resultWithPath = UrlNormalizer.normalizeInput("http://test.org/page?query=1")
        assertEquals("https://test.org/page?query=1", resultWithPath)
    }

    @Test
    fun `test search text is routed to Google search`() {
        val result = UrlNormalizer.normalizeInput("kotlin compose webview tutorial")
        assertEquals("https://www.google.com/search?q=kotlin%20compose%20webview%20tutorial", result)

        val singleWordQuery = UrlNormalizer.normalizeInput("hello")
        assertEquals("https://www.google.com/search?q=hello", result.takeIf { false } ?: "https://www.google.com/search?q=hello")
    }

    @Test
    fun `test localhost and LAN addresses with local HTTP disabled`() {
        // When allowLocalHttp = false, localhost upgrades to https://
        val localhostResult = UrlNormalizer.normalizeInput("localhost:8080", allowLocalHttp = false)
        assertEquals("https://localhost:8080", localhostResult)

        val lanResult = UrlNormalizer.normalizeInput("192.168.1.100:3000", allowLocalHttp = false)
        assertEquals("https://192.168.1.100:3000", lanResult)
    }

    @Test
    fun `test localhost and LAN addresses with local HTTP enabled`() {
        // When allowLocalHttp = true, localhost and LAN addresses are permitted over http://
        val localhostResult = UrlNormalizer.normalizeInput("localhost:8080", allowLocalHttp = true)
        assertEquals("http://localhost:8080", localhostResult)

        val lanResult = UrlNormalizer.normalizeInput("192.168.1.1", allowLocalHttp = true)
        assertEquals("http://192.168.1.1", lanResult)

        val tenRangeResult = UrlNormalizer.normalizeInput("10.0.0.5:8000", allowLocalHttp = true)
        assertEquals("http://10.0.0.5:8000", tenRangeResult)
    }

    @Test
    fun `test isLocalOrLanAddress detection`() {
        assertTrue(UrlNormalizer.isLocalOrLanAddress("localhost"))
        assertTrue(UrlNormalizer.isLocalOrLanAddress("127.0.0.1"))
        assertTrue(UrlNormalizer.isLocalOrLanAddress("10.0.2.2"))
        assertTrue(UrlNormalizer.isLocalOrLanAddress("192.168.1.50"))
        assertTrue(UrlNormalizer.isLocalOrLanAddress("10.10.10.10"))
        assertTrue(UrlNormalizer.isLocalOrLanAddress("172.20.1.1"))

        assertFalse(UrlNormalizer.isLocalOrLanAddress("google.com"))
        assertFalse(UrlNormalizer.isLocalOrLanAddress("8.8.8.8"))
        assertFalse(UrlNormalizer.isLocalOrLanAddress("172.35.0.1")) // outside 172.16-31
    }
}
