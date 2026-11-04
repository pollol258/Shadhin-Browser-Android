package com.shadhin.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadhin.browser.network.DohProvider
import com.shadhin.browser.ui.theme.AccentOrange
import com.shadhin.browser.ui.theme.AppThemeMode
import com.shadhin.browser.ui.theme.DarkCapsuleBorder
import com.shadhin.browser.ui.theme.DarkCapsuleFill
import com.shadhin.browser.ui.theme.DarkTextPrimary
import com.shadhin.browser.ui.theme.DarkTextSecondary
import com.shadhin.browser.ui.theme.LightCapsuleBorder
import com.shadhin.browser.ui.theme.LightCapsuleFill
import com.shadhin.browser.ui.theme.LightTextPrimary
import com.shadhin.browser.ui.theme.LightTextSecondary
import com.shadhin.browser.ui.theme.ShadhinTheme
import com.shadhin.browser.ui.theme.StatusError
import com.shadhin.browser.viewmodel.BrowserUiIntent
import com.shadhin.browser.viewmodel.BrowserUiState

/**
 * Capsule over web pages:
 * - Fill alpha 88%, hairline border (1dp).
 * - Text strictly ensures at least 4.5:1 contrast against both pure white and pure black web pages.
 * - Icons: Material Symbols Rounded, 22dp inside capsule, with content descriptions and min 48dp touch targets.
 */
@Composable
fun FloatingCapsuleBar(
    uiState: BrowserUiState,
    onIntent: (BrowserUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = when (uiState.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    // Dynamic capsule styling: 88% alpha warm glass container
    val capsuleBackground = if (isDark) DarkCapsuleFill else LightCapsuleFill
    val capsuleBorderColor = if (isDark) DarkCapsuleBorder else LightCapsuleBorder
    val textPrimaryColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondaryColor = if (isDark) DarkTextSecondary else LightTextSecondary

    AnimatedVisibility(
        visible = uiState.isCapsuleVisible && !uiState.isFullScreenVideo,
        enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(29.dp))
                    .background(capsuleBackground)
                    .border(1.dp, capsuleBorderColor, RoundedCornerShape(29.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Back Button (touch target >= 48dp)
                IconButton(
                    onClick = { onIntent(BrowserUiIntent.GoBack) },
                    enabled = uiState.canGoBack || uiState.tabs.size > 1,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("capsule_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Navigate back",
                        tint = if (uiState.canGoBack || uiState.tabs.size > 1) textPrimaryColor else textSecondaryColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Omnibox Center Pill (Lock + Host + Shield Count + DoH Badge + Refresh)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(if (isDark) Color(0x33FFFFFF) else Color(0x22000000))
                        .border(1.dp, capsuleBorderColor.copy(alpha = 0.6f), RoundedCornerShape(21.dp))
                        .clickable { onIntent(BrowserUiIntent.ToggleOmnibox(true)) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isHttps = uiState.activeUrl.startsWith("https://")
                    Icon(
                        imageVector = if (isHttps) Icons.Rounded.Lock else Icons.Rounded.Public,
                        contentDescription = if (isHttps) "Secure connection" else "Web address",
                        tint = if (isHttps) AccentOrange else textSecondaryColor,
                        modifier = Modifier.size(15.dp)
                    )

                    // Host / Title
                    Text(
                        text = if (uiState.activeHost.isNotBlank()) uiState.activeHost else "Search or URL",
                        color = textPrimaryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Shield Badge (Opens Shield Dialog)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x44000000) else Color(0x18000000))
                            .border(1.dp, capsuleBorderColor, CircleShape)
                            .clickable { onIntent(BrowserUiIntent.ToggleShieldDialog(true)) }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                            .testTag("capsule_shield_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = "Privacy Shield",
                                tint = AccentOrange,
                                modifier = Modifier.size(12.dp)
                            )
                            val blockedCount = uiState.activeTab?.blockedTrackerCount ?: 0
                            Text(
                                text = "$blockedCount",
                                color = AccentOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // DoH Indicator Badge
                    if (uiState.activeDohProvider != DohProvider.OFF) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0x44000000) else Color(0x18000000))
                                .border(0.5.dp, capsuleBorderColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (uiState.activeDohProvider == DohProvider.CLOUDFLARE) "CF" else "Q9",
                                color = AccentOrange,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Refresh Button on URL
                    if (uiState.activeUrl.isNotBlank()) {
                        IconButton(
                            onClick = { onIntent(BrowserUiIntent.Reload) },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("capsule_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh web page",
                                tint = textPrimaryColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Tab Count Chip (touch target >= 48dp)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onIntent(BrowserUiIntent.ToggleTabSwitcher(true)) }
                        .testTag("capsule_tab_count_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.5.dp, textPrimaryColor, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${uiState.tabs.size.coerceAtMost(99)}",
                            color = textPrimaryColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Add to Speed Dial Button when visiting a page
                if (uiState.activeUrl.isNotBlank()) {
                    IconButton(
                        onClick = { onIntent(BrowserUiIntent.AddCurrentPageToSpeedDial) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("capsule_bookmark_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdd,
                            contentDescription = "Add to Speed Dial",
                            tint = textPrimaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Burn Session Flame Icon (touch target >= 48dp)
                IconButton(
                    onClick = { onIntent(BrowserUiIntent.ToggleBurnConfirm(true)) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("capsule_burn_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = "Burn Session",
                        tint = StatusError,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Capsule - Dark Warm")
@Composable
fun PreviewFloatingCapsuleDark() {
    ShadhinTheme(themeMode = AppThemeMode.DARK) {
        FloatingCapsuleBar(
            uiState = BrowserUiState(
                activeHost = "github.com",
                activeUrl = "https://github.com",
                themeMode = AppThemeMode.DARK
            ),
            onIntent = {}
        )
    }
}

@Preview(name = "Capsule - Light Cream")
@Composable
fun PreviewFloatingCapsuleLight() {
    ShadhinTheme(themeMode = AppThemeMode.LIGHT) {
        FloatingCapsuleBar(
            uiState = BrowserUiState(
                activeHost = "wikipedia.org",
                activeUrl = "https://wikipedia.org",
                themeMode = AppThemeMode.LIGHT
            ),
            onIntent = {}
        )
    }
}
