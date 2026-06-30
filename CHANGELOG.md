# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed — broader device support

- **Lowered `minSdk` from 24 (Android 7.0) to 21 (Android 5.0)** so older host
  apps can integrate. 21 is the floor (Jetpack Compose + the current AndroidX
  baseline); every WebView/session API the SDK uses is available at 21.
- The published artifact is just bytecode — host apps do **not** need Kotlin 2.0
  to consume it.

### Added — full iOS public-API parity

- **Product quoting**: `quoteProduct(product)` (card above the input) and
  `clearProductQuote()`.
- **Personalization & theming**: `syncCustomerDetails(customerId)` and
  `setWebsiteFont(fontFamily)`.
- **Conversation control**: `startNewConversation()` (discards the resume pointer
  and reloads with `fresh=true`); `closeOverlays()` for back-gesture handling.
- **Data management**: `LoopsAIChat.clearWebCache()`, `LoopsAIChat.resetAllData(context)`,
  and `LoopsSessionStore.reset()` for a full session/web-data wipe.
- **Config**: `startFresh` and `keepAliveEnabled`.
- **Warm runtime reuse**: a process-wide WebView engine pool (`keepAliveEnabled`,
  default on) keeps the chat runtime warm for instant reopen, mirroring iOS.
- **Layout sync**: `mobileStateChange` dispatched on configuration changes.

### Changed

- **Production-only environment**: the SDK now targets `chat.loopsai.com`
  everywhere. `LoopsEnvironment.Test` was removed; `Production` and `Custom(url)`
  remain.

### Removed

- **Voice mode** is no longer exposed: the `openVoiceMode()` entry point and the
  `voiceModeEnabled` / `speechToTextEnabled` feature flags were removed.

## [1.0.0] - 2026-06-25

### Added — v2 contract parity with the iOS SDK

Mirrors the frozen iOS public API (CONTRACT Part A + Part B). Both platforms now
emit identical canonical analytics events and speak the same typed bridge.

- **Typed bridge envelope v1** (`LoopsAIBridgeProtocol`): native channel uses
  `{ protocolVersion, type:"nativeAction", name, requestId, payload }`. Full B.2
  web→native + B.3 native→web action allowlists; unknown names ignored.
- **Native session ownership (B.4)**: `LoopsSessionStore` (SharedPreferences) +
  `POST /api/widget/session` bootstrap with retry/backoff (3 attempts, transient
  /5xx only); `_lsuid` / `_lscid` re-injected on every load; `persistSession`.
- **Part A analytics**: `LoopsAnalyticsEvent` (forces `channel:"mobile_app"` +
  `schema_version "1.0"` + `app_version`/`device`/`os_version`),
  `LoopsAnalyticsDispatcher` (always-on `LoopsSinkAdapter` + optional customer
  adapter), `HttpWebhookAdapter`, `BlockAnalyticsAdapter` (Firebase/Mixpanel/
  Segment without a core dependency), `LoopsAnalyticsConfig`.
- **Full host callbacks (B.5)** on `LoopsAIChatListener`: `onReady`,
  `onMessageEvent`, `onResponding`, `onAnalyticsEvent`, `onProductQuoteChanged`,
  `onConversationActive`, `onError` (+ `onCloseRequested`/`onOpenUrlRequested`).
- **Flow-mode parity (B.3)**: `openWithSearch`, `startTryOnFromQuote`,
  `openVoiceMode` (with allowlisted mic-capture grant), `startVirtualTryOn`,
  `suggestSize`, `sendMessage`, `updateContext`; `LoopsFeatureFlags` forwarded
  via `initConfig` (overrides only when set).
- **`LoopsEnvironment`** (`Production`/`Test`/`Custom`) replaces the raw
  `baseURL` string; origin allowlist includes the custom host (B.6).
- `LoopsProductQuote`, `LoopsError` types added.

### Changed

- `LoopsAIChatConfig` / `LoopsAIChatContext` are no longer `Parcelable`; config
  (which now carries analytics closures) is passed in-process, mirroring how the
  iOS VC receives its config + delegate. Chat URL is built with pure-JVM APIs
  (unit-testable without Robolectric).

## [0.1.0] - 2026-04-06

### Added

- `LoopsAIChatConfig` — configuration with agentId, context, locale, and baseURL (Parcelable).
- `LoopsAIChatContext` — typed context model for productContext and userContext (Parcelable).
- `LoopsAIChatListener` — interface for receiving close and openURL requests.
- `LoopsAIChatBridge` — secure dual-strategy bridge: WebMessageListener (primary) with JavascriptInterface fallback.
- `LoopsAIChatFragment` — core Fragment with WebView, loading/error states, and convenience methods.
- `LoopsAIChatActivity` — standalone Activity shell for simple "open on button tap" usage.
- `LoopsAIChatView` — Jetpack Compose wrapper via `AndroidFragment`.
- `LoopsAIChat` — static convenience API with `openAsActivity()`, `newFragment()`, and `open()`.
- Convenience methods: `sendMessage()`, `suggestSize()`, `startVirtualTryOn()`, `updateContext()`.
- Domain-restricted WebView navigation with origin-checked bridge security.
