# Titanium Capsule Browser (Arc-Inspired Privacy Android Browser)

A lightweight, ultra-fast, and privacy-hardened Android web browser module using Jetpack Compose (Material 3) and Android System WebView. It implements the "Floating Capsule (Arc-Inspired Bottom Island)" design in a **Titanium Industrial** aesthetic (monochrome frosted glass with precision accents), powered by multi-tab memory recycling, a private sandboxed file vault, RFC 8484 DNS-over-HTTPS (DoH) resolver with upstream switching, and a two-stage cosmetic ad-blocking and CSS injection engine.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The user confirmed the following core product and aesthetic decisions:
> - **Visual Theme**: *Titanium Industrial* — Monochrome frosted glass surface (`#16181B` with subtle metallic border `#2E3238`), cold precision slate/silver accents, high-contrast readable typography, and tactile pill controls.
> - **Default Search Engine**: *Google Search* (`https://www.google.com/search?q=`) for typed queries with automatic URL auto-detection.
> - **New Tab Experience**: *Curated Tech & Privacy Hubs* — Quick dials (e.g., GitHub, Hacker News, Wikipedia, EFF, DuckDuckGo, ArXiv) with active real-time shield counters (trackers blocked, cosmetic rules applied, DoH status).

---

### 1. Overview & Core Concept

- **What It Does**: Provides a desktop-class browsing experience on Android with high-performance tab recycling (capping live WebViews to 3 to conserve memory), robust privacy protections (zero unencrypted DNS leaks via DoH, tracker blocking, referrer stripping), Adblock Plus cosmetic element-hiding CSS injection, and private sandboxed downloading with zero system permissions required.
- **Target Audience**: Power users, privacy-conscious developers, and mobile users seeking an Arc-style bottom-anchored ergonomics without browser bloat.
- **Key Value**: Extreme speed, zero memory bloat during multi-tab sessions, uncompromised DNS privacy, and an ergonomic floating capsule UI anchored comfortably within thumb reach.

---

### 2. User Experience & Visual Design

```
┌────────────────────────────────────────────────────────┐
│ [═════════════════════] (2dp Slim Loading Bar at top) │
│                                                        │
│                                                        │
│                    WEBVIEW CANVAS                      │
│            (or Curated Privacy / Tech Dials)           │
│                                                        │
│                                                        │
│                                                        │
│                                                        │
│       ┌────────────────────────────────────────┐       │
│       │ [◀]  🔒 github.com  🛡️ 12  ⚡CF  │ [3]  🔥 │       │
│       └────────────────────────────────────────┘       │
│                 (Floating Titanium Capsule)            │
└────────────────────────────────────────────────────────┘
```

#### Key User Flows:
1. **Browsing & Auto-Hiding Capsule**:
   - The active tab renders edge-to-edge behind system insets.
   - On scrolling down, the floating capsule smoothly translates down off-screen; on scrolling up, it instantly returns.
   - Clicking the Omnibox opens an in-place editing sheet with quick search suggestions and URL completion.
2. **Tab Switcher & Memory Lifecycle**:
   - Tapping `[ 3 ]` reveals a sliding bottom sheet with tab cards displaying live titles, favicons/URLs, and a "Hibernated" indicator badge for tabs exceeding the active 3-WebView cap.
   - Closing a tab immediately recycles memory; opening a tab restores state from saved bundles seamlessly.
3. **Shield & Privacy Controls**:
   - Tapping the `🛡️` shield badge opens the Privacy Hub dialog:
     - Toggle Cosmetic Ad Filtering on/off.
     - Switch DoH Provider: Cloudflare (1.1.1.1), Quad9 (9.9.9.9), or System Direct.
     - Blocked Tracker & Ad counters.
4. **Sandboxed Downloads & Storage Access Framework**:
   - Intercepted downloads stream via OkHttp directly into internal app storage (`sandbox_downloads/`), bypassing public storage permissions.
   - Downloads Sheet allows instant preview or 1-tap export to public device storage via `ActivityResultContracts.CreateDocument`.
5. **Burn Session (One-Tap Wipe)**:
   - Tapping `🔥` prompts a clean wipe: clears WebView cookies, cache, web storage, destroys inactive tabs, deletes sandboxed downloads, and resets the session to a fresh start page.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Max 3 Live WebViews Memory Cap**:
  - *Chosen Approach*: Maintain a pool of up to 3 active `WebView` instances. When switching tabs, least-recently-used tabs serialize state via `webView.saveState(bundle)` and destroy the native view. On return, a fresh `WebView` restores state with `restoreState(bundle)`.
  - *Why*: WebViews are heavy native C++ objects that easily cause out-of-memory (OOM) crashes when opening 5+ tabs on mobile devices.
- **Decision 2: Two-Stage Cosmetic Ad-Blocking & CSS Injection**:
  - *Chosen Approach*: Evaluate minified CSS hiding rules in `onPageCommitVisible` (to prevent content flicker before first paint) and install a MutationObserver in `onPageFinished` (for dynamic SPAs).
  - *Why*: Eliminates Cumulative Layout Shift (CLS) without needing a full Chromium engine fork.
- **Decision 3: RFC 8484 DoH Bridge + WebKit Proxy**:
  - *Chosen Approach*: Implement `DohResolver` parsing standard DNS wireformat (A/AAAA records) over HTTPS with Cloudflare and Quad9 upstreams, combined with LRU caching and `WebViewClient.shouldInterceptRequest` domain verification.

---

