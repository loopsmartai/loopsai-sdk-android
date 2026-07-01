package com.loopsai.chat

import java.net.URI
import java.net.URLEncoder

/**
 * Agent id, environment, context, feature flags, and analytics for a chat
 * presentation. Mirror of the iOS `LoopsAIChatConfig`.
 *
 * Not `Parcelable`: it carries analytics adapters (closures) and is passed to the
 * fragment in-process (see `LoopsAIChat` / the internal config registry), exactly
 * as the iOS VC receives its config in-process. Identity/session survive process
 * death via [LoopsSessionStore], not via the config.
 */
data class LoopsAIChatConfig(
    val agentId: String,
    val environment: LoopsEnvironment = LoopsEnvironment.Production,
    val initialContext: LoopsAIChatContext? = null,
    val features: LoopsFeatureFlags = LoopsFeatureFlags.DEFAULT,
    val analytics: LoopsAnalyticsConfig = LoopsAnalyticsConfig.DEFAULT,
    val locale: String? = null,
    val showCloseButton: Boolean = true,
    val startFresh: Boolean = false,
    val keepAliveEnabled: Boolean = true,
    val developmentMode: Boolean = false,
    val designMode: Boolean = false
) {
    /**
     * Pool key for the warm WebView engine (CONTRACT B — performance). One running
     * runtime per agent + environment + locale, so reopening the same chat reuses it.
     */
    internal val webCacheKey: String get() = "$agentId|$baseUrl|${locale ?: ""}"

    internal val baseUrl: String get() = environment.baseUrl

    /** Host the bridge must allow in addition to the canonical Loops hosts. */
    internal val baseHost: String?
        get() = try {
            URI(baseUrl).host
        } catch (_: Exception) {
            null
        }

    /** Origin allowlist: the canonical Loops host + the configured custom host. */
    internal val allowedHosts: Set<String>
        get() {
            val hosts = linkedSetOf("chat.loopsai.com")
            baseHost?.let { hosts.add(it) }
            return hosts
        }

    /**
     * Builds the `mode=sdk` chat URL, re-injecting the natively-owned session
     * (`_lsuid` / `_lscid`) on every load (CONTRACT B.4).
     *
     * Built with pure-JVM string assembly (no `android.net.Uri`) so it is
     * unit-testable without Robolectric, mirroring iOS's pure `URLComponents`.
     */
    internal fun chatUrl(anonUserId: String?, conversationId: String?, fresh: Boolean = false): String {
        val base = baseUrl.trimEnd('/')
        val query = buildList {
            add("embedded" to "true")
            add("mode" to "sdk")
            add("platform" to "android")
            add("isMobile" to "true")
            anonUserId?.let { add("_lsuid" to it) }
            if (!fresh) conversationId?.let { add("_lscid" to it) }
            if (fresh) add("fresh" to "true")
            locale?.let { add("locale" to it) }
        }.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
        return "$base/${encodePathSegment(agentId)}?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun encodePathSegment(value: String): String =
        encode(value).replace("%2F", "/")
}
