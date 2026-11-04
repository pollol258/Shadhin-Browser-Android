package com.shadhin.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadhin.browser.network.DohProvider
import com.shadhin.browser.ui.theme.AccentOrange
import com.shadhin.browser.ui.theme.ActiveTabBackground
import com.shadhin.browser.ui.theme.ActiveTabText
import com.shadhin.browser.ui.theme.AppThemeMode
import com.shadhin.browser.ui.theme.ShadhinTheme
import com.shadhin.browser.ui.theme.StatusError
import com.shadhin.browser.viewmodel.BrowserUiIntent
import com.shadhin.browser.viewmodel.BrowserUiState
import com.shadhin.browser.viewmodel.SearchEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    uiState: BrowserUiState,
    onIntent: (BrowserUiIntent) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = { onIntent(BrowserUiIntent.ToggleSettingsSheet(false)) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Browser Settings",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { onIntent(BrowserUiIntent.ToggleSettingsSheet(false)) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Settings",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Search Engine
            SettingsCard(title = "Search Engine", icon = Icons.Rounded.Search) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchEngine.entries.forEach { engine ->
                        val isSelected = uiState.searchEngine == engine
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) ActiveTabBackground else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentOrange else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onIntent(BrowserUiIntent.SetSearchEngine(engine)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = engine.displayName,
                                color = if (isSelected) ActiveTabText else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Privacy (DoH, HTTPS-only, Local HTTP)
            SettingsCard(title = "Privacy & Security", icon = Icons.Rounded.Security) {
                Text(
                    text = "DNS-over-HTTPS Provider",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DohProvider.entries.forEach { provider ->
                        val isSelected = uiState.activeDohProvider == provider
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) ActiveTabBackground else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentOrange else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onIntent(BrowserUiIntent.SetDohProvider(provider)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (provider) {
                                    DohProvider.CLOUDFLARE -> "Cloudflare"
                                    DohProvider.QUAD9 -> "Quad9"
                                    DohProvider.OFF -> "Off"
                                },
                                color = if (isSelected) ActiveTabText else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "HTTPS-Only Mode",
                    subtitle = "Always upgrade unencrypted connections to HTTPS first",
                    checked = uiState.isHttpsOnlyEnabled,
                    onCheckedChange = { onIntent(BrowserUiIntent.SetHttpsOnly(it)) },
                    testTag = "settings_toggle_https_only"
                )

                Spacer(modifier = Modifier.height(10.dp))

                SettingsToggleRow(
                    title = "Allow HTTP on Local Network",
                    subtitle = "Permit HTTP on 192.168.x.x, 10.x.x.x, 172.16-31.x.x, localhost",
                    checked = uiState.isLocalHttpAllowed,
                    onCheckedChange = { onIntent(BrowserUiIntent.SetLocalHttpAllowed(it)) },
                    testTag = "settings_toggle_local_http"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 3: Appearance (Theme & Reduce glass effects)
            SettingsCard(title = "Appearance & Performance", icon = Icons.Rounded.Palette) {
                Text(
                    text = "Theme",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val isSelected = uiState.themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) ActiveTabBackground else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentOrange else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onIntent(BrowserUiIntent.SetThemeMode(mode)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.displayName.substringBefore(" "),
                                color = if (isSelected) ActiveTabText else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Reduce Glass Effects",
                    subtitle = "Disables blur and renders solid translucent surfaces for high battery & FPS",
                    checked = uiState.reduceGlassEffects,
                    onCheckedChange = { onIntent(BrowserUiIntent.SetReduceGlassEffects(it)) },
                    testTag = "settings_toggle_reduce_glass"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section: Passwords & Autofill (Encrypted Room Vault)
            SettingsCard(title = "Passwords & Autofill", icon = Icons.Rounded.Key) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable { onIntent(BrowserUiIntent.TogglePasswordManagerSheet(true)) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("settings_open_password_manager"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Saved Passwords",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${uiState.savedCredentials.size} credentials stored securely",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = "Open Password Manager",
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                SettingsToggleRow(
                    title = "Auto-fill Login Credentials",
                    subtitle = "Automatically fill saved usernames and passwords on web pages",
                    checked = uiState.isAutofillEnabled,
                    onCheckedChange = { onIntent(BrowserUiIntent.SetAutofillEnabled(it)) },
                    testTag = "settings_toggle_autofill"
                )

                Spacer(modifier = Modifier.height(10.dp))

                SettingsToggleRow(
                    title = "Ask to Save Passwords",
                    subtitle = "Show save prompt when submitting web login forms",
                    checked = uiState.isSavePasswordsEnabled,
                    onCheckedChange = { onIntent(BrowserUiIntent.SetSavePasswordsEnabled(it)) },
                    testTag = "settings_toggle_save_passwords"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 4: Data (Burn session, Clear downloads)
            SettingsCard(title = "Data & Vault", icon = Icons.Rounded.Speed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable { onIntent(BrowserUiIntent.ClearAllDownloads) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clear Sandboxed Downloads",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Remove all files stored in private vault",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear downloads",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, StatusError.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { onIntent(BrowserUiIntent.ToggleBurnConfirm(true)) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Burn Session",
                            color = StatusError,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Wipe tabs, cookies, cache, databases, and sandbox",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = "Burn Session",
                        tint = StatusError,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 5: Start Page & Speed Dial
            SettingsCard(title = "Start Page & Speed Dial", icon = Icons.Rounded.Speed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable { onIntent(BrowserUiIntent.ToggleResetSpeedDialConfirm(true)) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("settings_reset_speed_dial"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reset Speed Dial to Defaults",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Restore initial presets (GitHub, Hacker News, Wikipedia, EFF...)",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = "Reset speed dial",
                        tint = AccentOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 6: Advanced & Diagnostics
            SettingsCard(title = "Advanced & Diagnostics", icon = Icons.Rounded.Code) {
                SettingsToggleRow(
                    title = "Network Diagnostics (ShadhinNet)",
                    subtitle = "Log main-frame navigations, DNS elapsed ms, and display collapsible details on error pages",
                    checked = uiState.isNetworkDiagnosticsEnabled,
                    onCheckedChange = { onIntent(BrowserUiIntent.SetNetworkDiagnosticsEnabled(it)) },
                    testTag = "settings_toggle_network_diagnostics"
                )
            }
        }

        // Reset Speed Dial Confirmation Dialog
        if (uiState.isResetSpeedDialConfirmVisible) {
            AlertDialog(
                onDismissRequest = { onIntent(BrowserUiIntent.ToggleResetSpeedDialConfirm(false)) },
                title = { Text("Reset Speed Dial?", color = MaterialTheme.colorScheme.onSurface) },
                text = { Text("This will reset all your speed dial shortcuts back to the initial default presets.", color = MaterialTheme.colorScheme.secondary) },
                confirmButton = {
                    Button(
                        onClick = { onIntent(BrowserUiIntent.ResetSpeedDialToDefaults) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.surface)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { onIntent(BrowserUiIntent.ToggleResetSpeedDialConfirm(false)) }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = AccentOrange,
                uncheckedThumbColor = MaterialTheme.colorScheme.secondary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Preview(name = "Settings - Dark Warm")
@Composable
fun PreviewSettingsDark() {
    ShadhinTheme(themeMode = AppThemeMode.DARK) {
        SettingsSheet(
            uiState = BrowserUiState(themeMode = AppThemeMode.DARK),
            onIntent = {}
        )
    }
}

@Preview(name = "Settings - Light Cream")
@Composable
fun PreviewSettingsLight() {
    ShadhinTheme(themeMode = AppThemeMode.LIGHT) {
        SettingsSheet(
            uiState = BrowserUiState(themeMode = AppThemeMode.LIGHT),
            onIntent = {}
        )
    }
}
