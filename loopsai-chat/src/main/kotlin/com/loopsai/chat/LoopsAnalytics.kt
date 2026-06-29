package com.loopsai.chat

import com.loopsai.chat.internal.LoopsHttp

/**
 * A transport for canonical analytics events (CONTRACT Part A). Each adapter
 * relabels/delivers to one target; the dispatcher never knows the target.
 *
 * Mirror of the iOS `LoopsAnalyticsAdapter`. Implementations may run delivery off
 * the main thread, so they must be safe to share.
 */
interface LoopsAnalyticsAdapter {
    val id: String
    fun send(event: LoopsAnalyticsEvent)
}

/**
 * Fans a canonical event out to the always-on Loops sink plus an optional
 * per-agent customer adapter (CONTRACT Part A, mirrors the web dispatcher).
 * The sink is isolated: a customer adapter can never suppress it.
 *
 * Mirror of the iOS `LoopsAnalyticsDispatcher`.
 */
class LoopsAnalyticsDispatcher internal constructor(
    private val sink: LoopsAnalyticsAdapter?,
    private val customer: LoopsAnalyticsAdapter?
) {
    internal fun dispatch(event: LoopsAnalyticsEvent) {
        sink?.send(event)
        customer?.send(event)
    }
}

// MARK: - Built-in adapters

/**
 * Always-on sink → our backend ingest (Firestore via a `functions` endpoint,
 * CONTRACT Part A). Carries the canonical event unrelabelled.
 *
 * Mirror of the iOS `LoopsSinkAdapter`.
 */
class LoopsSinkAdapter(private val endpoint: String) : LoopsAnalyticsAdapter {
    override val id = "loops-sink"
    override fun send(event: LoopsAnalyticsEvent) {
        LoopsHttp.postFireAndForget(endpoint, event.payload.toString())
    }
}

/**
 * Generic POST of the canonical event to a customer webhook (Mode A, provider-
 * agnostic). For warehouses / CDPs that accept raw JSON.
 *
 * Mirror of the iOS `HttpWebhookAdapter`.
 */
class HttpWebhookAdapter(
    private val endpoint: String,
    private val headers: Map<String, String> = emptyMap()
) : LoopsAnalyticsAdapter {
    override val id = "http-webhook"
    override fun send(event: LoopsAnalyticsEvent) {
        LoopsHttp.postFireAndForget(endpoint, event.payload.toString(), headers)
    }
}

/**
 * Closure-backed adapter — the decoupling point for on-device provider SDKs
 * (Firebase `logEvent`, Mixpanel, Segment) **without the core SDK depending on
 * them**. The host wires its provider in the lambda:
 *
 * ```kotlin
 * BlockAnalyticsAdapter("firebase") { event ->
 *     firebaseAnalytics.logEvent(event.event, event.payload.toBundle())
 * }
 * ```
 *
 * Mirror of the iOS `BlockAnalyticsAdapter`.
 */
class BlockAnalyticsAdapter(
    override val id: String,
    private val handler: (LoopsAnalyticsEvent) -> Unit
) : LoopsAnalyticsAdapter {
    override fun send(event: LoopsAnalyticsEvent) = handler(event)
}

/**
 * Per-agent analytics configuration: which always-on sink and which customer
 * adapter to fan out to (CONTRACT A.3 selection happens server-side; the SDK is
 * told the resolved choice here — no per-customer native code).
 *
 * Mirror of the iOS `LoopsAnalyticsConfig`.
 */
data class LoopsAnalyticsConfig(
    /** Always-on Loops sink endpoint (`null` disables the sink — e.g. tests). */
    val loopsSinkEndpoint: String? = null,
    /** The customer destination, if any (webhook / provider via [BlockAnalyticsAdapter]). */
    val customerAdapter: LoopsAnalyticsAdapter? = null
) {
    internal fun makeDispatcher(): LoopsAnalyticsDispatcher {
        val sink = loopsSinkEndpoint?.let { LoopsSinkAdapter(it) }
        return LoopsAnalyticsDispatcher(sink = sink, customer = customerAdapter)
    }

    companion object {
        @JvmField
        val DEFAULT = LoopsAnalyticsConfig()
    }
}
