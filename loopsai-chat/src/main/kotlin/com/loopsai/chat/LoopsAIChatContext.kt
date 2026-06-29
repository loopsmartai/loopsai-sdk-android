package com.loopsai.chat

import org.json.JSONObject

/**
 * Context handed to the chat runtime: the product the user is viewing
 * ([productContext]) and what's known about the user ([userContext]). Pass it
 * at launch via [LoopsAIChatConfig] or update live with
 * [LoopsAIChatFragment.updateContext].
 *
 * Mirror of the iOS `LoopsAIChatContext`.
 */
data class LoopsAIChatContext(
    val productContext: Map<String, String>? = null,
    val userContext: Map<String, String>? = null
) {
    internal fun toPayload(): JSONObject {
        val json = JSONObject()
        productContext?.let { json.put("productContext", JSONObject(it)) }
        userContext?.let { json.put("userContext", JSONObject(it)) }
        return json
    }
}
