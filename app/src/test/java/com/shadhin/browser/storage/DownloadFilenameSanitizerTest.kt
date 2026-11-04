package com.shadhin.browser.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadFilenameSanitizerTest {

    @Test
    fun `test path traversal sequences are stripped`() {
        val sanitized = SandboxFileVault.sanitizeFilename("../../etc/passwd")
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("/"))
        assertEquals("passwd", sanitized)
    }

    @Test
    fun `test windows style path traversal is stripped`() {
        val sanitized = SandboxFileVault.sanitizeFilename("..\\..\\Windows\\System32\\cmd.exe")
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("\\"))
        assertEquals("cmd.exe", sanitized)
    }

    @Test
    fun `test fallback for empty or blank input`() {
        val sanitized = SandboxFileVault.sanitizeFilename("   ")
        assertEquals("download.bin", sanitized)
    }

    @Test
    fun `test legal filenames are preserved`() {
        val sanitized = SandboxFileVault.sanitizeFilename("report_2026.pdf")
        assertEquals("report_2026.pdf", sanitized)
    }
}
