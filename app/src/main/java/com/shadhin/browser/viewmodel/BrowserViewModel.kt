package com.shadhin.browser.viewmodel

import android.app.Application
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shadhin.browser.engine.SecureWebChromeClient
import com.shadhin.browser.engine.SecureWebViewClient
import com.shadhin.browser.engine.TabManager
import com.shadhin.browser.engine.UrlNormalizer
import com.shadhin.browser.engine.WebViewSecurityConfig
import com.shadhin.browser.filter.CosmeticFilterEngine
import com.shadhin.browser.filter.PrivacyFilterEngine
import com.shadhin.browser.network.DohProvider
import com.shadhin.browser.network.DohResolver
import com.shadhin.browser.network.LocalDohProxy
import com.shadhin.browser.storage.DownloaderEngine
import com.shadhin.browser.storage.QuickDialRepository
import com.shadhin.browser.storage.SandboxFileVault
import com.shadhin.browser.engine.SearchSuggestionEngine
import com.shadhin.browser.vault.PasswordAutofillBridge
import com.shadhin.browser.vault.PasswordDatabase
import com.shadhin.browser.vault.WebCredential
import com.shadhin.browser.vault.WebCredentialRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

interface FullScreenHost {
    fun onEnterFullScreen(view: android.view.View, callback: android.webkit.WebChromeClient.CustomViewCallback)
    fun onExitFullScreen()
}

