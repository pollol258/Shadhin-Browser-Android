package com.shadhin.browser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadhin.browser.storage.DownloadItem
import com.shadhin.browser.storage.DownloadStatus
import com.shadhin.browser.storage.SandboxFileVault
import com.shadhin.browser.ui.theme.Accent
import com.shadhin.browser.ui.theme.CapsuleBorder
import com.shadhin.browser.ui.theme.CapsuleFill
import com.shadhin.browser.ui.theme.StatusError
import com.shadhin.browser.ui.theme.StatusSuccess
import com.shadhin.browser.ui.theme.Surface
import com.shadhin.browser.ui.theme.SurfaceHigh
import com.shadhin.browser.ui.theme.TextPrimary
import com.shadhin.browser.ui.theme.TextSecondary
import com.shadhin.browser.viewmodel.BrowserUiIntent
import com.shadhin.browser.viewmodel.BrowserUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    uiState: BrowserUiState,
    onIntent: (BrowserUiIntent) -> Unit,
    sandboxVault: SandboxFileVault
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var pendingExportFilePath by remember { mutableStateOf<String?>(null) }

    // SAF Document Exporter
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && pendingExportFilePath != null) {
            sandboxVault.exportToSaf(pendingExportFilePath!!, uri)
        }
        pendingExportFilePath = null
    }

    ModalBottomSheet(
        onDismissRequest = { onIntent(BrowserUiIntent.ToggleDownloadsSheet(false)) },
        sheetState = sheetState,
        containerColor = Surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloads",
                        tint = Accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Sandboxed Vault",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = { onIntent(BrowserUiIntent.ToggleDownloadsSheet(false)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Isolated private storage without public permissions. Wiped on Burn Session.",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No active or completed downloads",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.downloads, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onExport = {
                                pendingExportFilePath = item.localFilePath
                                exportLauncher.launch(item.fileName)
                            },
                            onCancel = { onIntent(BrowserUiIntent.CancelDownload(item.id)) },
                            onDelete = { onIntent(BrowserUiIntent.DeleteDownload(item.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    item: DownloadItem,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CapsuleFill)
            .border(1.dp, CapsuleBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (item.status) {
                        is DownloadStatus.Downloading -> "Downloading... ${(item.progress * 100).toInt()}%"
                        is DownloadStatus.Completed -> "Vault Stored (${formatBytes(item.totalBytes)})"
                        is DownloadStatus.Failed -> "Failed: ${item.status.reason}"
                        is DownloadStatus.Cancelled -> "Cancelled"
                        is DownloadStatus.Queued -> "Queued"
                    },
                    color = when (item.status) {
                        is DownloadStatus.Completed -> StatusSuccess
                        is DownloadStatus.Failed -> StatusError
                        else -> TextSecondary
                    },
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.status is DownloadStatus.Completed) {
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Export to public device storage",
                            tint = Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = if (item.status is DownloadStatus.Downloading) onCancel else onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (item.status is DownloadStatus.Downloading) Icons.Default.Close else Icons.Default.Delete,
                        contentDescription = "Remove download",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (item.status is DownloadStatus.Downloading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Accent,
                trackColor = SurfaceHigh
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val group = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, group.toDouble())
    return String.format("%.1f %s", value, units[group])
}
