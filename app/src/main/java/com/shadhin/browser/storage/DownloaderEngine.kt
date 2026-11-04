package com.shadhin.browser.storage

import android.content.Context
import android.util.Base64
import android.webkit.CookieManager
import com.shadhin.browser.network.DohResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Streaming coroutine-driven download engine wired to DohResolver and SandboxFileVault.
 * Handles HTTP/HTTPS, blob:, and data: URLs.
 */
class DownloaderEngine(
    private val context: Context,
    private val dohResolver: DohResolver,
    private val vault: SandboxFileVault,
    private val scope: CoroutineScope
) {

    private val httpClient = OkHttpClient.Builder()
        .dns(dohResolver)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?
    ): String {
        val id = UUID.randomUUID().toString()
        val guessedName = guessFileName(url, contentDisposition, mimetype)
        val sanitized = SandboxFileVault.sanitizeFilename(guessedName)

        val item = DownloadItem(
            id = id,
            fileName = sanitized,
            url = url,
            mimeType = mimetype ?: "application/octet-stream",
            status = DownloadStatus.Queued
        )

        _downloads.update { listOf(item) + it }

        val job = scope.launch(Dispatchers.IO) {
            executeDownload(id, url, userAgent, sanitized)
        }
        activeJobs[id] = job
        return id
    }

    private suspend fun executeDownload(
        id: String,
        url: String,
        userAgent: String?,
        sanitizedFileName: String
    ) {
        val destinationFile = vault.createSandboxFile(sanitizedFileName)

        try {
            if (url.startsWith("data:")) {
                handleDataUrlDownload(id, url, destinationFile)
                return
            }

            updateStatus(id, DownloadStatus.Downloading(0.0f))

            val requestBuilder = Request.Builder().url(url)
            if (!userAgent.isNullOrBlank()) {
                requestBuilder.addHeader("User-Agent", userAgent)
            }
            // Forward WebView cookies
            val cookies = CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrBlank()) {
                requestBuilder.addHeader("Cookie", cookies)
            }
            // Privacy: strip referrers
            requestBuilder.removeHeader("Referer")

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    updateStatus(id, DownloadStatus.Failed("HTTP ${response.code}"))
                    return
                }

                val body = response.body ?: run {
                    updateStatus(id, DownloadStatus.Failed("Empty response body"))
                    return
                }

                val totalLength = body.contentLength()
                updateItem(id) { it.copy(totalBytes = totalLength, localFilePath = destinationFile.absolutePath) }

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedSoFar = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSoFar += bytesRead

                            val progress = if (totalLength > 0) {
                                (downloadedSoFar.toFloat() / totalLength).coerceIn(0f, 1f)
                            } else {
                                0.5f // Indeterminate
                            }

                            updateItem(id) {
                                it.copy(
                                    downloadedBytes = downloadedSoFar,
                                    status = DownloadStatus.Downloading(progress)
                                )
                            }
                        }
                        output.flush()
                    }
                }

                updateStatus(id, DownloadStatus.Completed)
            }
        } catch (e: Exception) {
            updateStatus(id, DownloadStatus.Failed(e.localizedMessage ?: "Network error"))
        } finally {
            activeJobs.remove(id)
        }
    }

    private fun handleDataUrlDownload(id: String, dataUrl: String, destination: File) {
        try {
            val commaIndex = dataUrl.indexOf(",")
            if (commaIndex == -1) {
                updateStatus(id, DownloadStatus.Failed("Invalid data URL"))
                return
            }
            val meta = dataUrl.substring(0, commaIndex)
            val dataPart = dataUrl.substring(commaIndex + 1)
            val isBase64 = meta.contains(";base64")

            val bytes = if (isBase64) {
                Base64.decode(dataPart, Base64.DEFAULT)
            } else {
                dataPart.toByteArray(Charsets.UTF_8)
            }

            FileOutputStream(destination).use { out ->
                out.write(bytes)
                out.flush()
            }

            updateItem(id) {
                it.copy(
                    totalBytes = bytes.size.toLong(),
                    downloadedBytes = bytes.size.toLong(),
                    localFilePath = destination.absolutePath,
                    status = DownloadStatus.Completed
                )
            }
        } catch (e: Exception) {
            updateStatus(id, DownloadStatus.Failed("Failed decoding data URL: ${e.message}"))
        }
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        updateStatus(id, DownloadStatus.Cancelled)
    }

    fun deleteDownload(id: String) {
        cancelDownload(id)
        val item = _downloads.value.firstOrNull { it.id == id }
        if (item != null && item.localFilePath.isNotBlank()) {
            vault.deleteFile(item.localFilePath)
        }
        _downloads.update { list -> list.filterNot { it.id == id } }
    }

    fun clearAllDownloads() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        vault.wipeSandbox()
        _downloads.value = emptyList()
    }

    private fun updateStatus(id: String, status: DownloadStatus) {
        updateItem(id) { it.copy(status = status) }
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }

    private fun guessFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        // Try parsing Content-Disposition header
        if (!contentDisposition.isNullOrBlank()) {
            val cd = contentDisposition.lowercase()
            val filenameIndex = cd.indexOf("filename=")
            if (filenameIndex != -1) {
                var fn = contentDisposition.substring(filenameIndex + 9).trim()
                if (fn.startsWith("\"") && fn.endsWith("\"") && fn.length > 1) {
                    fn = fn.substring(1, fn.length - 1)
                }
                if (fn.isNotBlank()) return fn
            }
        }

        // Try extracting from URL path
        val path = try {
            val u = java.net.URI(url)
            u.path ?: ""
        } catch (_: Exception) {
            ""
        }
        val lastSegment = path.substringAfterLast("/", "")
        if (lastSegment.isNotBlank() && lastSegment.contains(".")) {
            return lastSegment
        }

        // Fallback by MIME type
        val ext = when (mimeType?.lowercase()) {
            "application/pdf" -> ".pdf"
            "image/png" -> ".png"
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/webp" -> ".webp"
            "text/html" -> ".html"
            "application/zip" -> ".zip"
            "application/json" -> ".json"
            else -> ".bin"
        }
        return "download_${System.currentTimeMillis()}$ext"
    }
}
