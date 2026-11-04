package com.shadhin.browser.viewmodel

import com.shadhin.browser.engine.BrowserTab
import com.shadhin.browser.engine.QuickDialShortcut
import com.shadhin.browser.network.DohProvider
import com.shadhin.browser.storage.DownloadItem
import com.shadhin.browser.ui.theme.AppThemeMode

enum class SearchEngine(val displayName: String, val queryUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave", "https://search.brave.com/search?q=")
}

data class PermissionPrompt(
    val origin: String,
    val resources: List<String>,
    val onGrant: () -> Unit,
    val onDeny: () -> Unit
)

data class ExternalSchemePrompt(
    val url: String,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit
)

data class AutofillPrompt(
    val origin: String,
    val credentials: List<com.shadhin.browser.vault.WebCredential>
)

data class SavePasswordPrompt(
    val origin: String,
    val username: String,
    val password: String
)

data class BrowserUiState(
    val tabs: List<BrowserTab> = emptyList(),
    val activeTabId: String = "",
    val activeUrl: String = "",
    val activeTitle: String = "Shadhin Browser",
    val activeHost: String = "",
    val isLoading: Boolean = false,
    val progress: Float = 0.0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isCapsuleVisible: Boolean = true,
    val isOmniboxSheetVisible: Boolean = false,
    val isTabSwitcherVisible: Boolean = false,
    val isDownloadsSheetVisible: Boolean = false,
    val isShieldDialogVisible: Boolean = false,
    val isSettingsSheetVisible: Boolean = false,
    val isPasswordManagerSheetVisible: Boolean = false,
    val isBurnConfirmVisible: Boolean = false,
    val activeDohProvider: DohProvider = DohProvider.CLOUDFLARE,
    val isDohProxyActive: Boolean = false,
    val isCosmeticFilteringEnabled: Boolean = true,
    val isHttpsOnlyEnabled: Boolean = true,
    val isLocalHttpAllowed: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val reduceGlassEffects: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val downloads: List<DownloadItem> = emptyList(),
    val quickDials: List<QuickDialShortcut> = emptyList(),
    val isAddQuickDialDialogVisible: Boolean = false,
    val activePermissionPrompt: PermissionPrompt? = null,
    val activeExternalSchemePrompt: ExternalSchemePrompt? = null,
    val isFullScreenVideo: Boolean = false,
    val omniboxQuery: String = "",
    val searchSuggestions: List<String> = emptyList(),
    val isFetchingSuggestions: Boolean = false,
    val savedCredentials: List<com.shadhin.browser.vault.WebCredential> = emptyList(),
    val isAutofillEnabled: Boolean = true,
    val isSavePasswordsEnabled: Boolean = true,
    val activeAutofillPrompt: AutofillPrompt? = null,
    val activeSavePasswordPrompt: SavePasswordPrompt? = null,
    val isNetworkDiagnosticsEnabled: Boolean = false,
    val shieldDisabledHosts: Set<String> = emptySet(),
    val isResetSpeedDialConfirmVisible: Boolean = false,
    val editingQuickDial: QuickDialShortcut? = null,
    val lastDeletedQuickDial: Pair<Int, QuickDialShortcut>? = null,
    val snackbarMessage: String? = null
) {
    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    val totalBlockedTrackers: Int
        get() = tabs.sumOf { it.blockedTrackerCount }

    val totalCosmeticRulesApplied: Int
        get() = tabs.sumOf { it.cosmeticRulesCount }
}

