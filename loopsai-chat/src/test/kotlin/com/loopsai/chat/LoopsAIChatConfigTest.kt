package com.loopsai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopsAIChatConfigTest {

    @Test
    fun `chatUrl contains agentId and required query params`() {
        val url = LoopsAIChatConfig(agentId = "test_agent").chatUrl(null, null)
        assertTrue(url.contains("test_agent"))
        assertTrue(url.contains("embedded=true"))
        assertTrue(url.contains("mode=sdk"))
        assertTrue(url.contains("platform=android"))
        assertTrue(url.contains("isMobile=true"))
    }

    @Test
    fun `chatUrl injects natively-owned session as _lsuid and _lscid (CONTRACT B4)`() {
        val url = LoopsAIChatConfig(agentId = "agt").chatUrl(anonUserId = "anon123", conversationId = "cnv789")
        assertTrue(url.contains("_lsuid=anon123"))
        assertTrue(url.contains("_lscid=cnv789"))
    }

    @Test
    fun `chatUrl omits session params when absent`() {
        val url = LoopsAIChatConfig(agentId = "agt").chatUrl(null, null)
        assertFalse(url.contains("_lsuid"))
        assertFalse(url.contains("_lscid"))
    }

    @Test
    fun `chatUrl includes locale when set and omits when null`() {
        assertTrue(LoopsAIChatConfig(agentId = "agt", locale = "tr").chatUrl(null, null).contains("locale=tr"))
        assertFalse(LoopsAIChatConfig(agentId = "agt", locale = null).chatUrl(null, null).contains("locale"))
    }

    @Test
    fun `default environment is production`() {
        assertEquals("https://chat.loopsai.com", LoopsAIChatConfig(agentId = "agt").baseUrl)
    }

    @Test
    fun `fresh load omits _lscid and adds fresh flag`() {
        val url = LoopsAIChatConfig(agentId = "agt")
            .chatUrl(anonUserId = "anon123", conversationId = "cnv789", fresh = true)
        assertTrue(url.contains("_lsuid=anon123"))
        assertFalse(url.contains("_lscid"))
        assertTrue(url.contains("fresh=true"))
    }

    @Test
    fun `allowedHosts includes the production host and the custom host (CONTRACT B6)`() {
        val config = LoopsAIChatConfig(
            agentId = "agt",
            environment = LoopsEnvironment.Custom("https://custom.example.com")
        )
        val hosts = config.allowedHosts
        assertTrue(hosts.contains("chat.loopsai.com"))
        assertFalse(hosts.contains("test-webchat.loopsai.com"))
        assertTrue(hosts.contains("custom.example.com"))
    }

    @Test
    fun `showCloseButton defaults to true`() {
        assertTrue(LoopsAIChatConfig(agentId = "agt").showCloseButton)
    }

    @Test
    fun `trailing slash in custom baseUrl is trimmed`() {
        val config = LoopsAIChatConfig(
            agentId = "agt",
            environment = LoopsEnvironment.Custom("https://chat.loopsai.com/")
        )
        assertFalse(config.chatUrl(null, null).contains("com//agt"))
    }
}
