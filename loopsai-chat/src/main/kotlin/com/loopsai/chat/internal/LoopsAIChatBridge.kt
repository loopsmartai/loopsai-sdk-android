package com.loopsai.chat.internal

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.loopsai.chat.LoopsAIChatConfig
import com.loopsai.chat.LoopsAIChatContext
import com.loopsai.chat.LoopsProductQuote
import org.json.JSONObject

/**
 * Native side of the `mode=sdk` bridge (CONTRACT Part B, native channel).
 *
 * Inbound: decodes the typed envelope, enforces the name allowlist, and routes to
 * the [BridgeHost] (origin allowlist + main-frame are enforced one layer up by
 * [WebMessageListenerBridge] / the navigation gate). Outbound: wraps native→web
 * actions in the same typed envelope.
 *
 * Mirror of the iOS `LoopsAIChatBridge`.
 */
internal class LoopsAIChatBridge(
    private val config: LoopsAIChatConfig,
    private val host: BridgeHost
) {

    /**
     * The fragment implements this; the bridge stays transport-only. Mirrors the
     * iOS split where the bridge routes to `viewController` + `delegate`.
     */
    internal interface BridgeHost {
        fun onWebReady()
        fun onCloseRequested()
        fun onOpenUrlRequested(url: String)
        fun onMessageEvent(event: Map<String, Any?>)
        fun onTokenRefreshRequested(requestId: String?)
        fun onTrackEvent(payload: JSONObject)
        fun onResponding(isResponding: Boolean)
        fun onProductQuoteChanged(quote: LoopsProductQuote?)
        fun onConversationActive()
        fun onPersistSession(payload: JSONObject)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // MARK: - Inbound (web → native)

    /** Entry point for both transports (WebMessageListener + JS interface). */
    fun handleMessage(body: String) {
        val inbound = LoopsAIInboundMessage.parse(body) ?: return
        val action = LoopsAIBridgeProtocol.WebAction.from(inbound.name) ?: return // unknown → ignored
        mainHandler.post { dispatch(action, inbound) }
    }

    private fun dispatch(action: LoopsAIBridgeProtocol.WebAction, message: LoopsAIInboundMessage) {
        val payload = message.payload
        when (action) {
            LoopsAIBridgeProtocol.WebAction.READY ->
                host.onWebReady()

            LoopsAIBridgeProtocol.WebAction.CLOSE_CHAT ->
                host.onCloseRequested()

            LoopsAIBridgeProtocol.WebAction.OPEN_PRODUCT,
            LoopsAIBridgeProtocol.WebAction.OPEN_MODULE ->
                host.onMessageEvent(mapOf("action" to action.raw, "payload" to payload))

            LoopsAIBridgeProtocol.WebAction.OPEN_EXTERNAL_URL -> {
                val url = payload.optString("url")
                if (url.isNotEmpty()) host.onOpenUrlRequested(url)
            }

            LoopsAIBridgeProtocol.WebAction.REQUEST_TOKEN_REFRESH ->
                host.onTokenRefreshRequested(message.requestId)

            LoopsAIBridgeProtocol.WebAction.TRACK_EVENT ->
                host.onTrackEvent(payload)

            LoopsAIBridgeProtocol.WebAction.RESPONDING_STATE_CHANGE ->
                host.onResponding(payload.optBoolean("isResponding", false))

            LoopsAIBridgeProtocol.WebAction.PRODUCT_QUOTE_CHANGED ->
                host.onProductQuoteChanged(parseQuote(payload))

            LoopsAIBridgeProtocol.WebAction.CONVERSATION_ACTIVE ->
                host.onConversationActive()

            LoopsAIBridgeProtocol.WebAction.NEW_CHAT_STARTED ->
                host.onMessageEvent(mapOf("action" to action.raw))

            LoopsAIBridgeProtocol.WebAction.PERSIST_SESSION ->
                host.onPersistSession(payload)

            LoopsAIBridgeProtocol.WebAction.OVERLAY_OPEN,
            LoopsAIBridgeProtocol.WebAction.OVERLAY_CLOSED ->
                host.onMessageEvent(mapOf("action" to action.raw))
        }
    }

    private fun parseQuote(payload: JSONObject): LoopsProductQuote? {
        // A null/empty product clears the quote.
        val product = payload.optJSONObject("product") ?: return null
        val code = product.optString("code").ifEmpty { product.optString("productCode") }
        if (code.isEmpty()) return null
        return LoopsProductQuote(
            code = code,
            name = product.optString("name").ifEmpty { null },
            image = product.optString("image").ifEmpty { null },
            vtoEnabled = product.optBoolean("vtoEnabled", false)
        )
    }

    // MARK: - Outbound (native → web)

    fun send(
        webView: WebView,
        action: LoopsAIBridgeProtocol.NativeAction,
        payload: JSONObject = JSONObject(),
        requestId: String? = null
    ) {
        val envelope = JSONObject().apply {
            put("protocolVersion", LoopsAIBridgeProtocol.VERSION)
            put("type", LoopsAIBridgeProtocol.MESSAGE_TYPE)
            put("name", action.raw)
            put("payload", payload)
            requestId?.let { put("requestId", it) }
        }
        inject(webView, envelope)
    }

    fun sendInitConfig(webView: WebView) {
        // Merge any explicit flow-mode flag overrides; absent flags keep the
        // server-resolved agent config (CONTRACT B.3).
        val cfg = JSONObject().apply { put("alwaysShowCloseButton", false) }
        val flags = config.features.payload()
        val keys = flags.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            cfg.put(key, flags.get(key))
        }
        send(webView, LoopsAIBridgeProtocol.NativeAction.INIT_CONFIG, JSONObject().put("config", cfg))
    }

    fun sendInitContext(webView: WebView) {
        val payload = config.initialContext?.toPayload() ?: JSONObject()
        send(webView, LoopsAIBridgeProtocol.NativeAction.INIT_CONTEXT, payload)
    }

    fun sendUpdateContext(webView: WebView, context: LoopsAIChatContext) {
        send(webView, LoopsAIBridgeProtocol.NativeAction.UPDATE_CONTEXT, context.toPayload())
    }

    private fun inject(webView: WebView, envelope: JSONObject) {
        // Deliver to the web runtime's window message listener (mode=sdk).
        val js = "window.postMessage($envelope, '*');"
        mainHandler.post { webView.evaluateJavascript(js, null) }
    }
}
