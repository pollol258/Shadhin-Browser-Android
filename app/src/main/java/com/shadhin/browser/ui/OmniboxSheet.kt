package com.shadhin.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadhin.browser.engine.UrlNormalizer
import com.shadhin.browser.ui.theme.AccentOrange
import com.shadhin.browser.ui.theme.AppThemeMode
import com.shadhin.browser.ui.theme.DarkCapsuleBorder
import com.shadhin.browser.ui.theme.DarkCapsuleFill
import com.shadhin.browser.ui.theme.ShadhinTheme
import com.shadhin.browser.viewmodel.BrowserUiIntent
import com.shadhin.browser.viewmodel.BrowserUiState
import com.shadhin.browser.viewmodel.SearchEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniboxSheet(
    uiState: BrowserUiState,
    onIntent: (BrowserUiIntent) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialUrl = remember { uiState.activeUrl.trim() }
    var textInput by remember { mutableStateOf(uiState.omniboxQuery.ifEmpty { uiState.activeUrl }) }
    var hasSubmitted by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        if (textInput.isNotBlank()) {
            onIntent(BrowserUiIntent.UpdateOmniboxQuery(textInput))
        }
    }

    // Handles outside-tap dismissal from ModalBottomSheet
    val handleOutsideDismiss = {
        val trimmed = textInput.trim()
        if (!hasSubmitted && trimmed.isNotEmpty() && trimmed != initialUrl && UrlNormalizer.isValidUrlOrHost(trimmed)) {
            hasSubmitted = true
            onIntent(BrowserUiIntent.LoadUrl(trimmed))
        } else {
            onIntent(BrowserUiIntent.ToggleOmnibox(false))
        }
    }

    ModalBottomSheet(
        onDismissRequest = handleOutsideDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Search or Enter Address",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = {
                        hasSubmitted = true
                        onIntent(BrowserUiIntent.ToggleOmnibox(false))
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("omnibox_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Omnibox",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Omnibox Text Input
            OutlinedTextField(
                value = textInput,
                onValueChange = {
                    textInput = it
                    onIntent(BrowserUiIntent.UpdateOmniboxQuery(it))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("omnibox_text_input"),
                placeholder = {
                    Text(
                        text = "Search with ${uiState.searchEngine.displayName} or enter URL",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search Icon",
                        tint = AccentOrange,
                        modifier = Modifier.size(22.dp)
                    )
                },
                trailingIcon = {
                    if (textInput.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                textInput = ""
                                onIntent(BrowserUiIntent.UpdateOmniboxQuery(""))
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search input",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        val trimmed = textInput.trim()
                        if (trimmed.isNotEmpty() && !hasSubmitted) {
                            hasSubmitted = true
                            onIntent(BrowserUiIntent.LoadUrl(trimmed))
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(16.dp)
            )

            // Live Fetching Suggestions Indicator
            AnimatedVisibility(
                visible = uiState.isFetchingSuggestions,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(top = 2.dp)
                        .testTag("suggestions_loading_bar"),
                    color = AccentOrange,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real-Time Search Suggestions Drop-down List
            if (uiState.searchSuggestions.isNotEmpty()) {
                Text(
                    text = "Suggestions",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag("suggestions_dropdown_list")
                ) {
                    uiState.searchSuggestions.forEachIndexed { index, suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    hasSubmitted = true
                                    onIntent(BrowserUiIntent.SelectSuggestion(suggestion))
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("suggestion_item_$index"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = suggestion,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }

                            // Tap arrow button to fill omnibox input without navigating
                            IconButton(
                                onClick = {
                                    textInput = suggestion
                                    onIntent(BrowserUiIntent.UpdateOmniboxQuery(suggestion))
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("prefill_suggestion_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NorthWest,
                                    contentDescription = "Put $suggestion in search bar",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (index < uiState.searchSuggestions.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                thickness = 0.8.dp
                            )
                        }
                    }
                }
            } else {
                // Quick Shortcuts when no suggestions or input is empty
                Text(
                    text = "Quick Shortcuts",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                val shortcuts = listOf(
                    "wikipedia.org",
                    "github.com",
                    "news.ycombinator.com",
                    "duckduckgo.com"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shortcuts.forEach { domain ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable {
                                    hasSubmitted = true
                                    onIntent(BrowserUiIntent.LoadUrl("https://$domain"))
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("shortcut_chip_$domain")
                        ) {
                            Text(
                                text = domain,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Omnibox - Suggestions Dark Warm")
@Composable
fun PreviewOmniboxDark() {
    ShadhinTheme(themeMode = AppThemeMode.DARK) {
        OmniboxSheet(
            uiState = BrowserUiState(
                omniboxQuery = "android",
                searchSuggestions = listOf(
                    "android",
                    "android studio",
                    "android auto",
                    "android 17 release date"
                ),
                themeMode = AppThemeMode.DARK
            ),
            onIntent = {}
        )
    }
}

@Preview(name = "Omnibox - Suggestions Light Cream")
@Composable
fun PreviewOmniboxLight() {
    ShadhinTheme(themeMode = AppThemeMode.LIGHT) {
        OmniboxSheet(
            uiState = BrowserUiState(
                omniboxQuery = "weather",
                searchSuggestions = listOf(
                    "weather today",
                    "weather radar",
                    "weather forecast 10 days"
                ),
                themeMode = AppThemeMode.LIGHT
            ),
            onIntent = {}
        )
    }
}
