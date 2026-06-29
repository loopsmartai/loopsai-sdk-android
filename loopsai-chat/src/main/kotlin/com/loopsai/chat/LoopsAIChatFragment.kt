package com.loopsai.chat

import android.content.Intent
import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import com.loopsai.chat.internal.LoopsAIBridgeProtocol
import com.loopsai.chat.internal.LoopsAIChatRegistry
import com.loopsai.chat.internal.LoopsChatEngine
import com.loopsai.chat.internal.LoopsChatWebViewPool
import org.json.JSONObject

/**
 * The chat container fragment — the Android analogue of the iOS
 * `LoopsAIChatViewController`. It borrows a warm [LoopsChatEngine] (which owns the
 * WebView, bridge, session and analytics), exposes the same public methods, and
 * routes the same host callbacks (CONTRACT Part B).
 */
open class LoopsAIChatFragment : Fragment() {

    companion object {
        internal fun newInstance(config: LoopsAIChatConfig, listener: LoopsAIChatListener?): LoopsAIChatFragment {
            val token = LoopsAIChatRegistry.register(config, listener)
            return forToken(token)
        }

        /** Build a fragment bound to an already-registered token (Activity host). */
        internal fun forToken(token: String): LoopsAIChatFragment = LoopsAIChatFragment().apply {
            arguments = Bundle().apply { putString(LoopsAIChatRegistry.ARG_TOKEN, token) }
        }
    }

    var listener: LoopsAIChatListener? = null
        set(value) {
            field = value
            LoopsAIChatRegistry.setListener(token, value)
        }

    /** Natively-owned anonymous session (CONTRACT B.4). */
    lateinit var sessionStore: LoopsSessionStore
        private set

    private var token: String? = null
    private lateinit var config: LoopsAIChatConfig
    private lateinit var engine: LoopsChatEngine
    private var reusedWarmEngine = false
    private var progressBar: ProgressBar? = null
    private var errorView: LinearLayout? = null
    private var closeButton: ImageButton? = null

