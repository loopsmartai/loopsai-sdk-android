# LoopsAI Chat SDK for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-blue.svg)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-green.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-Proprietary-red.svg)](LICENSE)

The official Android SDK for [LoopsAI](https://loopsai.com) — embed AI-powered chat into any Android app with a few lines of code. Supports both **Views/Fragments** and **Jetpack Compose**.


---

## Installation

### Gradle (JitPack)

Add the JitPack repository to your project-level `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

Add the dependency to your app-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.loopsmartai.loopsai-sdk-android:loopsai-chat:1.0.0")
}
```

### Direct AAR

For private distributions, download the release AAR from GitHub Releases and add it to your `libs/` folder:

```kotlin
dependencies {
    implementation(files("libs/loopsai-chat-1.0.0.aar"))
}
```

---

## Quick Start

### Jetpack Compose

```kotlin
import com.loopsai.chat.LoopsAIChatConfig
import com.loopsai.chat.LoopsAIChatView

@Composable
fun MyScreen() {
    var isChatOpen by remember { mutableStateOf(false) }
    val config = LoopsAIChatConfig(agentId = "your_agent_id")

    Button(onClick = { isChatOpen = true }) {
        Text("Open Chat")
    }

    if (isChatOpen) {
        LoopsAIChatView(
            config = config,
            modifier = Modifier.fillMaxSize(),
            onClose = { isChatOpen = false }
        )
    }
}
```

### Activity (Simplest)

```kotlin
import com.loopsai.chat.LoopsAIChat
import com.loopsai.chat.LoopsAIChatConfig

class MainActivity : AppCompatActivity() {
    fun openChat() {
        val config = LoopsAIChatConfig(agentId = "your_agent_id")
        LoopsAIChat.openAsActivity(this, config)
    }
}
```

### Fragment

```kotlin
import com.loopsai.chat.LoopsAIChat
import com.loopsai.chat.LoopsAIChatConfig

class MainActivity : AppCompatActivity() {
    fun openChat() {
        val config = LoopsAIChatConfig(agentId = "your_agent_id")
        LoopsAIChat.open(
            fragmentManager = supportFragmentManager,
            containerId = R.id.container,
            config = config
        )
    }
}
```

---

## Configuration

`LoopsAIChatConfig` accepts the following parameters:

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `agentId` | `String` | Yes | — | Your LoopsAI agent identifier |
| `environment` | `LoopsEnvironment` | No | `Production` | `Production` (chat.loopsai.com) or `Custom(url)` (on-prem) |
| `initialContext` | `LoopsAIChatContext` | No | `null` | Product and user context |
| `features` | `LoopsFeatureFlags` | No | `DEFAULT` | Flow-mode overrides (VTO/search/…); absent flags defer to server config |
| `analytics` | `LoopsAnalyticsConfig` | No | `DEFAULT` | Always-on Loops sink + optional customer adapter |
| `locale` | `String` | No | `null` | Language code (e.g., `"en"`, `"tr"`) |
| `showCloseButton` | `Boolean` | No | `true` | Show native close button (top-right overlay) |
| `startFresh` | `Boolean` | No | `false` | Start a new conversation on load instead of resuming the last one |
| `keepAliveEnabled` | `Boolean` | No | `true` | Keep the chat runtime warm for instant reopen (shared WebView pool) |

> Mirrors the iOS SDK's public API (shared `CONTRACT` v1). Both platforms speak
> the same typed bridge and emit identical canonical analytics events
> (`channel: "mobile_app"`).

---

## Context

Pass product and user context to personalize the chat experience:

```kotlin
val config = LoopsAIChatConfig(
    agentId = "your_agent_id",
    initialContext = LoopsAIChatContext(
        productContext = mapOf(
            "productCode" to "SKU-12345",
            "productName" to "Classic T-Shirt"
        ),
        userContext = mapOf(
            "userId" to "u_42",
            "firstName" to "John",
            "email" to "john@example.com"
        )
    )
)
```

### Updating Context at Runtime

```kotlin
val fragment = LoopsAIChat.newFragment(config)

fragment.updateContext(LoopsAIChatContext(
    productContext = mapOf("productCode" to "SKU-67890")
))
```

---

## Integration Patterns

### 1. Activity — Simplest Integration

Open chat as a standalone Activity. No Fragment management needed:

```kotlin
LoopsAIChat.openAsActivity(context, config)
```

### 2. Fragment — Flexible Integration

Embed the chat Fragment into any container:

```kotlin
// Via convenience API
LoopsAIChat.open(
    fragmentManager = supportFragmentManager,
    containerId = R.id.container,
    config = config,
    listener = myListener
)

// Or create Fragment manually
val fragment = LoopsAIChat.newFragment(config, listener = myListener)
supportFragmentManager.beginTransaction()
    .replace(R.id.container, fragment)
    .addToBackStack(null)
    .commit()
```

### 3. Jetpack Compose

Use the composable wrapper for Compose-based apps:

```kotlin
LoopsAIChatView(
    config = config,
    modifier = Modifier.fillMaxSize(),
    onClose = { /* handle close */ },
    onOpenUrl = { url -> /* handle URL */ }
)
```

---

## Convenience Methods

```kotlin
val fragment = LoopsAIChat.newFragment(config)

fragment.sendMessage("I need help with sizing")
fragment.suggestSize()
fragment.startVirtualTryOn(mapOf("productCode" to "SKU-12345"))
fragment.startTryOnFromQuote()
fragment.quoteProduct(mapOf("productCode" to "SKU-12345"))   // card above the input
fragment.clearProductQuote()
fragment.openWithSearch("blue dress", productsOnly = false)
fragment.syncCustomerDetails("cust_42")  // personalize after login
fragment.setWebsiteFont("Inter, sans-serif")
fragment.startNewConversation()          // discard resume pointer, start fresh
fragment.closeOverlays()                 // close overlays on a back gesture
```

---

## Data & session management

```kotlin
// Drop the warm WebView pool (logout / memory pressure). Cookies & storage kept.
LoopsAIChat.clearWebCache()

// Full wipe: warm runtimes + native session + web cookies / localStorage / cache.
// The next presentation cold-loads a brand-new pseudonymous identity.
LoopsAIChat.resetAllData(context)
```

---

## Listener

`LoopsAIChatListener` notifies your app of chat lifecycle and native actions
(CONTRACT B.5 — mirrors the iOS `LoopsAIChatDelegate`):

| Method | Description |
|---|---|
| `onReady(fragment)` | Bridge is live and context applied |
| `onMessageEvent(fragment, event)` | A chat message / overlay frame crossed the bridge |
| `onResponding(fragment, isResponding)` | Bot started/stopped responding |
| `onAnalyticsEvent(fragment, event)` | Canonical analytics event (`channel: "mobile_app"`) |
| `onProductQuoteChanged(fragment, quote)` | Active product quote changed (`null` = cleared) |
| `onConversationActive(fragment)` | A conversation became active |
| `onError(fragment, error)` | Load / bridge / session error |
| `onCloseRequested(fragment)` | Chat requested to be closed |
| `onOpenUrlRequested(fragment, url)` | Chat requested to open an external URL |

All methods have default implementations. For Compose, use the matching `on…`
lambdas in `LoopsAIChatView`.

```kotlin
class MyActivity : AppCompatActivity(), LoopsAIChatListener {
    override fun onCloseRequested(fragment: LoopsAIChatFragment) {
        supportFragmentManager.popBackStack()
    }

    override fun onAnalyticsEvent(fragment: LoopsAIChatFragment, event: LoopsAnalyticsEvent) {
        // event.event, event.payload (canonical Part A shape)
    }
}

// Compose callbacks
LoopsAIChatView(
    config = config,
    onClose = { /* dismiss */ },
    onOpenUrl = { url -> /* open browser */ },
    onAnalyticsEvent = { event -> /* forward to your provider */ }
)
```

---

## Analytics

Analytics flow **through the SDK** on the shared canonical schema (CONTRACT
Part A). The web runtime's events cross the bridge, are relabelled with
`channel: "mobile_app"` + native context (`app_version`/`device`/`os_version`),
and fan out to an always-on Loops sink plus an optional customer adapter:

```kotlin
val config = LoopsAIChatConfig(
    agentId = "your_agent_id",
    analytics = LoopsAnalyticsConfig(
        loopsSinkEndpoint = "https://<your-ingest>",         // always-on Loops sink
        customerAdapter = BlockAnalyticsAdapter("firebase") { event ->
            // Wire any on-device provider — no core dependency on it:
            firebaseAnalytics.logEvent(event.event, event.payload.toBundle())
        }
    )
)
```

Built-in adapters: `LoopsSinkAdapter`, `HttpWebhookAdapter` (raw JSON POST),
`BlockAnalyticsAdapter` (Firebase/Mixpanel/Segment without a core dependency).

---

## Requirements

- Android 5.0+ (API 21)
- AndroidX

---

## Security

- The WebView only loads content from LoopsAI-owned domains
- Bridge uses `WebViewCompat.addWebMessageListener` with origin allowlisting (primary), falling back to `addJavascriptInterface` only when needed
- External URLs are opened in the default browser, never inside the SDK WebView
- The SDK natively owns the anonymous session (CONTRACT B.4): the pseudonymous
  anon id + last conversation id are persisted in app-private `SharedPreferences`
  and re-injected (`_lsuid` / `_lscid`) on every load, so the session survives
  WebView storage clears and app relaunch. No auth credentials/tokens are stored
- WebView debugging is enabled only in debug builds
- Media-capture permission requests are granted only to allowlisted origins

---

## License

Proprietary — see the [LICENSE](LICENSE) file for details.
