package com.loopsai.chat.internal

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.loopsai.chat.BuildConfig
import com.loopsai.chat.LoopsAIChatConfig
import com.loopsai.chat.LoopsAIChatFragment
import com.loopsai.chat.LoopsAnalyticsContext
import com.loopsai.chat.LoopsAnalyticsDispatcher
import com.loopsai.chat.LoopsAnalyticsEvent
import com.loopsai.chat.LoopsProductQuote
import com.loopsai.chat.LoopsSessionStore
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * A long-lived chat runtime: the [WebView] plus the bridge, session, analytics and
 * ready-state that belong to it. The engine — not the fragment — owns everything
 * durable, so the runtime keeps running in the background between presentations.
 * Reopening just re-parents this WebView; it is never torn down and re-fetched
 * while the app lives (unless keep-alive is off).
 *
 * Persistent web→native messages (`ready`, `persistSession`, `trackEvent`) are
 * handled here even when no fragment is attached, so nothing is dropped while the
 * chat is closed. UI-facing callbacks are forwarded to [presenter] (the currently
 * attached fragment) and simply skipped when the chat is closed.
 *
 * Mirror of the iOS `LoopsChatEngine`.
 */
internal class LoopsChatEngine(
    val webView: WebView,
    val config: LoopsAIChatConfig,
    val sessionStore: LoopsSessionStore
) : LoopsAIChatBridge.BridgeHost {

    val bridge = LoopsAIChatBridge(config, this)
    private val analyticsDispatcher: LoopsAnalyticsDispatcher = config.analytics.makeDispatcher()

    var isWebReady = false
    private var didSendInit = false
    var loadedUrl: String? = null
    private var usesJsInterface = false

    private var presenterRef: WeakReference<LoopsAIChatFragment>? = null

    /**
     * The fragment currently presenting this engine. **Weak** so it auto-clears when
     * the fragment is destroyed — that is what makes the engine reliably available
     * for the next open.
     */
    var presenter: LoopsAIChatFragment?
        get() = presenterRef?.get()
        set(value) {
            presenterRef = value?.let { WeakReference(it) }
        }

    /** Reusable when nothing is presenting it. */
    val isAvailable: Boolean get() = presenter == null

    @SuppressLint("SetJavaScriptEnabled")
    fun setup() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Origin allowlist enforced at attach time (CONTRACT B.6).
        if (WebMessageListenerBridge.isSupported()) {
            WebMessageListenerBridge.attach(webView, config.allowedHosts, bridge)
            usesJsInterface = false
        } else {
            JavaScriptInterfaceBridge.attach(webView, bridge)
            usesJsInterface = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                val host = url.host ?: return true
                if (config.allowedHosts.contains(host)) return false
                // Foreign link → open outside the WebView (CONTRACT B.6).
                presenter?.let { it.listener?.onOpenUrlRequested(it, url.toString()) }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                // Visual readiness only — the bridge-level `ready` action drives init.
                presenter?.onEnginePageVisible()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    presenter?.onEngineLoadError(error?.description?.toString() ?: "web resource error")
                }
            }
        }

        // Grant microphone capture only to allowlisted origins; the host app still
        // needs RECORD_AUDIO. Origin-gated so it is safe regardless of which flows
        // the server enables.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request ?: return
                val originHost = request.origin?.host
                val allowed = originHost != null && config.allowedHosts.contains(originHost)
                if (allowed && request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    request.deny()
                }
            }
        }
    }

    fun load(url: String) {
        isWebReady = false
        loadedUrl = url
        webView.loadUrl(url)
    }

    fun teardown() {
        if (usesJsInterface) JavaScriptInterfaceBridge.detach(webView)
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        presenter = null
    }

    // MARK: - BridgeHost (web → native)

    override fun onWebReady() {
        isWebReady = true
        if (!didSendInit) {
            didSendInit = true
            bridge.sendInitConfig(webView)
            bridge.sendInitContext(webView)
        }
        presenter?.onEngineReady()
    }

    override fun onTrackEvent(payload: JSONObject) {
        val context = LoopsAnalyticsContext.from(
            webView.context,
            locale = config.locale,
            anonUserId = sessionStore.anonUserId,
            conversationId = sessionStore.conversationId(config.agentId)
        )
        val event = LoopsAnalyticsEvent.from(payload, context) ?: return
        analyticsDispatcher.dispatch(event)
        presenter?.let { it.listener?.onAnalyticsEvent(it, event) }
    }

    override fun onPersistSession(payload: JSONObject) {
        sessionStore.applyPersistSession(payload)
    }

    override fun onCloseRequested() {
        presenter?.let { it.listener?.onCloseRequested(it) }
    }

    override fun onOpenUrlRequested(url: String) {
        presenter?.let { it.listener?.onOpenUrlRequested(it, url) }
    }

    override fun onMessageEvent(event: Map<String, Any?>) {
        presenter?.let { it.listener?.onMessageEvent(it, event) }
    }

    override fun onTokenRefreshRequested(requestId: String?) {
        // Reserved for an optional auth provider.
    }

    override fun onResponding(isResponding: Boolean) {
        presenter?.let { it.listener?.onResponding(it, isResponding) }
    }

    override fun onProductQuoteChanged(quote: LoopsProductQuote?) {
        presenter?.let { it.listener?.onProductQuoteChanged(it, quote) }
    }

    override fun onConversationActive() {
        presenter?.let { it.listener?.onConversationActive(it) }
    }

    companion object {
        /**
         * Build the reusable WebView on a [MutableContextWrapper] over the
         * application context. While presented, the fragment swaps the base to its
         * Activity (so native dropdowns / dialogs resolve a themed Activity context);
         * while warm, it reverts to the application context so the engine can outlive
         * any single Activity without leaking it.
         */
        fun makeWebView(appContext: Context): WebView = WebView(MutableContextWrapper(appContext))
    }
}
