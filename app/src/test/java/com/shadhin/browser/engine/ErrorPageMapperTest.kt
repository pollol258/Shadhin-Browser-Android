package com.shadhin.browser.engine

import android.webkit.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorPageMapperTest {

    @Test
    fun `test cleartext permitted error mapping`() {
        val details = ErrorPageMapper.mapError(
            errorCode = -1,
            description = "net::ERR_CLEARTEXT_NOT_PERMITTED",
            failingUrl = "http://google.com"
        )
        assertTrue(details.isCleartextBlocked)
        assertEquals("Cleartext Connection Blocked", details.title)
    }

    @Test
    fun `test host lookup error mapping`() {
        val details = ErrorPageMapper.mapError(
            errorCode = WebViewClient.ERROR_HOST_LOOKUP,
            description = "net::ERR_NAME_NOT_RESOLVED",
            failingUrl = "https://nonexistent-domain-404.org"
        )
        assertEquals("Server Not Found", details.title)
        assertTrue(details.isHttpsDowngradeCandidate)
        assertFalse(details.isCleartextBlocked)
    }

    @Test
    fun `test connection refused mapping`() {
        val details = ErrorPageMapper.mapError(
            errorCode = WebViewClient.ERROR_CONNECT,
            description = "net::ERR_CONNECTION_REFUSED",
            failingUrl = "https://localhost:9999"
        )
        assertEquals("Connection Refused", details.title)
    }

    @Test
    fun `test timeout error mapping`() {
        val details = ErrorPageMapper.mapError(
            errorCode = WebViewClient.ERROR_TIMEOUT,
            description = "net::ERR_CONNECTION_TIMED_OUT",
            failingUrl = "https://slow-site.com"
        )
        assertEquals("Connection Timed Out", details.title)
    }

    @Test
    fun `test disconnected error mapping`() {
        val details = ErrorPageMapper.mapError(
            errorCode = -15,
            description = "net::ERR_INTERNET_DISCONNECTED",
            failingUrl = "https://example.com"
        )
        assertEquals("No Internet Connection", details.title)
        assertFalse(details.isHttpsDowngradeCandidate)
    }

    @Test
    fun `test HTTP status error mapping`() {
        val notFound = ErrorPageMapper.mapHttpError(404, "Not Found")
        assertEquals("Page Not Found (404)", notFound.title)

        val serverError = ErrorPageMapper.mapHttpError(500, "Internal Error")
        assertEquals("Internal Server Error (500)", serverError.title)
    }

    @Test
    fun `test themed error HTML contains Titanium palette tokens and fallback button`() {
        val details = ErrorPageMapper.ErrorDetails(
            title = "Test Failure",
            description = "A test error description",
            isHttpsDowngradeCandidate = true
        )

        val html = ErrorPageMapper.buildThemedErrorHtml(
            failingUrl = "https://test.com",
            details = details,
            showHttpFallbackButton = true
        )

        // Verify Warm Glass color palette hex values and emblem are embedded
        assertTrue(html.contains("#18100C"))
        assertTrue(html.contains("#261914"))
        assertTrue(html.contains("#FFF4EE"))
        assertTrue(html.contains("#B59E92"))
        assertTrue(html.contains("#F28C4E"))

        // Verify action buttons and bridge
        assertTrue(html.contains("Retry"))
        assertTrue(html.contains("Continue with HTTP (not secure)"))
        assertTrue(html.contains("Go to start page"))
        assertTrue(html.contains("window.__shadhin_bridge__"))
    }
}
