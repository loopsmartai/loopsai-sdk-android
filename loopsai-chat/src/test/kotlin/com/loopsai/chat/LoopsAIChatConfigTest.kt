package com.loopsai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class LoopsAIChatConfigTest {

    @Test
    fun `chatUrl contains agentId and required query params`() {
        val url = LoopsAIChatConfig(agentId = "test_agent").chatUrl(null, null)
        assertTrue(url.contains("test_agent"))
        assertTrue(url.contains("embedded=true"))
        assertTrue(url.contains("mode=sdk"))
        assertTrue(url.contains("platform=android"))
        assertTrue(url.contains("isMobile=true"))
        assertTrue(url.contains("hostManagedVisibility=true"))
    }

    @Test
    fun `chatUrl injects natively-owned session as _lsuid and _lscid`() {
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
    fun `chatUrl normalizes a regional locale to its base language`() {
        for (raw in listOf("tr_TR", "tr-TR", "TR", " tr ")) {
            val url = LoopsAIChatConfig(agentId = "agt", locale = raw).chatUrl(null, null)
            assertTrue("expected locale=tr for $raw", url.contains("locale=tr"))
            assertFalse(url.lowercase().contains("tr_tr"))
            assertFalse(url.lowercase().contains("tr-tr"))
        }
    }

    @Test
    fun `chatUrl omits a blank locale`() {
        assertFalse(
            LoopsAIChatConfig(agentId = "agt", locale = "   ").chatUrl(null, null).contains("locale")
        )
    }

    @Test
    fun `webCacheKey ignores locale spelling`() {
        assertEquals(
            LoopsAIChatConfig(agentId = "agt", locale = "tr-TR").webCacheKey,
            LoopsAIChatConfig(agentId = "agt", locale = "tr").webCacheKey
        )
    }

    @Test
    fun `webCacheKey differs on preview token`() {
        assertNotEquals(
            LoopsAIChatConfig(agentId = "agt").webCacheKey,
            LoopsAIChatConfig(agentId = "agt", previewToken = "tok").webCacheKey
        )
    }

    @Test
    fun `webCacheKey separates header settings`() {
        val base = LoopsAIChatConfig(agentId = "agt").webCacheKey
        val layout = LoopsHeaderLayout(
            left = listOf(LoopsHeaderAction.HISTORY),
            right = listOf(LoopsHeaderAction.FAVORITES)
        )
        assertNotEquals(base, LoopsAIChatConfig(agentId = "agt", showCloseButton = false).webCacheKey)
        assertNotEquals(base, LoopsAIChatConfig(agentId = "agt", closeButtonSlotWidthDp = 48).webCacheKey)
        assertNotEquals(base, LoopsAIChatConfig(agentId = "agt", headerLayout = layout).webCacheKey)
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
    fun `allowedHosts includes the production host and the custom host`() {
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
    fun `allowedOriginRules keep the scheme and port of a custom host`() {
        val config = LoopsAIChatConfig(
            agentId = "agt",
            environment = LoopsEnvironment.Custom("http://10.0.2.2:3001")
        )
        assertEquals(setOf("https://chat.loopsai.com", "http://10.0.2.2:3001"), config.allowedOriginRules)
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
    @Test
    fun `version preview url preserves identity and encodes token`() {
        val params = query(LoopsAIChatConfig(agentId = "agt", developmentMode = true, widgetVersion = " \nv7\t ", previewToken = " \ntoken+with/&=value\t ", agentVersion = " \nv3\t "))
        assertEquals("v7", params["wv"])
        assertEquals("token+with/&=value", params["pt"])
        assertEquals("v3", params["av"])
        assertEquals("1", params["dev"])
        assertEquals("anon_42", params["_lsuid"])
        assertEquals("cnv_7", params["_lscid"])
        assertEquals("android", params["platform"])
    }

    @Test
    fun `default release url leaves assignment to runtime`() {
        val params = query(LoopsAIChatConfig(agentId = "agt"))
        assertEquals("anon_42", params["_lsuid"])
        for (key in listOf("wv", "pt", "av", "dev", "rv")) assertFalse(params.containsKey(key))
    }

    @Test
    fun `blank versions do not send preview credential`() {
        val params = query(LoopsAIChatConfig(agentId = "agt", widgetVersion = " \n\t ", previewToken = "secret", agentVersion = " \n\t "))
        for (key in listOf("wv", "pt", "av")) assertFalse(params.containsKey(key))
    }

    @Test
    fun `test pointer does not require preview credential`() {
        val params = query(LoopsAIChatConfig(agentId = "agt", widgetVersion = "test"), fresh = true)
        assertEquals("test", params["wv"])
        assertEquals("anon_42", params["_lsuid"])
        assertEquals("true", params["fresh"])
        assertFalse(params.containsKey("pt"))
        assertFalse(params.containsKey("_lscid"))
    }

    @Test
    fun `runtime cache separates version selection`() {
        val base = LoopsAIChatConfig(agentId = "agt", widgetVersion = "v7", previewToken = "one", agentVersion = "v3")
        val variants = listOf(base.copy(widgetVersion = "v8"), base.copy(previewToken = "two"), base.copy(previewToken = null), base.copy(agentVersion = "v4"), base.copy(developmentMode = true), LoopsAIChatConfig(agentId = "agt"))
        for (config in variants) assertNotEquals(base.webCacheKey, config.webCacheKey)
        assertEquals(base.webCacheKey, base.copy(widgetVersion = " \nv7 ", previewToken = " one\n", agentVersion = "\tv3\n").webCacheKey)
    }

    private fun query(config: LoopsAIChatConfig, fresh: Boolean = false): Map<String, String> =
        URI(config.chatUrl("anon_42", "cnv_7", fresh)).rawQuery.split("&").associate { entry ->
            val pair = entry.split("=", limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }

}
