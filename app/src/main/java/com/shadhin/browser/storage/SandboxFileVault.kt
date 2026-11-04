package com.shadhin.browser.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream

/**
 * Manages private app-internal sandboxed downloads directory.
 * Provides zero-permission private storage, session wipe, and SAF streaming.
 */
class SandboxFileVault(private val context: Context) {

    private val sandboxDir: File = File(context.filesDir, "sandbox_downloads").apply {
        if (!exists()) mkdirs()
    }

    fun getSandboxDirectory(): File = sandboxDir

    fun createSandboxFile(sanitizedFileName: String): File {
        var target = File(sandboxDir, sanitizedFileName)
        var counter = 1
        val nameWithoutExt = sanitizedFileName.substringBeforeLast(".")
        val ext = sanitizedFileName.substringAfterLast(".", "")
        val dotExt = if (ext.isNotEmpty() && ext != sanitizedFileName) ".$ext" else ""

        while (target.exists()) {
            target = File(sandboxDir, "${nameWithoutExt}_$counter$dotExt")
            counter++
        }
        return target
    }

    /**
     * Wipes all sandboxed downloaded files.
     */
    fun wipeSandbox() {
        try {
            sandboxDir.listFiles()?.forEach { file ->
                if (file.isFile) file.delete()
            }
        } catch (_: Exception) {}
    }

    /**
     * Streams a sandboxed file into a user-selected SAF URI.
     */
    fun exportToSaf(localFilePath: String, destinationUri: Uri): Boolean {
        val sourceFile = File(localFilePath)
        if (!sourceFile.exists()) return false

        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                FileInputStream(sourceFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteFile(localFilePath: String): Boolean {
        val file = File(localFilePath)
        return if (file.exists()) file.delete() else false
    }

    companion object {
        /**
         * Robust filename sanitization protecting against path traversal attacks.
         */
        fun sanitizeFilename(raw: String?, fallback: String = "download.bin"): String {
            if (raw.isNullOrBlank()) return fallback
            // Strip any directory traversal sequences
            var cleaned = raw
                .replace("\\", "/")
                .substringAfterLast("/")
                .replace("..", "")
                .trim()

            // Remove illegal filesystem characters
            cleaned = cleaned.replace(Regex("[^a-zA-Z0-9._\\-\\(\\)\\[\\]]"), "_")

            if (cleaned.isBlank() || cleaned == "." || cleaned == "..") {
                cleaned = fallback
            }
            return cleaned.take(128)
        }
    }
}
