package com.loopsai.chat

import android.content.Context
import android.content.SharedPreferences
import com.loopsai.chat.internal.LoopsHttp
import org.json.JSONObject

/**
 * Native ownership of the anonymous widget session (CONTRACT B.4).
 *
 * Mobile WebViews can drop partitioned web storage, so the web runtime cannot
 * reliably keep the anon session in `mode=sdk`. The SDK persists it natively and
 * re-injects `_lsuid` / `_lscid` on every WebView load.
 *
 * Mirror of the iOS `LoopsSessionStore` (Keychain). Android uses app-private
 * [SharedPreferences] (the iOS `Security` analogue sanctioned for the
 * pseudonymous anon id — see the SDK README / Atlas Remaining for the optional
 * `EncryptedSharedPreferences` hardening note).
 */
class LoopsSessionStore internal constructor(private val prefs: SharedPreferences) {

    private val lock = Any()

    /** The durable anonymous user id, persisted across app relaunches. */
    var anonUserId: String?
        get() = synchronized(lock) { prefs.getString(KEY_ANON_USER_ID, null) }
        set(value) = synchronized(lock) {
            prefs.edit().apply {
                if (value.isNullOrEmpty()) remove(KEY_ANON_USER_ID) else putString(KEY_ANON_USER_ID, value)
            }.apply()
        }

    /** Last conversation id for a given agent (per agent). */
    fun conversationId(agentId: String): String? =
        synchronized(lock) { prefs.getString(conversationKey(agentId), null) }

    fun setConversationId(id: String?, agentId: String) = synchronized(lock) {
        prefs.edit().apply {
            if (id.isNullOrEmpty()) remove(conversationKey(agentId)) else putString(conversationKey(agentId), id)
        }.apply()
    }

    /**
     * Wipe the entire native session — the anon id and every per-agent conversation
     * pointer — for a "reset" / logout / account switch. Mirror of the iOS
     * `LoopsSessionStore.reset()`. Prefer [LoopsAIChat.resetAllData] for a full wipe
     * that also clears the web runtime's storage.
     */
    fun reset() = synchronized(lock) {
        prefs.edit().clear().apply()
    }

    /** Applies a `persistSession` payload from the web runtime (CONTRACT B.2). */
    internal fun applyPersistSession(payload: JSONObject) {
        payload.optString("anonUserId").takeIf { it.isNotEmpty() }?.let { anonUserId = it }
        val agentId = payload.optString("agentId")
        val cid = payload.optString("conversationId")
        if (agentId.isNotEmpty() && cid.isNotEmpty()) {
            setConversationId(cid, agentId)
        }
    }

    private fun conversationKey(agentId: String) = "conversationId_$agentId"

    companion object {
        private const val PREFS_NAME = "com.loopsai.chat.session"
        private const val KEY_ANON_USER_ID = "anonUserId"

        fun create(context: Context): LoopsSessionStore {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return LoopsSessionStore(prefs)
        }
    }
}

/**
 * Bootstraps the anon session against `POST /api/widget/session` (CONTRACT B.4),
 * migrating any locally-stored legacy id, then caches the result in the store.
 *
 * Mirror of the iOS `LoopsSessionBootstrapper`: 1 try + 2 retries, exponential
 * backoff, retrying transport errors and 5xx only (never a 4xx caller error).
 */
internal class LoopsSessionBootstrapper(
    private val baseUrl: String,
    private val store: LoopsSessionStore
) {
    data class Result(val anonUserId: String, val lastConversationId: String?)

    sealed class Outcome {
        data class Success(val result: Result) : Outcome()
        data class Failure(val error: LoopsError) : Outcome()
    }

    fun bootstrap(agentId: String, completion: (Outcome) -> Unit) {
        attempt(agentId, 1, completion)
    }

    private fun attempt(agentId: String, n: Int, completion: (Outcome) -> Unit) {
        val url = "${baseUrl.trimEnd('/')}/api/widget/session"
        val body = JSONObject().apply {
            put("agentId", agentId)
            store.anonUserId?.let { put("legacyAnonUserId", it) }
        }

        LoopsHttp.post(url, body.toString(), timeoutMs = 10_000) { response ->
            val transient = response.error != null || response.status in 500..599
            if (transient && n < MAX_ATTEMPTS) {
                // Exponential backoff: 0.5s, 1s, …
                val delayMs = (500.0 * Math.pow(2.0, (n - 1).toDouble())).toLong()
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                attempt(agentId, n + 1, completion)
                return@post
            }

            if (response.error != null) {
                completion(Outcome.Failure(LoopsError.Session(response.error.message ?: "network error")))
                return@post
            }

            val json = response.body?.let {
                try {
                    JSONObject(it)
                } catch (_: Exception) {
                    null
                }
            }
            val anonUserId = json?.optString("anonUserId")?.takeIf { it.isNotEmpty() }
            if (anonUserId == null) {
                completion(Outcome.Failure(LoopsError.Session("invalid /api/widget/session response (status ${response.status})")))
                return@post
            }

            val lastConversationId = json.optString("lastConversationId").takeIf { it.isNotEmpty() }
            store.anonUserId = anonUserId
            lastConversationId?.let { store.setConversationId(it, agentId) }
            completion(Outcome.Success(Result(anonUserId, lastConversationId)))
        }
    }

    companion object {
        /** Max bootstrap attempts (1 try + 2 retries). */
        const val MAX_ATTEMPTS = 3
    }
}
