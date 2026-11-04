package com.shadhin.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shadhin.browser.ui.theme.AccentOrange
import com.shadhin.browser.ui.theme.AppThemeMode
import com.shadhin.browser.ui.theme.DarkBackgroundEnd
import com.shadhin.browser.ui.theme.DarkBackgroundStart
import com.shadhin.browser.ui.theme.LightBackgroundEnd
import com.shadhin.browser.ui.theme.LightBackgroundStart
import com.shadhin.browser.ui.theme.StatusError
import com.shadhin.browser.viewmodel.BrowserUiIntent
import com.shadhin.browser.viewmodel.BrowserViewModel

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // Android System Back Button Interception
    BackHandler {
        viewModel.onIntent(BrowserUiIntent.GoBack)
    }

    val systemInDark = isSystemInDarkTheme()
    val isDark = when (uiState.themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    // Static cached background gradient (no per-frame blur) or solid fill if reduceGlassEffects or full-screen video is active
    val backgroundModifier = if (uiState.reduceGlassEffects || uiState.isFullScreenVideo) {
        Modifier.background(if (isDark) DarkBackgroundStart else LightBackgroundStart)
    } else {
        val bgBrush = remember(isDark) {
            if (isDark) {
                Brush.verticalGradient(listOf(DarkBackgroundStart, DarkBackgroundEnd))
            } else {
                Brush.verticalGradient(listOf(LightBackgroundStart, LightBackgroundEnd))
            }
        }
        Modifier.background(bgBrush)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        // Active Content: Start Page or Edge-to-Edge WebView
        if (uiState.activeUrl.isBlank()) {
            StartPageView(
                uiState = uiState,
                onIntent = viewModel::onIntent,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    val webView = viewModel.tabManager.getOrCreateLiveWebView(uiState.activeTabId, ctx)!!
                    viewModel.configureWebView(webView, uiState.activeTabId)
                    if (uiState.activeUrl.isNotBlank() && webView.url != uiState.activeUrl) {
                        webView.loadUrl(uiState.activeUrl)
                    }
                    webView
                },
                update = { webView ->
                    // Rebind if active tab changed
                    val activeLive = viewModel.tabManager.detachActiveWebView()
                    val targetView = if (webView != activeLive) {
                        viewModel.tabManager.getOrCreateLiveWebView(uiState.activeTabId, webView.context)?.also {
                            viewModel.configureWebView(it, uiState.activeTabId)
                        } ?: webView
                    } else {
                        webView
                    }
                    if (uiState.activeUrl.isNotBlank() && targetView.url != uiState.activeUrl) {
                        targetView.loadUrl(uiState.activeUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .testTag("browser_webview_container")
            )
        }

        // Top 2dp Animated Loading Bar (Warm Orange Accent #F28C4E)
        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .testTag("top_loading_bar"),
                color = AccentOrange,
                trackColor = MaterialTheme.colorScheme.background
            )
        }

        // Floating Warm Glass Capsule Bottom Island
        FloatingCapsuleBar(
            uiState = uiState,
            onIntent = viewModel::onIntent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        // Modal Sheets
        if (uiState.isOmniboxSheetVisible) {
            OmniboxSheet(
                uiState = uiState,
                onIntent = viewModel::onIntent
            )
        }

        if (uiState.isTabSwitcherVisible) {
            TabSwitcherSheet(
                uiState = uiState,
                onIntent = viewModel::onIntent
            )
        }

        if (uiState.isShieldDialogVisible) {
            ShieldDialog(
                uiState = uiState,
                onIntent = viewModel::onIntent
            )
        }

        if (uiState.isSettingsSheetVisible) {
            SettingsSheet(
                uiState = uiState,
                onIntent = viewModel::onIntent
            )
        }

        if (uiState.isDownloadsSheetVisible) {
            DownloadsSheet(
                uiState = uiState,
                onIntent = viewModel::onIntent,
                sandboxVault = viewModel.sandboxVault
            )
        }

        if (uiState.isPasswordManagerSheetVisible) {
            PasswordManagerSheet(
                uiState = uiState,
                onIntent = viewModel::onIntent
            )
        }

        // Floating Autofill Prompt Banner (docked right above the bottom capsule bar)
        AnimatedVisibility(
            visible = uiState.activeAutofillPrompt != null && uiState.isAutofillEnabled,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 76.dp, start = 16.dp, end = 16.dp)
        ) {
            uiState.activeAutofillPrompt?.let { prompt ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("autofill_prompt_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AccentOrange.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Key,
                                        contentDescription = "Autofill key",
                                        tint = AccentOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Autofill Login",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = prompt.origin,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.onIntent(BrowserUiIntent.DismissAutofillPrompt) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("autofill_prompt_dismiss")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            prompt.credentials.take(2).forEach { credential ->
                                Button(
                                    onClick = {
                                        viewModel.onIntent(BrowserUiIntent.AutofillCredential(credential))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("autofill_button_${credential.username}")
                                ) {
                                    Text(
                                        text = credential.username.ifBlank { "Fill Password" },
                                        color = MaterialTheme.colorScheme.surface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Burn Session Confirmation Dialog
        if (uiState.isBurnConfirmVisible) {
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(BrowserUiIntent.ToggleBurnConfirm(false)) },
                title = {
                    Text("Burn Session", color = StatusError, fontSize = 18.sp)
                },
                text = {
                    Text(
                        "This will immediately close all tabs, wipe all cookies, browsing data, and delete all sandboxed downloaded files. This cannot be undone.",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.onIntent(BrowserUiIntent.ConfirmBurnSession) },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                        modifier = Modifier.testTag("burn_confirm_button")
                    ) {
                        Text("Burn Everything", color = MaterialTheme.colorScheme.surface)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.onIntent(BrowserUiIntent.ToggleBurnConfirm(false)) },
                        modifier = Modifier.testTag("burn_cancel_button")
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Permission Request Prompt
        uiState.activePermissionPrompt?.let { prompt ->
            AlertDialog(
                onDismissRequest = prompt.onDeny,
                title = { Text("Permission Request", color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Text(
                        "${prompt.origin} is requesting permission for: ${prompt.resources.joinToString(", ")}. Allow access?",
                        color = MaterialTheme.colorScheme.secondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = prompt.onGrant,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                    ) {
                        Text("Allow", color = MaterialTheme.colorScheme.surface)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = prompt.onDeny) {
                        Text("Deny", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // External Scheme Prompt
        uiState.activeExternalSchemePrompt?.let { prompt ->
            AlertDialog(
                onDismissRequest = prompt.onCancel,
                title = { Text("Open External App?", color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Text(
                        "The web page wants to open an external application:\n${prompt.url}",
                        color = MaterialTheme.colorScheme.secondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = prompt.onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                    ) {
                        Text("Open App", color = MaterialTheme.colorScheme.surface)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = prompt.onCancel) {
                        Text("Stay Here", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Save Password Confirmation Dialog (Hardware AES-256 GCM Room Vault)
        uiState.activeSavePasswordPrompt?.let { prompt ->
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(BrowserUiIntent.DismissSavePasswordPrompt) },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AccentOrange.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = "Key",
                                tint = AccentOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Save Password?",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Save credentials for ${prompt.origin} to Shadhin's encrypted vault? It will be protected by AES-256 GCM hardware encryption.",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Username: ${prompt.username}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.onIntent(
                                BrowserUiIntent.ConfirmSavePassword(
                                    origin = prompt.origin,
                                    username = prompt.username,
                                    password = prompt.password
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        modifier = Modifier.testTag("save_password_confirm_button")
                    ) {
                        Text("Save", color = MaterialTheme.colorScheme.surface)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.onIntent(BrowserUiIntent.DismissSavePasswordPrompt) },
                        modifier = Modifier.testTag("save_password_dismiss_button")
                    ) {
                        Text("Never", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
