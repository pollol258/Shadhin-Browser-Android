package com.shadhin.browser.storage

sealed interface DownloadStatus {
    data object Queued : DownloadStatus
    data class Downloading(val progress: Float) : DownloadStatus
    data object Completed : DownloadStatus
    data class Failed(val reason: String) : DownloadStatus
    data object Cancelled : DownloadStatus
}

data class DownloadItem(
    val id: String,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.Queued,
    val localFilePath: String = ""
) {
    val progress: Float
        get() = when (val s = status) {
            is DownloadStatus.Downloading -> s.progress
            is DownloadStatus.Completed -> 1.0f
            else -> 0.0f
        }
}