    /** One-shot: when set, the next load starts a fresh conversation. */
    private var startFreshOnNextLoad = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = requireArguments().getString(LoopsAIChatRegistry.ARG_TOKEN)
        config = LoopsAIChatRegistry.config(token)
            ?: throw IllegalStateException(
                "LoopsAIChatConfig not found — present the chat via LoopsAIChat (the config did not survive process death)."
            )
        listener = listener ?: LoopsAIChatRegistry.listener(token)
        startFreshOnNextLoad = config.startFresh
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_loopsai_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progressBar)
        errorView = view.findViewById(R.id.errorView)
        closeButton = view.findViewById(R.id.closeButton)

        acquireEngine()
        sessionStore = engine.sessionStore
        engine.presenter = this
        attachWebView(view as ViewGroup)

        setupCloseButton()
        setupRetryButton(view)

        if (reusedWarmEngine && !config.startFresh) {
            // Warm path: the runtime is already loaded + ready — reattach with no
            // network, no reload, no JS re-execution. Instant reopen.
            resumeWarmEngine()
        } else {
            if (reusedWarmEngine) engine.isWebReady = false
            bootstrapSessionThenLoad()
        }
    }

    private fun acquireEngine() {
        val key = config.webCacheKey
        val warm = if (config.keepAliveEnabled) LoopsChatWebViewPool.borrow(key) else null
        if (warm != null) {
            engine = warm
            reusedWarmEngine = true
        } else {
            val webView = LoopsChatEngine.makeWebView(requireContext().applicationContext)
            engine = LoopsChatEngine(webView, config, LoopsSessionStore.create(requireContext()))
            engine.setup()
            reusedWarmEngine = false
            if (config.keepAliveEnabled) LoopsChatWebViewPool.store(key, engine)
        }
    }

    private fun attachWebView(container: ViewGroup) {
        val wv = engine.webView
        (wv.context as? MutableContextWrapper)?.baseContext = requireActivity()
        (wv.parent as? ViewGroup)?.removeView(wv)
        container.addView(
            wv,
            0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun setupCloseButton() {
        closeButton?.visibility = View.GONE
        closeButton?.setOnClickListener { listener?.onCloseRequested(this) }
    }

    private fun setupRetryButton(view: View) {
        view.findViewById<View>(R.id.retryButton)?.setOnClickListener { loadChat() }
    }

    // MARK: - Session + load

    /**
     * Resolves the anon session natively (CONTRACT B.4), then loads the runtime
     * with `_lsuid` / `_lscid` injected. Falls back to the cached session (or none)
     * if bootstrap fails — the web runtime can still self-bootstrap.
     */
    private fun bootstrapSessionThenLoad() {
        val bootstrapper = LoopsSessionBootstrapper(config.baseUrl, sessionStore)
        bootstrapper.bootstrap(config.agentId) { outcome ->
            view?.post {
                if (!isAdded) return@post
                if (outcome is LoopsSessionBootstrapper.Outcome.Failure) {
                    listener?.onError(this, outcome.error)
                }
                loadChat()
            }
        }
    }

    private fun loadChat() {
        errorView?.visibility = View.GONE
        progressBar?.visibility = View.VISIBLE
        engine.webView.alpha = 0f
        closeButton?.alpha = 0f
        // `startFreshOnNextLoad` is one-shot: consume it so a later reload (e.g.
        // retry) resumes normally once the new conversation has been persisted.
        val fresh = startFreshOnNextLoad
        startFreshOnNextLoad = false
        val url = config.chatUrl(
            anonUserId = sessionStore.anonUserId,
            conversationId = if (fresh) null else sessionStore.conversationId(config.agentId),
            fresh = fresh
        )
        engine.load(url)
    }

    private fun resumeWarmEngine() {
        // borrow() only hands out ready engines, so fire the ready path directly.
        onEngineReady()
    }

    private fun revealContent() {
        progressBar?.visibility = View.GONE
        engine.webView.animate().alpha(1f).setDuration(250).start()
        if (config.showCloseButton) {
            closeButton?.visibility = View.VISIBLE
            closeButton?.animate()?.alpha(1f)?.setDuration(250)?.start()
        }
    }

    // MARK: - Engine callbacks (web → native, UI-facing)

    internal fun onEngineReady() {
        revealContent()
        listener?.onReady(this)
    }

    internal fun onEnginePageVisible() {
        revealContent()
    }

    internal fun onEngineLoadError(description: String) {
        progressBar?.visibility = View.GONE
        errorView?.visibility = View.VISIBLE
        listener?.onError(this, LoopsError.Load(description))
    }

    // MARK: - Public API (native → web)

    fun updateContext(context: LoopsAIChatContext) {
        if (!engine.isWebReady) return
        engine.bridge.sendUpdateContext(engine.webView, context)
    }

    fun sendMessage(message: String) {
        sendIfReady(LoopsAIBridgeProtocol.NativeAction.SEND_USER_MESSAGE, JSONObject().put("message", message))
    }

    fun startVirtualTryOn(product: Map<String, String>) {
        sendIfReady(LoopsAIBridgeProtocol.NativeAction.START_VIRTUAL_TRY_ON, JSONObject().put("product", JSONObject(product)))
    }

    /** Try on the product currently quoted in the conversation (CONTRACT B.3). */
    fun startTryOnFromQuote() {
        sendIfReady(LoopsAIBridgeProtocol.NativeAction.START_TRY_ON_FROM_QUOTE)
    }

    /**
     * Quote a specific product in the conversation (CONTRACT B.3 `quoteProduct`):
     * the runtime shows that product as a card **above the input** so the user can
     * ask their own question about it. No message is sent. Pairs with
     * [clearProductQuote] and [startTryOnFromQuote].
     * @param product product context; must include `productCode` (or `code`).
     */
    fun quoteProduct(product: Map<String, String>) {
        sendIfReady(LoopsAIBridgeProtocol.NativeAction.QUOTE_PRODUCT, JSONObject().put("product", JSONObject(product)))
    }

    /**
     * Clear the active product quote (CONTRACT B.3 `clearProductQuote`). Pairs with
     * [LoopsAIChatListener.onProductQuoteChanged]: when your app dismisses its native
     * quote chip, call this so the web runtime drops the quote too.
     */
    fun clearProductQuote() {
        sendIfReady(LoopsAIBridgeProtocol.NativeAction.CLEAR_PRODUCT_QUOTE)
    }

    fun suggestSize() {
        sendIfReady(LoopsAIBridgeProtocol.NativeAction.SUGGEST_SIZE)
    }

    /**
     * Open the chat in AI search / escalation mode with a prefilled query.
     * @param productsOnly restrict to product search (no chat escalation).
     */
    fun openWithSearch(query: String, productsOnly: Boolean = false) {
        sendIfReady(
            LoopsAIBridgeProtocol.NativeAction.SEARCH_ESCALATION,
            JSONObject().put("query", query).put("isOnlySearchProducts", productsOnly)
        )
    }

    /**
     * Sync a known customer into the conversation (CONTRACT B.3
     * `syncCustomerDetails`). Use when your app already knows who the user is (e.g.
     * after login) so chat / size / recommendations are personalized.
     */
    fun syncCustomerDetails(customerId: String) {
        sendIfReady(
            LoopsAIBridgeProtocol.NativeAction.SYNC_CUSTOMER_DETAILS,
            JSONObject().put("customerId", customerId)
        )
    }

    /**
     * Match the chat typography to your app (CONTRACT B.3 `setWebsiteFont`).
     * @param fontFamily a CSS `font-family` value the web runtime applies.
     */
    fun setWebsiteFont(fontFamily: String) {
        sendIfReady(
            LoopsAIBridgeProtocol.NativeAction.SET_WEBSITE_FONT,
            JSONObject().put("fontFamily", fontFamily)
        )
    }

    /**
     * Close any open web overlay — size input, sidebars, drawers — without unloading
     * the WebView (CONTRACT B.3 `closeOverlays`). Wire this to a back gesture so a
     * system back closes the overlay first instead of tearing down the chat.
     */
    fun closeOverlays() {
        sendIfReady(LoopsAIBridgeProtocol.NativeAction.CLOSE_OVERLAYS)
    }

    /**
     * Start a brand-new conversation, discarding the resume pointer. Reloads the
     * runtime with `fresh=true` so no prior conversation is restored. The fresh
     * conversation is persisted back natively once the web runtime reports it
     * (CONTRACT B.2 `persistSession`), so a later relaunch resumes *it*.
     */
    fun startNewConversation() {
        sessionStore.setConversationId(null, config.agentId)
        startFreshOnNextLoad = true
        engine.isWebReady = false
        loadChat()
    }

    /**
     * Set the analytics consent state from your app's consent management platform
     * (CONTRACT B.3 `setAnalyticsConsent`). `true` lets analytics dispatch, `false`
     * denies it (stops every adapter, including the always-on Loops sink). Call this
     * whenever the user updates their consent; it is forward-compatible.
     * @param granted `true` to grant analytics consent, `false` to deny it.
     */
    fun setAnalyticsConsent(granted: Boolean) {
        sendIfReady(
            LoopsAIBridgeProtocol.NativeAction.SET_ANALYTICS_CONSENT,
            JSONObject().put("granted", granted)
        )
    }

    private fun sendIfReady(action: LoopsAIBridgeProtocol.NativeAction, payload: JSONObject = JSONObject()) {
        if (!engine.isWebReady) return
        engine.bridge.send(engine.webView, action, payload)
    }

    internal fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // No activity found to handle the URL.
        }
    }

    /**
     * On rotation / size change, tell the web runtime whether the layout is compact
     * (CONTRACT B.3 `mobileStateChange`). The initial state is seeded in the load
     * URL; this keeps the runtime in sync afterwards (hosts that handle config
     * changes themselves).
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        sendIfReady(
            LoopsAIBridgeProtocol.NativeAction.MOBILE_STATE_CHANGE,
            JSONObject().put("isMobile", newConfig.screenWidthDp < 600)
        )
    }

    override fun onDestroyView() {
        if (engine.presenter === this) {
            (engine.webView.parent as? ViewGroup)?.removeView(engine.webView)
            // Revert to the application context so a warm engine never retains the
            // Activity (the next presentation swaps its own Activity back in).
            (engine.webView.context as? MutableContextWrapper)?.baseContext =
                requireContext().applicationContext
            engine.presenter = null
            // Cold mode disposes the engine outright. With keep-alive on, the engine
            // normally stays warm in the pool for an instant reopen — but if it was
            // superseded in the pool (e.g. a newer presentation of the same agent),
            // this fragment owns the only reference, so dispose it to avoid a leak.
            if (!config.keepAliveEnabled || !LoopsChatWebViewPool.isPooled(engine)) {
                engine.teardown()
            }
        }
        progressBar = null
        errorView = null
        closeButton = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        // Release the registry entry only on a real teardown, not a config change.
        if (activity?.isChangingConfigurations != true) {
            LoopsAIChatRegistry.release(token)
        }
        super.onDestroy()
    }
}