sealed interface BrowserUiIntent {
    data class OpenNewTab(val url: String = "") : BrowserUiIntent
    data class CloseTab(val tabId: String) : BrowserUiIntent
    data class SelectTab(val tabId: String) : BrowserUiIntent
    data class LoadUrl(val rawInput: String) : BrowserUiIntent
    data object GoBack : BrowserUiIntent
    data object GoForward : BrowserUiIntent
    data object Reload : BrowserUiIntent
    data class OnScrollDelta(val scrollY: Int, val oldScrollY: Int) : BrowserUiIntent
    data class SetCapsuleVisibility(val isVisible: Boolean) : BrowserUiIntent
    data class ToggleOmnibox(val show: Boolean) : BrowserUiIntent
    data class UpdateOmniboxQuery(val query: String) : BrowserUiIntent
    data class SelectSuggestion(val suggestion: String) : BrowserUiIntent
    data class ToggleTabSwitcher(val show: Boolean) : BrowserUiIntent
    data class ToggleDownloadsSheet(val show: Boolean) : BrowserUiIntent
    data class ToggleShieldDialog(val show: Boolean) : BrowserUiIntent
    data class ToggleSettingsSheet(val show: Boolean) : BrowserUiIntent
    data class ToggleBurnConfirm(val show: Boolean) : BrowserUiIntent
    data class ToggleAddQuickDialDialog(val show: Boolean) : BrowserUiIntent
    data class AddQuickDial(val title: String, val url: String) : BrowserUiIntent
    data class RemoveQuickDial(val id: String) : BrowserUiIntent
    data class OpenEditQuickDial(val shortcut: QuickDialShortcut) : BrowserUiIntent
    data object DismissEditQuickDial : BrowserUiIntent
    data class ConfirmEditQuickDial(val id: String, val title: String, val url: String) : BrowserUiIntent
    data class MoveQuickDial(val id: String, val moveLeft: Boolean) : BrowserUiIntent
    data object UndoDeleteQuickDial : BrowserUiIntent
    data object AddCurrentPageToSpeedDial : BrowserUiIntent
    data class ToggleResetSpeedDialConfirm(val show: Boolean) : BrowserUiIntent
    data object ResetSpeedDialToDefaults : BrowserUiIntent
    data class SetThemeMode(val mode: AppThemeMode) : BrowserUiIntent
    data class SetReduceGlassEffects(val enabled: Boolean) : BrowserUiIntent
    data class SetDohProvider(val provider: DohProvider) : BrowserUiIntent
    data class SetCosmeticFiltering(val enabled: Boolean) : BrowserUiIntent
    data class SetHttpsOnly(val enabled: Boolean) : BrowserUiIntent
    data class SetLocalHttpAllowed(val enabled: Boolean) : BrowserUiIntent
    data class ContinueWithHttp(val url: String) : BrowserUiIntent
    data object RetryLoad : BrowserUiIntent
    data object GoToStartPage : BrowserUiIntent
    data class SetSearchEngine(val engine: SearchEngine) : BrowserUiIntent
    data class CancelDownload(val id: String) : BrowserUiIntent
    data class DeleteDownload(val id: String) : BrowserUiIntent
    data object ClearAllDownloads : BrowserUiIntent
    data object ConfirmBurnSession : BrowserUiIntent
    data class TogglePasswordManagerSheet(val show: Boolean) : BrowserUiIntent
    data class SetAutofillEnabled(val enabled: Boolean) : BrowserUiIntent
    data class SetSavePasswordsEnabled(val enabled: Boolean) : BrowserUiIntent
    data class AutofillCredential(val credential: com.shadhin.browser.vault.WebCredential) : BrowserUiIntent
    data object DismissAutofillPrompt : BrowserUiIntent
    data class ConfirmSavePassword(val origin: String, val username: String, val password: String) : BrowserUiIntent
    data object DismissSavePasswordPrompt : BrowserUiIntent
    data class DeleteCredential(val id: Long) : BrowserUiIntent
    data object ClearAllCredentials : BrowserUiIntent
    data class SetNetworkDiagnosticsEnabled(val enabled: Boolean) : BrowserUiIntent
    data class ToggleShieldForHost(val host: String, val enabled: Boolean) : BrowserUiIntent
    data object ExitFullScreenVideo : BrowserUiIntent
    data object DismissSnackbar : BrowserUiIntent
}