class BrowserViewModel(
    application: Application
) : AndroidViewModel(application) {

    var fullScreenHost: FullScreenHost? = null

    val tabManager = TabManager(maxLiveWebViews = 3)
    val cosmeticFilterEngine = CosmeticFilterEngine()
    val privacyFilterEngine = PrivacyFilterEngine()
    val dohResolver = DohResolver(DohProvider.CLOUDFLARE)
    val localDohProxy = LocalDohProxy(dohResolver)
    val quickDialRepository = QuickDialRepository(application, dohResolver)
    val searchSuggestionEngine = SearchSuggestionEngine(dohResolver)
    val sandboxVault = SandboxFileVault(application)
    val downloaderEngine = DownloaderEngine(application, dohResolver, sandboxVault, viewModelScope)
    val passwordDatabase = PasswordDatabase.getInstance(application)
    val credentialRepository = WebCredentialRepository(passwordDatabase.credentialDao())
    private val omniboxQueryFlow = MutableStateFlow("")
    // Temporary per-host cleartext HTTP allowance set (host -> true) for one-time explicit user continues
    private val temporaryCleartextExceptions = ConcurrentHashMap.newKeySet<String>()

    private val isLowRamDevice: Boolean by lazy {
        val am = application.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        if (am != null) {
            am.isLowRamDevice || am.memoryClass < 256
        } else {
            false
        }
    }

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            activeDohProvider = DohProvider.CLOUDFLARE,
            isCosmeticFilteringEnabled = true,
            isHttpsOnlyEnabled = true,
            isLocalHttpAllowed = false,
            themeMode = com.shadhin.browser.ui.theme.AppThemeMode.DARK,
            reduceGlassEffects = isLowRamDevice,
            searchEngine = SearchEngine.GOOGLE,
            quickDials = quickDialRepository.loadShortcuts()
        )
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        // Observe tabs and sync active state
        viewModelScope.launch {
            tabManager.tabs.collect { tabList ->
                _uiState.update { current ->
                    val active = tabList.firstOrNull { it.id == current.activeTabId }
                    current.copy(
                        tabs = tabList,
                        activeUrl = active?.url.orEmpty(),
                        activeTitle = active?.title.takeIf { !it.isNullOrBlank() } ?: "Shadhin Browser",
                        activeHost = extractHost(active?.url)
                    )
                }
            }
        }

        viewModelScope.launch {
            tabManager.activeTabId.collect { activeId ->
                _uiState.update { current ->
                    val active = current.tabs.firstOrNull { it.id == activeId }
                    current.copy(
                        activeTabId = activeId,
                        activeUrl = active?.url.orEmpty(),
                        activeTitle = active?.title.takeIf { !it.isNullOrBlank() } ?: "Shadhin Browser",
                        activeHost = extractHost(active?.url)
                    )
                }
            }
        }

        // Observe downloads
        viewModelScope.launch {
            downloaderEngine.downloads.collect { list ->
                _uiState.update { it.copy(downloads = list) }
            }
        }

        // Fetch favicons in background
        viewModelScope.launch {
            val refreshed = quickDialRepository.refreshFavicons(_uiState.value.quickDials)
            _uiState.update { it.copy(quickDials = refreshed) }
        }

        // Real-time debounced search suggestion fetching from Google Suggest API
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            omniboxQueryFlow
                .debounce(250)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (trimmed.isEmpty() || UrlNormalizer.isLikelyWebAddress(trimmed) || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        _uiState.update { it.copy(searchSuggestions = emptyList(), isFetchingSuggestions = false) }
                    } else {
                        _uiState.update { it.copy(isFetchingSuggestions = true) }
                        val suggestions = searchSuggestionEngine.fetchSuggestions(trimmed)
                        _uiState.update { it.copy(searchSuggestions = suggestions, isFetchingSuggestions = false) }
                    }
                }
        }

        // Observe encrypted password credentials from Room database
        viewModelScope.launch {
            credentialRepository.allCredentials.collect { list ->
                _uiState.update { it.copy(savedCredentials = list) }
            }
        }

        setupDohProxy()
    }

    private fun setupDohProxy() {
        val attached = localDohProxy.attachToWebView(getApplication())
        _uiState.update { it.copy(isDohProxyActive = attached) }
    }

    fun onIntent(intent: BrowserUiIntent) {
        when (intent) {
            is BrowserUiIntent.OpenNewTab -> {
                tabManager.openNewTab(intent.url)
                _uiState.update { it.copy(isTabSwitcherVisible = false) }
            }
            is BrowserUiIntent.CloseTab -> {
                tabManager.closeTab(intent.tabId)
            }
            is BrowserUiIntent.SelectTab -> {
                tabManager.switchTab(intent.tabId)
                _uiState.update { it.copy(isTabSwitcherVisible = false) }
            }
            is BrowserUiIntent.LoadUrl -> {
                handleLoadUrl(intent.rawInput)
            }
            is BrowserUiIntent.GoBack -> {
                val live = tabManager.detachActiveWebView()
                if (live != null && live.canGoBack()) {
                    live.goBack()
                } else if (_uiState.value.tabs.size > 1) {
                    tabManager.closeTab(_uiState.value.activeTabId)
                }
            }
            is BrowserUiIntent.GoForward -> {
                tabManager.detachActiveWebView()?.goForward()
            }
            is BrowserUiIntent.Reload -> {
                tabManager.detachActiveWebView()?.reload()
            }
            is BrowserUiIntent.RetryLoad -> {
                val url = _uiState.value.activeUrl
                if (url.isNotBlank()) {
                    tabManager.detachActiveWebView()?.loadUrl(url)
                }
            }
            is BrowserUiIntent.GoToStartPage -> {
                tabManager.updateTabMetadata(_uiState.value.activeTabId, title = "Shadhin Browser", url = "")
                tabManager.detachActiveWebView()?.loadUrl("about:blank")
                _uiState.update {
                    it.copy(activeUrl = "", activeTitle = "Shadhin Browser", activeHost = "")
                }
            }
            is BrowserUiIntent.ContinueWithHttp -> {
                handleContinueWithHttp(intent.url)
            }
            is BrowserUiIntent.OnScrollDelta -> {
                val delta = intent.scrollY - intent.oldScrollY
                if (delta > 15) {
                    _uiState.update { it.copy(isCapsuleVisible = false) }
                } else if (delta < -15 || intent.scrollY <= 10) {
                    _uiState.update { it.copy(isCapsuleVisible = true) }
                }
            }
            is BrowserUiIntent.SetCapsuleVisibility -> {
                _uiState.update { it.copy(isCapsuleVisible = intent.isVisible) }
            }
            is BrowserUiIntent.ToggleOmnibox -> {
                val currentUrl = if (intent.show) _uiState.value.activeUrl else ""
                _uiState.update {
                    it.copy(
                        isOmniboxSheetVisible = intent.show,
                        omniboxQuery = currentUrl,
                        searchSuggestions = emptyList(),
                        isFetchingSuggestions = false
                    )
                }
                omniboxQueryFlow.value = currentUrl
            }
            is BrowserUiIntent.UpdateOmniboxQuery -> {
                _uiState.update { it.copy(omniboxQuery = intent.query) }
                omniboxQueryFlow.value = intent.query
            }
            is BrowserUiIntent.SelectSuggestion -> {
                handleLoadUrl(intent.suggestion)
            }
            is BrowserUiIntent.ToggleTabSwitcher -> {
                _uiState.update { it.copy(isTabSwitcherVisible = intent.show) }
            }
            is BrowserUiIntent.ToggleDownloadsSheet -> {
                _uiState.update { it.copy(isDownloadsSheetVisible = intent.show) }
            }
            is BrowserUiIntent.ToggleShieldDialog -> {
                _uiState.update { it.copy(isShieldDialogVisible = intent.show) }
            }
            is BrowserUiIntent.ToggleSettingsSheet -> {
                _uiState.update { it.copy(isSettingsSheetVisible = intent.show) }
            }
            is BrowserUiIntent.ToggleBurnConfirm -> {
                _uiState.update { it.copy(isBurnConfirmVisible = intent.show) }
            }
            is BrowserUiIntent.ToggleAddQuickDialDialog -> {
                _uiState.update { it.copy(isAddQuickDialDialogVisible = intent.show) }
            }
            is BrowserUiIntent.AddQuickDial -> {
                viewModelScope.launch {
                    quickDialRepository.addShortcut(intent.title, intent.url)
                    val updated = quickDialRepository.loadShortcuts()
                    _uiState.update { it.copy(quickDials = updated, isAddQuickDialDialogVisible = false) }
                }
            }
            is BrowserUiIntent.RemoveQuickDial -> {
                val index = _uiState.value.quickDials.indexOfFirst { it.id == intent.id }
                val (updated, deleted) = quickDialRepository.deleteShortcut(intent.id)
                _uiState.update {
                    it.copy(
                        quickDials = updated,
                        lastDeletedQuickDial = if (deleted != null && index != -1) Pair(index, deleted) else null,
                        snackbarMessage = if (deleted != null) "Deleted \"${deleted.title}\"" else null
                    )
                }
            }
            is BrowserUiIntent.OpenEditQuickDial -> {
                _uiState.update { it.copy(editingQuickDial = intent.shortcut) }
            }
            is BrowserUiIntent.DismissEditQuickDial -> {
                _uiState.update { it.copy(editingQuickDial = null) }
            }
            is BrowserUiIntent.ConfirmEditQuickDial -> {
                viewModelScope.launch {
                    val updated = quickDialRepository.editShortcut(intent.id, intent.title, intent.url)
                    _uiState.update { it.copy(quickDials = updated, editingQuickDial = null) }
                }
            }
            is BrowserUiIntent.MoveQuickDial -> {
                val updated = quickDialRepository.moveShortcut(intent.id, intent.moveLeft)
                _uiState.update { it.copy(quickDials = updated) }
            }
            is BrowserUiIntent.UndoDeleteQuickDial -> {
                val deletedPair = _uiState.value.lastDeletedQuickDial
                if (deletedPair != null) {
                    val restored = quickDialRepository.restoreShortcut(deletedPair.second, deletedPair.first)
                    _uiState.update {
                        it.copy(
                            quickDials = restored,
                            lastDeletedQuickDial = null,
                            snackbarMessage = "Restored \"${deletedPair.second.title}\""
                        )
                    }
                }
            }
            is BrowserUiIntent.AddCurrentPageToSpeedDial -> {
                val currentUrl = _uiState.value.activeUrl
                val currentTitle = _uiState.value.activeTitle
                if (currentUrl.isNotBlank()) {
                    viewModelScope.launch {
                        quickDialRepository.addShortcut(currentTitle, currentUrl)
                        val updated = quickDialRepository.loadShortcuts()
                        _uiState.update { it.copy(quickDials = updated, snackbarMessage = "Added \"$currentTitle\" to Speed Dial") }
                    }
                }
            }
            is BrowserUiIntent.ToggleResetSpeedDialConfirm -> {
                _uiState.update { it.copy(isResetSpeedDialConfirmVisible = intent.show) }
            }
            is BrowserUiIntent.ResetSpeedDialToDefaults -> {
                val defaults = quickDialRepository.resetToDefaults()
                _uiState.update {
                    it.copy(
                        quickDials = defaults,
                        isResetSpeedDialConfirmVisible = false,
                        snackbarMessage = "Speed dial reset to defaults"
                    )
                }
            }
            is BrowserUiIntent.SetThemeMode -> {
                _uiState.update { it.copy(themeMode = intent.mode) }
            }
            is BrowserUiIntent.SetReduceGlassEffects -> {
                _uiState.update { it.copy(reduceGlassEffects = intent.enabled) }
            }
            is BrowserUiIntent.ClearAllDownloads -> {
                downloaderEngine.clearAllDownloads()
            }
            is BrowserUiIntent.SetDohProvider -> {
                dohResolver.setProvider(intent.provider)
                if (intent.provider == DohProvider.OFF) {
                    localDohProxy.detachFromWebView()
                    _uiState.update { it.copy(activeDohProvider = intent.provider, isDohProxyActive = false) }
                } else {
                    val attached = localDohProxy.attachToWebView(getApplication())
                    _uiState.update { it.copy(activeDohProvider = intent.provider, isDohProxyActive = attached) }
                }
            }
            is BrowserUiIntent.SetCosmeticFiltering -> {
                _uiState.update { it.copy(isCosmeticFilteringEnabled = intent.enabled) }
            }
            is BrowserUiIntent.SetHttpsOnly -> {
                _uiState.update { it.copy(isHttpsOnlyEnabled = intent.enabled) }
            }
            is BrowserUiIntent.SetLocalHttpAllowed -> {
                _uiState.update { it.copy(isLocalHttpAllowed = intent.enabled) }
            }
            is BrowserUiIntent.SetSearchEngine -> {
                _uiState.update { it.copy(searchEngine = intent.engine) }
            }
            is BrowserUiIntent.CancelDownload -> {
                downloaderEngine.cancelDownload(intent.id)
            }
            is BrowserUiIntent.DeleteDownload -> {
                downloaderEngine.deleteDownload(intent.id)
            }
            is BrowserUiIntent.ConfirmBurnSession -> {
                performBurnSession()
            }
            is BrowserUiIntent.TogglePasswordManagerSheet -> {
                _uiState.update { it.copy(isPasswordManagerSheetVisible = intent.show) }
            }
            is BrowserUiIntent.SetAutofillEnabled -> {
                _uiState.update { it.copy(isAutofillEnabled = intent.enabled, activeAutofillPrompt = if (!intent.enabled) null else it.activeAutofillPrompt) }
            }
            is BrowserUiIntent.SetSavePasswordsEnabled -> {
                _uiState.update { it.copy(isSavePasswordsEnabled = intent.enabled, activeSavePasswordPrompt = if (!intent.enabled) null else it.activeSavePasswordPrompt) }
            }
            is BrowserUiIntent.AutofillCredential -> {
                handleAutofillCredential(intent.credential)
            }
            is BrowserUiIntent.DismissAutofillPrompt -> {
                _uiState.update { it.copy(activeAutofillPrompt = null) }
            }
            is BrowserUiIntent.ConfirmSavePassword -> {
                viewModelScope.launch {
                    credentialRepository.saveCredential(
                        origin = intent.origin,
                        username = intent.username,
                        password = intent.password
                    )
                }
                _uiState.update { it.copy(activeSavePasswordPrompt = null) }
            }
            is BrowserUiIntent.DismissSavePasswordPrompt -> {
                _uiState.update { it.copy(activeSavePasswordPrompt = null) }
            }
            is BrowserUiIntent.DeleteCredential -> {
                viewModelScope.launch {
                    credentialRepository.deleteCredential(intent.id)
                }
            }
            is BrowserUiIntent.ClearAllCredentials -> {
                viewModelScope.launch {
                    credentialRepository.deleteAll()
                }
            }
            is BrowserUiIntent.SetNetworkDiagnosticsEnabled -> {
                _uiState.update { it.copy(isNetworkDiagnosticsEnabled = intent.enabled) }
            }
            is BrowserUiIntent.ToggleShieldForHost -> {
                privacyFilterEngine.setShieldDisabled(intent.host, !intent.enabled)
                val updatedSet = if (!intent.enabled) {
                    _uiState.value.shieldDisabledHosts + intent.host
                } else {
                    _uiState.value.shieldDisabledHosts - intent.host
                }
                _uiState.update { it.copy(shieldDisabledHosts = updatedSet) }
            }
            is BrowserUiIntent.ExitFullScreenVideo -> {
                fullScreenHost?.onExitFullScreen()
                tabManager.setFullScreen(_uiState.value.activeTabId, false)
                _uiState.update { it.copy(isFullScreenVideo = false) }
            }
            is BrowserUiIntent.DismissSnackbar -> {
                _uiState.update { it.copy(snackbarMessage = null) }
            }
        }
    }

    fun onFullScreenChanged(isFullScreen: Boolean) {
        _uiState.update { it.copy(isFullScreenVideo = isFullScreen) }
    }

    private fun handleLoadUrl(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        // Use UrlNormalizer for HTTPS-first resolution with user's preferred search engine
        val normalized = UrlNormalizer.normalizeInput(
            rawInput = trimmed,
            allowLocalHttp = _uiState.value.isLocalHttpAllowed,
            searchPrefix = _uiState.value.searchEngine.queryUrl
        )

        tabManager.updateTabMetadata(_uiState.value.activeTabId, url = normalized)
        val webView = tabManager.detachActiveWebView()
        webView?.loadUrl(normalized)

        _uiState.update {
            it.copy(
                isOmniboxSheetVisible = false,
                omniboxQuery = "",
                searchSuggestions = emptyList(),
                isFetchingSuggestions = false,
                activeUrl = normalized,
                activeHost = extractHost(normalized),
                isLoading = true,
                progress = 0.1f
            )
        }
        omniboxQueryFlow.value = ""
    }

    /**
     * Explanation of temporary cleartext HTTP exception:
     * When cleartext traffic is disabled in Android's NetworkSecurityConfig (<base-config cleartextTrafficPermitted="false"/>),
     * Android's platform security layer rejects all plaintext HTTP connections for non-configured domains at the socket level.
     * For temporary single-navigation user allowances, we register the host into temporaryCleartextExceptions so the WebViewClient
     * will not block or re-upgrade the URL, and allow the connection to proceed.
     */
    private fun handleContinueWithHttp(originalUrl: String) {
        val host = extractHost(originalUrl)
        if (host.isNotBlank()) {
            temporaryCleartextExceptions.add(host)
        }
        val httpUrl = if (originalUrl.startsWith("https://")) {
            "http://" + originalUrl.substringAfter("https://")
        } else if (!originalUrl.startsWith("http://")) {
            "http://$originalUrl"
        } else {
            originalUrl
        }

        tabManager.updateTabMetadata(_uiState.value.activeTabId, url = httpUrl)
        tabManager.detachActiveWebView()?.loadUrl(httpUrl)

        _uiState.update {
            it.copy(
                activeUrl = httpUrl,
                activeHost = extractHost(httpUrl)
            )
        }
    }

    private fun performBurnSession() {
        temporaryCleartextExceptions.clear()
        tabManager.closeAllTabs()
        downloaderEngine.clearAllDownloads()
        WebViewSecurityConfig.performFullWipe(getApplication())
        _uiState.update {
            it.copy(
                isBurnConfirmVisible = false,
                isTabSwitcherVisible = false,
                isDownloadsSheetVisible = false,
                isShieldDialogVisible = false,
                activeUrl = "",
                activeTitle = "Shadhin Browser",
                activeHost = ""
            )
        }
    }

    fun configureWebView(webView: WebView, tabId: String) {
        WebViewSecurityConfig.applyHardenedSettings(webView)

        // Bind JavaScript Bridge for custom error page actions (Retry, Continue with HTTP, Go to start page)
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun retry() {
                webView.post { onIntent(BrowserUiIntent.RetryLoad) }
            }

            @JavascriptInterface
            fun continueWithHttp(url: String) {
                webView.post { onIntent(BrowserUiIntent.ContinueWithHttp(url)) }
            }

            @JavascriptInterface
            fun goToStartPage() {
                webView.post { onIntent(BrowserUiIntent.GoToStartPage) }
            }
        }, "__shadhin_bridge__")

        // Password Autofill & Login Capture Bridge
        val autofillBridge = PasswordAutofillBridge(
            onLoginFormDetected = { origin ->
                webView.post { handleLoginFormDetected(origin) }
            },
            onPasswordSubmitted = { origin, username, password ->
                webView.post { handlePasswordSubmitted(origin, username, password) }
            }
        )
        webView.addJavascriptInterface(autofillBridge, PasswordAutofillBridge.INTERFACE_NAME)

        // Bind scroll listener to drive capsule auto-hiding
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            onIntent(BrowserUiIntent.OnScrollDelta(scrollY, oldScrollY))
        }

        // Secure WebViewClient
        webView.webViewClient = SecureWebViewClient(
            context = getApplication(),
            tabId = tabId,
            privacyFilterEngine = privacyFilterEngine,
            cosmeticFilterEngine = cosmeticFilterEngine,
            isCosmeticFilteringEnabled = { _uiState.value.isCosmeticFilteringEnabled },
            isHttpsOnlyEnabled = { _uiState.value.isHttpsOnlyEnabled },
            isLocalHttpAllowed = { _uiState.value.isLocalHttpAllowed },
            isNetworkDiagnosticsEnabled = { _uiState.value.isNetworkDiagnosticsEnabled },
            getDohProviderName = { _uiState.value.activeDohProvider.displayName },
            activeHostProvider = { _uiState.value.activeHost },
            isCleartextAllowedForHost = { host -> temporaryCleartextExceptions.contains(host) },
            onCleartextFallbackRequested = { url -> handleContinueWithHttp(url) },
            onTrackerBlocked = { id -> tabManager.incrementTrackerCount(id) },
            onPageTitleOrUrlChanged = { id, title, url ->
                tabManager.updateTabMetadata(id, title, url)
                if (id == _uiState.value.activeTabId) {
                    _uiState.update {
                        it.copy(
                            activeUrl = url.orEmpty(),
                            activeTitle = title.takeIf { t -> !t.isNullOrBlank() } ?: it.activeTitle,
                            activeHost = extractHost(url),
                            canGoBack = webView.canGoBack(),
                            canGoForward = webView.canGoForward()
                        )
                    }
                }
            },
            onPageLoadFailed = { id, failingUrl ->
                // Preserve failing URL in capsule so user can see it and retry works
                tabManager.updateTabMetadata(id, title = "Failed to load", url = failingUrl)
                if (id == _uiState.value.activeTabId) {
                    _uiState.update {
                        it.copy(
                            activeUrl = failingUrl,
                            activeTitle = "Failed to load",
                            activeHost = extractHost(failingUrl)
                        )
                    }
                }
            },
            onExternalSchemeRequested = { intent ->
                _uiState.update {
                    it.copy(
                        activeExternalSchemePrompt = ExternalSchemePrompt(
                            url = intent.dataString.orEmpty(),
                            onConfirm = {
                                try {
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    getApplication<Application>().startActivity(intent)
                                } catch (_: Exception) {}
                                _uiState.update { s -> s.copy(activeExternalSchemePrompt = null) }
                            },
                            onCancel = {
                                _uiState.update { s -> s.copy(activeExternalSchemePrompt = null) }
                            }
                        )
                    )
                }
            },
            onSslErrorEncountered = { _, _ -> }
        )

        // Secure WebChromeClient
        webView.webChromeClient = SecureWebChromeClient(
            tabId = tabId,
            onProgressUpdate = { id, progress ->
                if (id == _uiState.value.activeTabId) {
                    val isLoading = progress < 1.0f
                    _uiState.update {
                        it.copy(
                            progress = progress,
                            isLoading = isLoading,
                            canGoBack = webView.canGoBack(),
                            canGoForward = webView.canGoForward()
                        )
                    }
                }
            },
            onTitleUpdate = { id, title ->
                tabManager.updateTabMetadata(id, title = title)
                if (id == _uiState.value.activeTabId) {
                    _uiState.update { it.copy(activeTitle = title) }
                }
            },
            onShowCustomVideoView = { view, callback ->
                android.util.Log.d("ShadhinMedia", "BrowserViewModel: onShowCustomVideoView for tab $tabId")
                tabManager.setFullScreen(tabId, true)
                _uiState.update { it.copy(isFullScreenVideo = true) }
                fullScreenHost?.onEnterFullScreen(view, callback)
            },
            onHideCustomVideoView = {
                android.util.Log.d("ShadhinMedia", "BrowserViewModel: onHideCustomVideoView for tab $tabId")
                tabManager.setFullScreen(tabId, false)
                _uiState.update { it.copy(isFullScreenVideo = false) }
                fullScreenHost?.onExitFullScreen()
            },
            onRequestUserPermission = { origin, resources, grant, deny ->
                _uiState.update {
                    it.copy(
                        activePermissionPrompt = PermissionPrompt(
                            origin = origin,
                            resources = resources.toList(),
                            onGrant = {
                                grant()
                                _uiState.update { s -> s.copy(activePermissionPrompt = null) }
                            },
                            onDeny = {
                                deny()
                                _uiState.update { s -> s.copy(activePermissionPrompt = null) }
                            }
                        )
                    )
                }
            },
            onPopupRequested = { targetUrl ->
                tabManager.openNewTab(targetUrl)
            }
        )

        // Download Listener
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            downloaderEngine.startDownload(url, userAgent, contentDisposition, mimetype)
            _uiState.update { it.copy(isDownloadsSheetVisible = true) }
        }
    }

    private fun handleLoginFormDetected(origin: String) {
        if (!_uiState.value.isAutofillEnabled) return
        val cleanHost = credentialRepository.normalizeOrigin(origin.ifEmpty { _uiState.value.activeHost })
        if (cleanHost.isBlank()) return

        viewModelScope.launch {
            val matching = credentialRepository.getCredentialsForOrigin(cleanHost)
            if (matching.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        activeAutofillPrompt = AutofillPrompt(
                            origin = cleanHost,
                            credentials = matching
                        )
                    )
                }
            }
        }
    }

    private fun handlePasswordSubmitted(origin: String, username: String, password: String) {
        if (!_uiState.value.isSavePasswordsEnabled) return
        val cleanHost = credentialRepository.normalizeOrigin(origin.ifEmpty { _uiState.value.activeHost })
        if (cleanHost.isBlank() || username.isBlank() || password.isBlank()) return

        _uiState.update {
            it.copy(
                activeSavePasswordPrompt = SavePasswordPrompt(
                    origin = cleanHost,
                    username = username.trim(),
                    password = password
                )
            )
        }
    }

    private fun handleAutofillCredential(credential: WebCredential) {
        val webView = tabManager.detachActiveWebView() ?: return
        val script = PasswordAutofillBridge.buildAutofillScript(credential.username, credential.decryptedPassword)
        webView.post {
            webView.evaluateJavascript(script, null)
        }
        viewModelScope.launch {
            credentialRepository.markCredentialUsed(credential.id)
        }
        _uiState.update { it.copy(activeAutofillPrompt = null) }
    }

    private fun extractHost(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            Uri.parse(url).host.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        localDohProxy.detachFromWebView()
        tabManager.destroy()
    }
}
