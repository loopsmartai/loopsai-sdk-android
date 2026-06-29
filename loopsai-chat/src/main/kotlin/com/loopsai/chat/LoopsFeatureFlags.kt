package com.loopsai.chat

import org.json.JSONObject

/**
 * Optional flow-mode feature-flag overrides forwarded to the web runtime via
 * `initConfig` (CONTRACT B.3). Each flag is optional: when `null` the SDK sends
 * nothing for it and the **server-resolved agent config** decides. Set a flag
 * only to deliberately override per launch.
 *
 * These also tell the host app which flows to surface as native entry points
 * (e.g. show a VTO button only when [virtualTryOnEnabled] != false).
 *
 * Mirror of the iOS `LoopsFeatureFlags`.
 */
data class LoopsFeatureFlags(
    val virtualTryOnEnabled: Boolean? = null,
    val searchEscalationEnabled: Boolean? = null,
    val productSuggestionEnabled: Boolean? = null,
    val outfitSuggestionEnabled: Boolean? = null
) {
    /**
     * Only the flags that were explicitly set, for merging into `initConfig`.
     * Absent flags are omitted so the web keeps its server defaults.
     */
    internal fun payload(): JSONObject {
        val json = JSONObject()
        virtualTryOnEnabled?.let { json.put("virtualTryOnEnabled", it) }
        searchEscalationEnabled?.let { json.put("searchEscalationEnabled", it) }
        productSuggestionEnabled?.let { json.put("productSuggestionEnabled", it) }
        outfitSuggestionEnabled?.let { json.put("outfitSuggestionEnabled", it) }
        return json
    }

    companion object {
        /** No overrides — defer entirely to the server-resolved agent config. */
        @JvmField
        val DEFAULT = LoopsFeatureFlags()
    }
}
