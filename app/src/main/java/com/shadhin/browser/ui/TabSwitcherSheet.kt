package com.shadhin.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadhin.browser.engine.BrowserTab
import com.shadhin.browser.ui.theme.AccentOrange
import com.shadhin.browser.ui.theme.ActiveTabBackground
import com.shadhin.browser.ui.theme.ActiveTabText
import com.shadhin.browser.ui.theme.AppThemeMode
import com.shadhin.browser.ui.theme.ShadhinTheme
import com.shadhin.browser.viewmodel.BrowserUiIntent
import com.shadhin.browser.viewmodel.BrowserUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherSheet(
    uiState: BrowserUiState,
    onIntent: (BrowserUiIntent) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = { onIntent(BrowserUiIntent.ToggleTabSwitcher(false)) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(18.dp)
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
                    Text(
                        text = "Tabs (${uiState.tabs.size})",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Max 3 Live WebViews",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = { onIntent(BrowserUiIntent.OpenNewTab("")) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("tab_switcher_add_tab_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New Tab",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "New Tab",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grid of Tabs
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                items(uiState.tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == uiState.activeTabId
                    TabCard(
                        tab = tab,
                        isActive = isActive,
                        onSelect = { onIntent(BrowserUiIntent.SelectTab(tab.id)) },
                        onClose = { onIntent(BrowserUiIntent.CloseTab(tab.id)) }
                    )
                }
            }
        }
    }
}

@Composable
fun TabCard(
    tab: BrowserTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    // Decision 5: Active tab uses solid soft white #FFF8F3 with dark brown text
    val cardBackground = if (isActive) ActiveTabBackground else MaterialTheme.colorScheme.surfaceVariant
    val cardBorder = if (isActive) AccentOrange else MaterialTheme.colorScheme.outline
    val titleColor = if (isActive) ActiveTabText else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isActive) ActiveTabText.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cardBackground)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = cardBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onSelect)
            .padding(12.dp)
            .testTag("tab_card_${tab.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = "Website",
                    tint = if (isActive) AccentOrange else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close tab",
                        tint = if (isActive) ActiveTabText else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = tab.title.takeIf { it.isNotBlank() } ?: "New Tab",
                color = titleColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (tab.url.isNotBlank()) tab.url.removePrefix("https://").removePrefix("http://") else "Start Page",
                    color = subtitleColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (!tab.isAlive && tab.historyBundle != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) ActiveTabText.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Hibernated",
                            color = subtitleColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "TabSwitcher - Dark Warm")
@Composable
fun PreviewTabSwitcherDark() {
    ShadhinTheme(themeMode = AppThemeMode.DARK) {
        TabSwitcherSheet(
            uiState = BrowserUiState(
                tabs = listOf(
                    BrowserTab(id = "1", title = "GitHub - Building software", url = "https://github.com", isAlive = true),
                    BrowserTab(id = "2", title = "Hacker News", url = "https://news.ycombinator.com", isAlive = false, historyBundle = android.os.Bundle())
                ),
                activeTabId = "1",
                themeMode = AppThemeMode.DARK
            ),
            onIntent = {}
        )
    }
}

@Preview(name = "TabSwitcher - Light Cream")
@Composable
fun PreviewTabSwitcherLight() {
    ShadhinTheme(themeMode = AppThemeMode.LIGHT) {
        TabSwitcherSheet(
            uiState = BrowserUiState(
                tabs = listOf(
                    BrowserTab(id = "1", title = "Wikipedia - Free Encyclopedia", url = "https://wikipedia.org", isAlive = true),
                    BrowserTab(id = "2", title = "ArXiv Papers", url = "https://arxiv.org", isAlive = false, historyBundle = android.os.Bundle())
                ),
                activeTabId = "1",
                themeMode = AppThemeMode.LIGHT
            ),
            onIntent = {}
        )
    }
}
