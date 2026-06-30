# LoopsAI SDK — Android Showcase

A comprehensive sample app that exercises the **entire** public API of the LoopsAI
Chat SDK for Android. It's wired as the `:example` module of this repo and depends
on `:loopsai-chat` directly (a real app would instead pull the published artifact
from JitPack — see below).

Open the repo in Android Studio and run the **example** configuration on a device
or emulator (API 24+).

## Credentials

The agent and organization are set once in
[`LoopsConfig.kt`](app/src/main/java/com/loopsai/example/LoopsConfig.kt):

```kotlin
const val AGENT_ID = "j4PDx4UNSpV2tnmPoVW6"
const val ORGANIZATION_ID = "Bqox4RGliaFEQAyRXnru"
```

Only `AGENT_ID` is required by the SDK — the agent's organization, enabled flows,
theme, etc. are all resolved server-side from the agent id. `ORGANIZATION_ID` is
kept for reference / your own back end.

## What it demonstrates

**Home (`MainActivity`)** — builds a full `LoopsAIChatConfig` from live controls:
environment (Production / Custom URL), locale, `showCloseButton`, `startFresh`,
`keepAliveEnabled`, every `LoopsFeatureFlags` override, and product + user context.
From here you launch the chat three ways:

- **Embedded** — `LoopsAIChat.open(...)` into a `FragmentContainerView` (`ChatActivity`).
- **Activity** — `LoopsAIChat.openAsActivity(...)` (the simplest integration).
- **Compose** — the `LoopsAIChatView` composable (`ComposeChatActivity`).

It also calls the data/session APIs: `LoopsAIChat.clearWebCache()` and
`LoopsAIChat.resetAllData(context)`.

**Embedded chat (`ChatActivity`)** — drives every method on `LoopsAIChatFragment`:
`sendMessage`, `suggestSize`, `startVirtualTryOn`, `startTryOnFromQuote`,
`quoteProduct`, `clearProductQuote`, `openWithSearch`, `syncCustomerDetails`,
`setWebsiteFont`, `updateContext`, `startNewConversation`, `closeOverlays`,
`setAnalyticsConsent` — and logs **every** `LoopsAIChatListener` callback to an
on-screen console. Analytics are also forwarded through a `BlockAnalyticsAdapter`.

## Using the published SDK instead

A standalone app would depend on the SDK via JitPack rather than the local module:

```kotlin
// settings.gradle.kts
maven(url = "https://jitpack.io")

// app/build.gradle.kts
implementation("com.github.loopsmartai.loopsai-sdk-android:loopsai-chat:1.0.0")
```
