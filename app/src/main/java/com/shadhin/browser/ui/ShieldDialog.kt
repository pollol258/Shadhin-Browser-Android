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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.shadhin.browser.viewmodel.BrowserUiIntent
import com.shadhin.browser.viewmodel.BrowserUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldDialog(
    uiState: BrowserUiState,
    onIntent: (BrowserUiIntent) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onIntent(BrowserUiIntent.ToggleShieldDialog(false)) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
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
                        imageVector = Icons.Rounded.Security,
                        contentDescription = "Shield",
                        tint = AccentOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Privacy Shield",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = { onIntent(BrowserUiIntent.ToggleShieldDialog(false)) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Shield Dialog",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Counters Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${uiState.totalBlockedTrackers}",
                        color = AccentOrange,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Trackers Blocked",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 11.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.isCosmeticFilteringEnabled) "Active" else "Disabled",
                        color = if (uiState.isCosmeticFilteringEnabled) AccentOrange else MaterialTheme.colorScheme.secondary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cosmetic Filter",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Per-Site Shield Toggle
            if (uiState.activeHost.isNotBlank()) {
                val isShieldOnForSite = !uiState.shieldDisabledHosts.contains(uiState.activeHost)
                SettingToggleRow(
                    title = "Shield for ${uiState.activeHost}",
                    subtitle = if (isShieldOnForSite) "Privacy protections active on this site" else "Shield turned off for this site",
                    checked = isShieldOnForSite,
                    onCheckedChange = { onIntent(BrowserUiIntent.ToggleShieldForHost(uiState.activeHost, it)) },
                    testTag = "toggle_site_shield"
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Toggles
            SettingToggleRow(
                title = "Cosmetic Ad-Blocking",
                subtitle = "Zero-flicker CSS injection hiding element banners & popups",
                checked = uiState.isCosmeticFilteringEnabled,
                onCheckedChange = { onIntent(BrowserUiIntent.SetCosmeticFiltering(it)) },
                testTag = "toggle_cosmetic_filter"
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingToggleRow(
                title = "Allow HTTP on Local Network",
                subtitle = "Permit unencrypted HTTP for private LAN addresses (192.168.x.x, 10.x.x.x, localhost)",
                checked = uiState.isLocalHttpAllowed,
                onCheckedChange = { onIntent(BrowserUiIntent.SetLocalHttpAllowed(it)) },
                testTag = "toggle_local_http"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // DoH Provider Selector
            Text(
                text = "DNS-over-HTTPS (RFC 8484)",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (uiState.isDohProxyActive) "Active with loopback proxy routing" else "Best-effort DoH (ProxyController fallback)",
                color = if (uiState.isDohProxyActive) AccentOrange else MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp
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
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
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

@Preview(name = "ShieldDialog - Dark Warm")
@Composable
fun PreviewShieldDialogDark() {
    ShadhinTheme(themeMode = AppThemeMode.DARK) {
        ShieldDialog(
            uiState = BrowserUiState(themeMode = AppThemeMode.DARK),
            onIntent = {}
        )
    }
}

@Preview(name = "ShieldDialog - Light Cream")
@Composable
fun PreviewShieldDialogLight() {
    ShadhinTheme(themeMode = AppThemeMode.LIGHT) {
        ShieldDialog(
            uiState = BrowserUiState(themeMode = AppThemeMode.LIGHT),
            onIntent = {}
        )
    }
}
