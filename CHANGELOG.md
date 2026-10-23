# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Added optional widgetVersion, previewToken, and agentVersion configuration, plus developmentMode URL selection.
- Isolated warm runtimes by version, preview token, and development mode.

### Added — server-controlled availability

- **`LoopsAIChat.fetchAvailability(agentId)`** — reports whether the agent is
  active for this platform. Reads the Android channel's own Active/Passive status
  and falls back to the web channel when the Android channel is unset, so hosts
  can show/hide their chat entry point and toggle Android independently of web and
  iOS from the dashboard, without an app update. Fails open on network errors.
- **`developmentMode` / `designMode`** config flags — load the runtime even when
  the channel is inactive (staging / QA / design preview).

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
- **Host-managed widget visibility** — the SDK now drives the web runtime's
  visible state itself (`hostManagedVisibility=true` on the load URL plus a new
  native→web `widgetVisibility` bridge action sent on present/dismiss), so
  `loops_ai_widget_opened` analytics fire on every open — including warm
  keep-alive reopens that previously emitted nothing.
- **Queued `openWithSearch`** — calls made before the web runtime is ready (or
  before the view exists) are no longer dropped: the last query is stored and
  flushed as `searchEscalation` when the engine becomes ready.

### Added

- **`closeButtonSlotWidthDp`** config (default `36` dp): width the chat header
  and panels keep free at the trailing edge for the native close button. Panels
  show a leading dismiss chevron instead of their own close button.
- **`headerLayout`** config (`LoopsHeaderLayout`): choose which header actions
  (`HISTORY`, `NEW_CHAT`, `FAVORITES`, `FITTING_ROOM`, `CART`) appear on the left
  and right. `null` keeps the default layout.

### Fixed

- **`openAsActivity` fits edge-to-edge**: the SDK activity pads the chat for
  system bars, display cutouts and the keyboard (edge-to-edge is enforced on
  Android 15). Embedded fragments keep the host's inset policy.
- **Custom http hosts and ports** (e.g. `http://10.0.2.2:3001`) now deliver
  bridge messages; origin rules no longer assume `https` on the default port.
- **`initConfig` / `initContext` are resent for every loaded document**, including
  reloads and `startNewConversation()`.

### Changed

- **Analytics channel is now `"android"`** (was `"mobile_app"`): canonical events
  are relabelled with the per-platform channel, following the platform split of
  the former single mobile channel. Events also carry a **`kind`** field
  (`"ecommerce"` | `"interaction"`) discriminating canonical ecommerce events
  from customer-interaction events; it defaults to `"ecommerce"` when absent.
- **Production-only environment**: the SDK now targets `chat.loopsai.com`
  everywhere. `LoopsEnvironment.Test` was removed; `Production` and `Custom(url)`
  remain.

### Fixed

- `loops_ai_widget_opened` was attributed `open_source: "bubble"` even when the
  chat was opened via `openWithSearch`; the search escalation now reaches the
  runtime before `visible=true` so the open is attributed `search_escalation`.
- `loops_ai_search_clicked` could be lost when `openWithSearch` was called
  before the web `ready` handshake (the call was silently dropped) — and calling
  it before `onViewCreated` crashed on the `lateinit` engine.

### Removed

- **Voice mode** is no longer exposed: the `openVoiceMode()` entry point and the
  `voiceModeEnabled` / `speechToTextEnabled` feature flags were removed.

## [1.0.0] - 2026-06-25

### Added — v2 contract parity with the iOS SDK

Mirrors the frozen iOS public API. Both platforms now emit identical canonical
analytics events and speak the same typed bridge.

- **Typed bridge envelope v1** (`LoopsAIBridgeProtocol`): native channel uses
  `{ protocolVersion, type:"nativeAction", name, requestId, payload }`. Full
  web→native + native→web action allowlists; unknown names ignored.
- **Native session ownership**: `LoopsSessionStore` (SharedPreferences) +
  `POST /api/widget/session` bootstrap with retry/backoff (3 attempts, transient
  /5xx only); `_lsuid` / `_lscid` re-injected on every load; `persistSession`.
- **Canonical analytics**: `LoopsAnalyticsEvent` (forces `channel:"mobile_app"` +
  `schema_version "1.0"` + `app_version`/`device`/`os_version`),
  `LoopsAnalyticsDispatcher` (always-on `LoopsSinkAdapter` + optional customer
  adapter), `HttpWebhookAdapter`, `BlockAnalyticsAdapter` (Firebase/Mixpanel/
  Segment without a core dependency), `LoopsAnalyticsConfig`.
- **Full host callbacks** on `LoopsAIChatListener`: `onReady`,
  `onMessageEvent`, `onResponding`, `onAnalyticsEvent`, `onProductQuoteChanged`,
  `onConversationActive`, `onError` (+ `onCloseRequested`/`onOpenUrlRequested`).
- **Flow-mode parity**: `openWithSearch`, `startTryOnFromQuote`,
  `openVoiceMode` (with allowlisted mic-capture grant), `startVirtualTryOn`,
  `suggestSize`, `sendMessage`, `updateContext`; `LoopsFeatureFlags` forwarded
  via `initConfig` (overrides only when set).
- **`LoopsEnvironment`** (`Production`/`Test`/`Custom`) replaces the raw
  `baseURL` string; origin allowlist includes the custom host.
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
