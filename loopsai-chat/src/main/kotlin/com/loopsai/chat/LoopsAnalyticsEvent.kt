package com.loopsai.chat

import android.content.Context
import android.os.Build
import org.json.JSONObject

/**
 * The canonical analytics event (`schema_version "1.0"`),
 * received from the web runtime over the `trackEvent` bridge action and
 * re-dispatched natively. On mobile the SDK **dictates the shape**:
 * [channel] is forced to `"android"` and native context
 * (`app_version`, `device`, `os_version`) is attached.
 *
 * Mirror of the iOS `LoopsAnalyticsEvent`.
 */
class LoopsAnalyticsEvent private constructor(
    /** Canonical event name (e.g. `loops_ai_view_item_list`). */
    val event: String,
    /** Always `"android"` on native. */
    val channel: String,
    /** Discriminates canonical ecommerce events from customer-interaction events; defaults to `"ecommerce"`. */
    val kind: String,
    /**
     * The full canonical event envelope with `channel` + native context
     * applied — ready for adapters and the host callback.
     */
    val payload: JSONObject
) {
    companion object {
        const val SCHEMA_VERSION = "1.0"
        const val CHANNEL = "android"

        /**
         * Builds a canonical event from a `trackEvent` bridge payload. The web
         * sends `{ event: <CanonicalEvent> }`; a bare event object is tolerated.
         * Returns `null` when no canonical `event` name is present.
         */
        internal fun from(bridgePayload: JSONObject, context: LoopsAnalyticsContext): LoopsAnalyticsEvent? {
            val raw = bridgePayload.optJSONObject("event") ?: bridgePayload
            val name = raw.optString("event")
            if (name.isEmpty()) return null

            val kind = raw.optString("kind")
                .ifEmpty { bridgePayload.optString("kind") }
                .ifEmpty { "ecommerce" }

            // Copy raw → merged so we never mutate the inbound payload.
            val merged = JSONObject(raw.toString())
            merged.put("schema_version", SCHEMA_VERSION)
            merged.put("channel", CHANNEL)
            merged.put("kind", kind)
            val fields = context.fields()
            val keys = fields.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!merged.has(key)) merged.put(key, fields.get(key))
            }
            return LoopsAnalyticsEvent(name, CHANNEL, kind, merged)
        }
    }
}

/**
 * Native-only base context attached to every canonical event.
 * `app_version`/`device`/`os_version` are device facts the web can't know; the
 * identity fields ride along when known.
 *
 * Mirror of the iOS `LoopsAnalyticsContext`. The bare constructor is pure-JVM
 * (unit-testable); [from] fills the device facts from a real [Context].
 */
data class LoopsAnalyticsContext(
    val appVersion: String = "unknown",
    val device: String = "unknown",
    val osVersion: String = "unknown",
    val locale: String? = null,
    val anonUserId: String? = null,
    val conversationId: String? = null
) {
    internal fun fields(): JSONObject {
        val json = JSONObject()
        json.put("app_version", appVersion)
        json.put("device", device)
        json.put("os_version", osVersion)
        locale?.let { json.put("locale", it) }
        anonUserId?.let { json.put("anon_user_id", it) }
        conversationId?.let { json.put("loops_conversation_id", it) }
        return json
    }

    companion object {
        internal fun from(
            context: Context,
            locale: String?,
            anonUserId: String?,
            conversationId: String?
        ): LoopsAnalyticsContext = LoopsAnalyticsContext(
            appVersion = hostAppVersion(context),
            device = deviceModel(),
            osVersion = Build.VERSION.RELEASE ?: "unknown",
            locale = locale,
            anonUserId = anonUserId,
            conversationId = conversationId
        )

        private fun hostAppVersion(context: Context): String = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        private fun deviceModel(): String {
            val manufacturer = Build.MANUFACTURER ?: ""
            val model = Build.MODEL ?: ""
            return listOf(manufacturer, model)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "unknown" }
        }
    }
}