### 4. Technical Architecture & Component Hierarchy

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           MainActivity & UI                             │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    BrowserScreen (Compose)                        │  │
│  │  - Edge-to-Edge Container & NestedScrollConnection                 │  │
│  │  - Top LinearProgressIndicator (2dp, animated)                    │  │
│  │  - WebViewHost (AndroidView with Tab Pooling)                     │  │
│  │  - StartPageView (Curated Quick Dials & Live Stats)               │  │
│  │  - FloatingCapsuleBar (Back, Omnibox, Shield, TabCount, Burn)     │  │
│  │  - TabSwitcherSheet (Cards, Close, Hibernation Badge, Add)        │  │
│  │  - DownloadsSheet (Progress, Status, Export via SAF, Delete)      │  │
│  │  - ShieldDialog (DoH Provider, Adblock Toggle, Block Counters)    │  │
│  └───────────────────────────────────┬───────────────────────────────┘  │
│                                      │ UDF (BrowserUiIntent / StateFlow) │
│                                      ▼                                  │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                          BrowserViewModel                         │  │
│  │  - TabManager (Cap 3 WebViews, saveState / restoreState)          │  │
│  │  - StateFlow<BrowserUiState> (Immutable UI State)                 │  │
│  │  - DownloaderEngine Coordinator                                   │  │
│  └───────────┬───────────────────────┬───────────────────────┬───────┘  │
└──────────────┼───────────────────────┼───────────────────────┼──────────┘
               ▼                       ▼                       ▼
   ┌───────────────────────┐ ┌───────────────────┐ ┌──────────────────────┐
   │ filter Package        │ │ network Package   │ │ storage Package      │
   │ - CosmeticFilterEngine│ │ - DohResolver     │ │ - DownloaderEngine   │
   │ - PrivacyFilterEngine │ │ - LruDnsCache     │ │ - BrowserDownload    │
   │ - CssInjectionPipeline│ │ - SecureWebClient │ │   Listener           │
   │ - TrackerBlocklist    │ │ - DohProvider     │ │ - SandboxFileVault   │
   └───────────────────────┘ └───────────────────┘ └──────────────────────┘
```

#### Package Structure:
- `com.example.filter`:
  - `CosmeticFilterEngine.kt`: Parser for Adblock Plus / uBlock Origin element-hiding syntax (`##.banner`, `domain##.ad`, `~domain##.rule`), domain-bound lookup map, and CSS minifier.
  - `PrivacyFilterEngine.kt`: Network-level tracker blocklist matching hostnames and subdomains.
- `com.example.network`:
  - `DohResolver.kt`: RFC 8484 DNS over HTTPS resolver with binary query/response parsing for A/AAAA records, supporting Cloudflare (`1.1.1.1`) and Quad9 (`9.9.9.9`).
  - `LruDnsCache.kt`: Thread-safe LRU cache respecting DNS TTL.
  - `DohProvider.kt`: Enum (`CLOUDFLARE`, `QUAD9`, `OFF`).
- `com.example.storage`:
  - `DownloadModels.kt`: `DownloadItem`, `DownloadStatus`.
  - `DownloaderEngine.kt`: Coroutine OkHttp download streamer into sandbox directory with progress tracking.
  - `SandboxFileVault.kt`: Management of `filesDir/sandbox_downloads`, file wipe on burn session, and content stream copying for SAF export.
- `com.example.engine`:
  - `BrowserTab.kt`: Immutable tab data class with `historyBundle`, `isAlive`, `blockedTrackerCount`.
  - `TabManager.kt`: Live WebView pooling (max 3), LRU hibernation, state restoration.
  - `SecureWebViewClient.kt` & `SecureWebChromeClient.kt`: Interception, cosmetic injection in `onPageCommitVisible`, progress tracking, error handling.
  - `WebViewSecurityConfig.kt`: Hardening settings (cookies, storage, file access, geolocation).
- `com.example.viewmodel`:
  - `BrowserUiState.kt` & `BrowserUiIntent.kt`.
  - `BrowserViewModel.kt`: Central coordinator.
- `com.example.ui`:
  - `BrowserScreen.kt`: Edge-to-edge layout, nested scroll capsule.
  - `FloatingCapsuleBar.kt`: Titanium industrial capsule dock.
  - `OmniboxDialog.kt`: Search & URL entry modal with suggestions.
  - `TabSwitcherSheet.kt`: Visual tab grid with hibernation badges.
  - `DownloadsSheet.kt`: Active & completed downloads panel.
  - `ShieldDialog.kt`: DoH and privacy settings modal.
  - `StartPageView.kt`: Curated dials and privacy dashboard.
  - `theme/Theme.kt` & `Color.kt`: Titanium industrial palette (`#0F1113`, `#1A1D20`, `#272B30`, `#E0E3E8`, `#64D2FF`).

---

### Verification Plan
1. **Compilation Check**: Run `compile_applet` to ensure all Kotlin Compose files, OkHttp networking, and Android WebKit components build without error.
2. **Robolectric & Unit Tests**:
   - `CosmeticFilterEngineTest`: Test rule parsing of global selectors, domain-specific selectors, domain negation (`~domain`), and generated CSS script injection.
   - `DohResolverTest`: Verify DNS packet encoding and mock response resolution.
   - `TabManagerTest`: Verify tab pooling, max 3 active WebViews cap, and bundle serialization on eviction.
3. **End-to-End Visual Verification**: Launch the streaming emulator to test navigation, bottom island scrolling auto-hide, quick dials, tab switching, and shield toggle.
