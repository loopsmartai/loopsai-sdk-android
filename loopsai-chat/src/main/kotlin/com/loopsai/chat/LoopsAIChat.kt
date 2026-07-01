package com.loopsai.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.fragment.app.FragmentManager
import com.loopsai.chat.internal.LoopsAIChatRegistry
import com.loopsai.chat.internal.LoopsChatWebViewPool
import com.loopsai.chat.internal.LoopsHttp
import org.json.JSONObject

/**
 * Entry point for presenting the Loops AI chat experience.
 *
 * Mirror of the iOS `LoopsAIChat` enum:
 * - [openAsActivity] ≈ `present(from:config:style:delegate:)` (full-screen).
 * - [open] adds the chat fragment to a host container (sheet/embedded shells).
 * - [newFragment] hands back the fragment for manual transaction control.
 * For Jetpack Compose, use `LoopsAIChatView(config:)` instead.
 */
object LoopsAIChat {

    /** Present the chat full-screen in its own activity. */
    @JvmStatic
    @JvmOverloads
    fun openAsActivity(context: Context, config: LoopsAIChatConfig, listener: LoopsAIChatListener? = null) {
        val token = LoopsAIChatRegistry.register(config, listener)
        LoopsAIChatActivity.start(context, token)
    }

    /** Create the chat fragment for the host to add to its own transaction. */
    @JvmStatic
    @JvmOverloads
    fun newFragment(config: LoopsAIChatConfig, listener: LoopsAIChatListener? = null): LoopsAIChatFragment =
        LoopsAIChatFragment.newInstance(config, listener)

    /** Add the chat fragment to a container view (embedded / sheet shells). */
    @JvmStatic
    @JvmOverloads
    fun open(
        fragmentManager: FragmentManager,
        containerId: Int,
        config: LoopsAIChatConfig,
        listener: LoopsAIChatListener? = null,
        tag: String? = "loopsai_chat"
    ): LoopsAIChatFragment {
        val fragment = newFragment(config, listener)
        fragmentManager.beginTransaction()
            .replace(containerId, fragment, tag)
            .addToBackStack(tag)
            .commit()
        return fragment
    }

    /**
     * Query whether the agent's web channel is **active** (server-controlled), so
     * you can show or hide your chat entry point without shipping an app update.
     * This mirrors the web widget's `embedEnabled` gate — flip the channel on/off
     * from the dashboard and this reflects it (e.g. keep chat off at release, turn
     * it on later; or disable it for maintenance).
     *
     * Fails **open** (`true`) on a network/parse error, matching the web behavior —
     * a transient blip should not hide a working chat. Runs off the main thread;
     * [completion] is delivered on the main thread.
     */
    @JvmStatic
    @JvmOverloads
    fun fetchAvailability(
        agentId: String,
        environment: LoopsEnvironment = LoopsEnvironment.Production,
        completion: (Boolean) -> Unit
    ) {
        val url = "${environment.baseUrl.trimEnd('/')}/api/embed/$agentId/style"
        LoopsHttp.get(url) { response ->
            val available = if (response.error != null || response.body == null) {
                true
            } else {
                try {
                    JSONObject(response.body).optBoolean("embedEnabled", true)
                } catch (_: Exception) {
                    true
                }
            }
            Handler(Looper.getMainLooper()).post { completion(available) }
        }
    }

    /**
     * Drop every kept-alive chat runtime (the warm WebView pool). Call on logout /
     * account switch, or under memory pressure, so the next presentation cold-loads
     * fresh. The shared cookies / localStorage are unaffected — use [resetAllData]
     * for a full data wipe. Call on the main thread.
     */
    @JvmStatic
    fun clearWebCache() {
        LoopsChatWebViewPool.purgeAll()
    }

    /**
     * Erase **all** chat state in one shot — for a "reset" / logout / account
     * switch. This drops every warm runtime, clears the native anon session, and
     * removes the web runtime's persisted data (cookies, localStorage, IndexedDB,
     * HTTP cache). After this the next presentation cold-loads a brand-new
     * pseudonymous identity and conversation. Call on the main thread.
     *
     * @param context any context (the application context is used).
     * @param sessionStore the store to wipe (defaults to the SDK-owned store).
     * @param completion called once the web data has been cleared.
     */
    @JvmStatic
    @JvmOverloads
    fun resetAllData(
        context: Context,
        sessionStore: LoopsSessionStore = LoopsSessionStore.create(context),
        completion: (() -> Unit)? = null
    ) {
        LoopsChatWebViewPool.purgeAll()
        sessionStore.reset()

        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        WebView(context.applicationContext).apply {
            clearCache(true)
            clearHistory()
            destroy()
        }
        completion?.invoke()
    }
}
