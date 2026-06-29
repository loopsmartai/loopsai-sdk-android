package com.loopsai.chat

import com.loopsai.chat.internal.LoopsAIBridgeProtocol
import com.loopsai.chat.internal.LoopsAIInboundMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LoopsBridgeProtocolTest {

    @Test
    fun `parses a well-formed typed nativeAction envelope (CONTRACT B1)`() {
        val body = JSONObject()
            .put("protocolVersion", 1)
            .put("type", "nativeAction")
            .put("name", "openProduct")
            .put("requestId", "req_123")
            .put("payload", JSONObject().put("url", "https://x"))
            .toString()

        val inbound = LoopsAIInboundMessage.parse(body)
        assertNotNull(inbound)
        assertEquals(1, inbound!!.protocolVersion)
        assertEquals("openProduct", inbound.name)
        assertEquals("req_123", inbound.requestId)
        assertEquals("https://x", inbound.payload.optString("url"))
    }

    @Test
    fun `rejects the legacy flat embed shape (iframe channel is not the native channel)`() {
        assertNull(LoopsAIInboundMessage.parse(JSONObject().put("action", "closeChat").toString()))
    }

    @Test
    fun `rejects an envelope with no name`() {
        assertNull(LoopsAIInboundMessage.parse(JSONObject().put("type", "nativeAction").toString()))
    }

    @Test
    fun `rejects malformed json`() {
        assertNull(LoopsAIInboundMessage.parse("}{not json"))
    }

    @Test
    fun `defaults protocolVersion to current when omitted (forward tolerance)`() {
        val inbound = LoopsAIInboundMessage.parse(
            JSONObject().put("type", "nativeAction").put("name", "ready").toString()
        )
        assertEquals(LoopsAIBridgeProtocol.VERSION, inbound!!.protocolVersion)
    }

    @Test
    fun `web action allowlist resolves known names and ignores unknown (CONTRACT B2)`() {
        assertEquals(LoopsAIBridgeProtocol.WebAction.TRACK_EVENT, LoopsAIBridgeProtocol.WebAction.from("trackEvent"))
        assertEquals(LoopsAIBridgeProtocol.WebAction.PERSIST_SESSION, LoopsAIBridgeProtocol.WebAction.from("persistSession"))
        assertNull(LoopsAIBridgeProtocol.WebAction.from("notARealAction"))
    }

    @Test
    fun `native action raws match the frozen B3 names`() {
        assertEquals("searchEscalation", LoopsAIBridgeProtocol.NativeAction.SEARCH_ESCALATION.raw)
        assertEquals("startTryOnFromQuote", LoopsAIBridgeProtocol.NativeAction.START_TRY_ON_FROM_QUOTE.raw)
        assertEquals("quoteProduct", LoopsAIBridgeProtocol.NativeAction.QUOTE_PRODUCT.raw)
        assertEquals("clearProductQuote", LoopsAIBridgeProtocol.NativeAction.CLEAR_PRODUCT_QUOTE.raw)
        assertEquals("closeOverlays", LoopsAIBridgeProtocol.NativeAction.CLOSE_OVERLAYS.raw)
        assertEquals("mobileStateChange", LoopsAIBridgeProtocol.NativeAction.MOBILE_STATE_CHANGE.raw)
        assertEquals("syncCustomerDetails", LoopsAIBridgeProtocol.NativeAction.SYNC_CUSTOMER_DETAILS.raw)
        assertEquals("setWebsiteFont", LoopsAIBridgeProtocol.NativeAction.SET_WEBSITE_FONT.raw)
        assertEquals("setAnalyticsConsent", LoopsAIBridgeProtocol.NativeAction.SET_ANALYTICS_CONSENT.raw)
    }
}
