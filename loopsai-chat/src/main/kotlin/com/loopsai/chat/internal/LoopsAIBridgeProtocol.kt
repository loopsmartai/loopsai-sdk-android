package com.loopsai.chat.internal

import org.json.JSONObject

/**
 * The frozen `mode=sdk` bridge protocol (CONTRACT Part B, native channel).
 *
 * The native channel (`loopsAIBridge`) uses a **typed envelope**:
 * ```jsonc
 * { "protocolVersion": 1, "type": "nativeAction", "name": "openProduct",
 *   "requestId": "req_123", "payload": { /* … */ } }
 * ```
 * The iframe/embed channel keeps the legacy flat `{ action }` shape — that is a
 * separate transport the SDK never touches.
 *
 * Mirror of the iOS `LoopsAIBridgeProtocol`. Bump [VERSION] only via a
 * coordinated web + iOS + Android update (CONTRACT change policy).
 */
internal object LoopsAIBridgeProtocol {
    /** Bump only via a coordinated web + iOS + Android update. */
    const val VERSION = 1

    /** Envelope `type` discriminator. Only `nativeAction` is defined in v1. */
    const val MESSAGE_TYPE = "nativeAction"

    /**
     * Web → native action names the SDK handles (CONTRACT B.2 allowlist).
     * Anything not listed here is ignored.
     */
    enum class WebAction(val raw: String) {
        READY("ready"),
        CLOSE_CHAT("closeChat"),
        OPEN_PRODUCT("openProduct"),
        OPEN_MODULE("openModule"),
        OPEN_EXTERNAL_URL("openExternalUrl"),
        REQUEST_TOKEN_REFRESH("requestTokenRefresh"),
        TRACK_EVENT("trackEvent"),                  // analytics passthrough → Part A
        RESPONDING_STATE_CHANGE("respondingStateChange"),
        PRODUCT_QUOTE_CHANGED("productQuoteChanged"),
        CONVERSATION_ACTIVE("conversationActive"),
        NEW_CHAT_STARTED("newChatStarted"),
        PERSIST_SESSION("persistSession"),
        OVERLAY_OPEN("overlayOpen"),
        OVERLAY_CLOSED("overlayClosed");

        companion object {
            private val byRaw = entries.associateBy { it.raw }
            fun from(raw: String): WebAction? = byRaw[raw]
        }
    }

    /** Native → web action names the SDK sends (CONTRACT B.3). */
    enum class NativeAction(val raw: String) {
        INIT_CONFIG("initConfig"),
        INIT_CONTEXT("initContext"),
        UPDATE_CONTEXT("updateContext"),
        SEND_USER_MESSAGE("sendUserMessage"),
        SEARCH_ESCALATION("searchEscalation"),
        START_VIRTUAL_TRY_ON("startVirtualTryOn"),
        START_TRY_ON_FROM_QUOTE("startTryOnFromQuote"),
        QUOTE_PRODUCT("quoteProduct"),               // pin a product as the active quote (card above the input)
        SUGGEST_SIZE("suggestSize"),
        SYNC_CUSTOMER_DETAILS("syncCustomerDetails"),
        SET_WEBSITE_FONT("setWebsiteFont"),
        START_VOICE_MODE("startVoiceMode"),          // reserved — server-gated, no public entry point
        SET_ANALYTICS_CONSENT("setAnalyticsConsent"), // host CMP consent → web A.4 consent gate
        CLEAR_PRODUCT_QUOTE("clearProductQuote"),    // clear the active quote chip
        CLOSE_OVERLAYS("closeOverlays"),             // dispatch loopsai:close-overlays in the runtime
        MOBILE_STATE_CHANGE("mobileStateChange");    // rotation / size change → re-evaluate layout
    }
}

/**
 * A decoded inbound envelope from the web runtime (native channel).
 *
 * Mirrors the iOS `LoopsAIInboundMessage`. [parse] returns `null` for anything
 * that is not a well-formed `nativeAction` envelope (legacy flat messages, other
 * frames). The web runtime emits the typed envelope only in `mode=sdk`.
 */
internal class LoopsAIInboundMessage private constructor(
    val protocolVersion: Int,
    val type: String,
    val name: String,
    val requestId: String?,
    val payload: JSONObject
) {
    companion object {
        fun parse(body: String): LoopsAIInboundMessage? {
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                return null
            }
            return parse(json)
        }

        fun parse(json: JSONObject): LoopsAIInboundMessage? {
            val type = json.optString("type")
            if (type != LoopsAIBridgeProtocol.MESSAGE_TYPE) return null
            val name = json.optString("name")
            if (name.isEmpty()) return null

            // protocolVersion is required on the typed envelope; default to current
            // for forward-tolerance if a producer omits it.
            val version = if (json.has("protocolVersion")) {
                json.optInt("protocolVersion", LoopsAIBridgeProtocol.VERSION)
            } else {
                LoopsAIBridgeProtocol.VERSION
            }
            val requestId = json.optString("requestId").ifEmpty { null }
            val payload = json.optJSONObject("payload") ?: JSONObject()

            return LoopsAIInboundMessage(version, type, name, requestId, payload)
        }
    }
}
