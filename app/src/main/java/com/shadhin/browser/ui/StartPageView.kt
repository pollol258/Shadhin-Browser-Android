package com.shadhin.browser.ui

import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Base64
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadhin.browser.engine.QuickDialShortcut
import com.shadhin.browser.engine.UrlNormalizer
import com.shadhin.browser.ui.theme.AccentOrange
import com.shadhin.browser.ui.theme.AppThemeMode
import com.shadhin.browser.ui.theme.ShadhinTheme
import com.shadhin.browser.ui.theme.StatusError
import com.shadhin.browser.viewmodel.BrowserUiIntent
import com.shadhin.browser.viewmodel.BrowserUiState

@Composable
fun StartPageView(
    uiState: BrowserUiState,
    onIntent: (BrowserUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val transitionScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1.0f
    )
    val animationsEnabled = transitionScale > 0f

    // One-time staggered entrance animation (300ms total, 12dp rise)
    val animOffset = remember { Animatable(if (animationsEnabled) 12f else 0f) }
    val animAlpha = remember { Animatable(if (animationsEnabled) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            animAlpha.animateTo(1f, animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            animOffset.animateTo(0f, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(top = 36.dp, bottom = 100.dp)
                .offset(y = animOffset.value.dp)
                .alpha(animAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row with Settings Gear Action (Touch target 48dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = { onIntent(BrowserUiIntent.ToggleSettingsSheet(true)) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("start_page_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Browser Settings",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Warm Glass Emblem with Shadhin Browser Soaring Bird Symbol
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_shadhin_logo),
                    contentDescription = "Shadhin Browser Logo",
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Shadhin Browser",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Text(
                text = "Warm Glass Privacy Capsule",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Live Metrics Dashboard (Cached static card layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricColumn(
                    label = "Trackers Blocked",
                    value = "${uiState.totalBlockedTrackers}",
                    highlight = true
                )
                MetricColumn(
                    label = "DoH Resolver",
                    value = if (uiState.isDohProxyActive) "Tunnel Active" else uiState.activeDohProvider.name,
                    highlight = false
                )
                MetricColumn(
                    label = "Cosmetic Hiding",
                    value = if (uiState.isCosmeticFilteringEnabled) "Enabled" else "Off",
                    highlight = false
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Dials Header with Add Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Quick Dials (${uiState.quickDials.size})",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable { onIntent(BrowserUiIntent.ToggleAddQuickDialDialog(true)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("add_quick_dial_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add Quick Dial",
                        tint = AccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Add",
                        color = AccentOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(uiState.quickDials, key = { _, it -> it.id }) { index, dial ->
                    QuickDialCard(
                        dial = dial,
                        canMoveLeft = index > 0,
                        canMoveRight = index < uiState.quickDials.size - 1,
                        onClick = { onIntent(BrowserUiIntent.LoadUrl(dial.url)) },
                        onEdit = { onIntent(BrowserUiIntent.OpenEditQuickDial(dial)) },
                        onDelete = { onIntent(BrowserUiIntent.RemoveQuickDial(dial.id)) },
                        onMove = { moveLeft -> onIntent(BrowserUiIntent.MoveQuickDial(dial.id, moveLeft)) }
                    )
                }

                // Add shortcut tile at the end of the grid
                item(key = "add_shortcut_tile") {
                    AddShortcutCard(
                        onClick = { onIntent(BrowserUiIntent.ToggleAddQuickDialDialog(true)) }
                    )
                }
            }
        }

        // Undo Snackbar when a quick dial is deleted
        if (uiState.snackbarMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            ) {
                Snackbar(
                    action = {
                        TextButton(
                            onClick = { onIntent(BrowserUiIntent.UndoDeleteQuickDial) }
                        ) {
                            Text(
                                text = "Undo",
                                color = AccentOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissAction = {
                        TextButton(
                            onClick = { onIntent(BrowserUiIntent.DismissSnackbar) }
                        ) {
                            Text(
                                text = "Dismiss",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("quick_dial_snackbar")
                ) {
                    Text(uiState.snackbarMessage)
                }
            }
        }
    }

    // Add Shortcut Dialog
    if (uiState.isAddQuickDialDialogVisible) {
        AddQuickDialDialog(
            onDismiss = { onIntent(BrowserUiIntent.ToggleAddQuickDialDialog(false)) },
            onConfirm = { title, url -> onIntent(BrowserUiIntent.AddQuickDial(title, url)) }
        )
    }

    // Edit Shortcut Dialog
    uiState.editingQuickDial?.let { shortcut ->
        EditQuickDialDialog(
            shortcut = shortcut,
            onDismiss = { onIntent(BrowserUiIntent.DismissEditQuickDial) },
            onConfirm = { id, title, url ->
                onIntent(BrowserUiIntent.ConfirmEditQuickDial(id, title, url))
            }
        )
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    highlight: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = if (highlight) AccentOrange else MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 10.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickDialCard(
    dial: QuickDialShortcut,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (moveLeft: Boolean) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(8.dp)
            .testTag("quick_dial_${dial.id}")
    ) {
        // Small 3-dots more button for discoverability
        Box(
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(28.dp)
                    .testTag("quick_dial_more_${dial.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Quick dial options",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        showMenu = false
                        onEdit()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = AccentOrange
                        )
                    }
                )

                if (canMoveLeft) {
                    DropdownMenuItem(
                        text = { Text("Move left") },
                        onClick = {
                            showMenu = false
                            onMove(true)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )
                }

                if (canMoveRight) {
                    DropdownMenuItem(
                        text = { Text("Move right") },
                        onClick = {
                            showMenu = false
                            onMove(false)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Delete", color = StatusError) },
                    onClick = {
                        showMenu = false
                        showDeleteConfirm = true
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = StatusError
                        )
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Favicon circle with fallback: first letter in colored glass circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val faviconBitmap = remember(dial.faviconBase64) {
                    dial.faviconBase64?.let { base64 ->
                        try {
                            val bytes = Base64.decode(base64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                }

                if (faviconBitmap != null) {
                    Image(
                        bitmap = faviconBitmap,
                        contentDescription = dial.title,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                } else {
                    val initial = dial.title.trim().take(1).uppercase()
                    if (initial.isNotBlank() && initial[0].isLetterOrDigit()) {
                        Text(
                            text = initial,
                            color = AccentOrange,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = dial.title,
                            tint = AccentOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = dial.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Shortcut?", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove \"${dial.title}\" from quick dials?", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun AddShortcutCard(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag("add_shortcut_tile"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentOrange.copy(alpha = 0.15f))
                    .border(1.dp, AccentOrange.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add shortcut",
                    tint = AccentOrange,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Add Shortcut",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AddQuickDialDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val isValidUrl = remember(url) {
        url.isNotBlank() && UrlNormalizer.isValidUrlOrHost(url)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Quick Dial", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name (optional)") },
                    placeholder = { Text("e.g. Reddit") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = AccentOrange,
                        unfocusedLabelColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("e.g. reddit.com") },
                    isError = url.isNotBlank() && !isValidUrl,
                    supportingText = {
                        if (url.isNotBlank() && !isValidUrl) {
                            Text("Please enter a valid domain or web address", color = StatusError, fontSize = 11.sp)
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = AccentOrange,
                        unfocusedLabelColor = MaterialTheme.colorScheme.secondary,
                        errorBorderColor = StatusError
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValidUrl) {
                        onConfirm(title, url)
                    }
                },
                enabled = isValidUrl,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
            ) {
                Text("Add", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun EditQuickDialDialog(
    shortcut: QuickDialShortcut,
    onDismiss: () -> Unit,
    onConfirm: (id: String, title: String, url: String) -> Unit
) {
    var title by remember { mutableStateOf(shortcut.title) }
    var url by remember { mutableStateOf(shortcut.url) }
    val isValidUrl = remember(url) {
        url.isNotBlank() && UrlNormalizer.isValidUrlOrHost(url)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Quick Dial", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = AccentOrange,
                        unfocusedLabelColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    isError = url.isNotBlank() && !isValidUrl,
                    supportingText = {
                        if (url.isNotBlank() && !isValidUrl) {
                            Text("Please enter a valid domain or web address", color = StatusError, fontSize = 11.sp)
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = AccentOrange,
                        unfocusedLabelColor = MaterialTheme.colorScheme.secondary,
                        errorBorderColor = StatusError
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValidUrl) {
                        onConfirm(shortcut.id, title, url)
                    }
                },
                enabled = isValidUrl,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
            ) {
                Text("Save", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Preview(name = "StartPage - Dark Warm")
@Composable
fun PreviewStartPageDark() {
    ShadhinTheme(themeMode = AppThemeMode.DARK) {
        StartPageView(
            uiState = BrowserUiState(themeMode = AppThemeMode.DARK),
            onIntent = {}
        )
    }
}

@Preview(name = "StartPage - Light Cream")
@Composable
fun PreviewStartPageLight() {
    ShadhinTheme(themeMode = AppThemeMode.LIGHT) {
        StartPageView(
            uiState = BrowserUiState(themeMode = AppThemeMode.LIGHT),
            onIntent = {}
        )
    }
}
