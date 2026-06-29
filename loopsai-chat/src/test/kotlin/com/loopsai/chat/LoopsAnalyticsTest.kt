package com.loopsai.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopsAnalyticsTest {

    private val context = LoopsAnalyticsContext(
        appVersion = "1.2.3",
        device = "Pixel 8",
        osVersion = "15",
        locale = "en",
        anonUserId = "anon1",
        conversationId = "cnv1"
    )

    @Test
    fun `forces channel mobile_app and schema_version on every event (CONTRACT Part A)`() {
        val payload = JSONObject().put("event", JSONObject().put("event", "loops_ai_view_item_list").put("channel", "web"))
        val event = LoopsAnalyticsEvent.from(payload, context)!!
        assertEquals("mobile_app", event.channel)
        assertEquals("mobile_app", event.payload.optString("channel"))
        assertEquals("1.0", event.payload.optString("schema_version"))
        assertEquals("loops_ai_view_item_list", event.event)
    }

    @Test
    fun `tolerates a bare event object (no event wrapper)`() {
        val payload = JSONObject().put("event", "loops_ai_select_item")
        val event = LoopsAnalyticsEvent.from(payload, context)!!
        assertEquals("loops_ai_select_item", event.event)
    }

    @Test
    fun `attaches native context fields without overwriting present ones`() {
        val payload = JSONObject().put("event", JSONObject().put("event", "loops_ai_view_item_list"))
        val event = LoopsAnalyticsEvent.from(payload, context)!!
        assertEquals("1.2.3", event.payload.optString("app_version"))
        assertEquals("Pixel 8", event.payload.optString("device"))
        assertEquals("15", event.payload.optString("os_version"))
        assertEquals("anon1", event.payload.optString("anon_user_id"))
        assertEquals("cnv1", event.payload.optString("loops_conversation_id"))
    }

    @Test
    fun `returns null when no canonical event name is present`() {
        assertNull(LoopsAnalyticsEvent.from(JSONObject().put("foo", "bar"), context))
    }

    @Test
    fun `dispatcher fans out to both sink and customer, and customer cannot suppress sink`() {
        val seen = mutableListOf<String>()
        val sink = BlockAnalyticsAdapter("sink") { seen.add("sink") }
        val customer = BlockAnalyticsAdapter("customer") { seen.add("customer") }
        val dispatcher = LoopsAnalyticsDispatcher(sink, customer)

        val event = LoopsAnalyticsEvent.from(JSONObject().put("event", "loops_ai_select_item"), context)!!
        dispatcher.dispatch(event)

        assertTrue(seen.contains("sink"))
        assertTrue(seen.contains("customer"))
    }

    @Test
    fun `config without a sink endpoint still delivers to the customer adapter`() {
        var delivered = false
        val config = LoopsAnalyticsConfig(
            loopsSinkEndpoint = null,
            customerAdapter = BlockAnalyticsAdapter("c") { delivered = true }
        )
        val event = LoopsAnalyticsEvent.from(JSONObject().put("event", "loops_ai_select_item"), context)!!
        config.makeDispatcher().dispatch(event)
        assertTrue(delivered)
    }
}
