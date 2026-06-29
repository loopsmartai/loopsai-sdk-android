package com.loopsai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopsFeatureFlagsTest {

    @Test
    fun `default flags produce an empty payload (defer to server config)`() {
        assertEquals(0, LoopsFeatureFlags.DEFAULT.payload().length())
    }

    @Test
    fun `only explicitly-set flags are forwarded (CONTRACT B3 overrides-only)`() {
        val payload = LoopsFeatureFlags(searchEscalationEnabled = true, virtualTryOnEnabled = false).payload()
        assertEquals(2, payload.length())
        assertTrue(payload.getBoolean("searchEscalationEnabled"))
        assertFalse(payload.getBoolean("virtualTryOnEnabled"))
        assertFalse(payload.has("productSuggestionEnabled"))
    }
}
