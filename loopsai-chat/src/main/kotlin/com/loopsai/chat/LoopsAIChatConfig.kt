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
    val designMode: Boolean = false,
    val closeButtonSlotWidthDp: Int = 36,
    val headerLayout: LoopsHeaderLayout? = null
) {
    /**
     * Pool key for the warm WebView engine. One running runtime per agent +
     * environment + locale + header settings, so reopening the same chat reuses it.
     */
    internal val webCacheKey: String
        get() = "$agentId|$baseUrl|${normalizedLocale ?: ""}|$headerCacheKey"

    private val headerCacheKey: String
        get() {
            val slot = if (showCloseButton) closeButtonSlotWidthDp.coerceAtLeast(0).toString() else "none"
            val layout = headerLayout?.let { "${it.left.map { a -> a.id }}${it.right.map { a -> a.id }}" } ?: ""
            return "$slot|$layout"
        }

    /**
     * The base language tag the web runtime matches on. Hosts routinely pass
     * `Locale.getDefault().toLanguageTag()` (`tr-TR`), which the runtime would not
     * recognise — and an unnormalized value would also split the warm-engine
     * bucket between two spellings of the same language.
     */
    internal val normalizedLocale: String?
        get() {
            val raw = locale?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val base = raw.split('-', '_').first()
            return base.ifEmpty { null }?.lowercase()
        }

    internal val baseUrl: String get() = environment.baseUrl

    /** Host the bridge must allow in addition to the canonical Loops hosts. */
    internal val baseHost: String?
        get() = try {
            URI(baseUrl).host
        } catch (_: Exception) {
            null
        }

    internal val allowedOriginRules: Set<String>
        get() {
            val rules = linkedSetOf("https://chat.loopsai.com")
            try {
                val uri = URI(baseUrl)
                if (uri.scheme != null && uri.host != null) {
                    rules.add(if (uri.port == -1) "${uri.scheme}://${uri.host}" else "${uri.scheme}://${uri.host}:${uri.port}")
                }
            } catch (_: Exception) {
            }
            return rules
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
     * (`_lsuid` / `_lscid`) on every load.
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
            normalizedLocale?.let { add("locale" to it) }
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

enum class LoopsHeaderAction(internal val id: String) {
    HISTORY("history"),
    NEW_CHAT("newChat"),
    FAVORITES("favorites"),
    FITTING_ROOM("fittingRoom"),
    CART("cart")
}

data class LoopsHeaderLayout(
    val left: List<LoopsHeaderAction> = emptyList(),
    val right: List<LoopsHeaderAction> = emptyList()
)
