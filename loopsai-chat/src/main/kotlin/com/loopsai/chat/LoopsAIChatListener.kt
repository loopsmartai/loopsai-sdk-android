package com.loopsai.chat

/**
 * Host callbacks for a [LoopsAIChatFragment] (CONTRACT B.5).
 *
 * Mirror of the iOS `LoopsAIChatDelegate`. All methods have default no-op
 * implementations except the two navigation callbacks ([onCloseRequested] /
 * [onOpenUrlRequested]), which keep their historical default behavior.
 */
interface LoopsAIChatListener {

    /** The web runtime emitted `ready` — the bridge is live and context is applied. */
    fun onReady(fragment: LoopsAIChatFragment) {}

    /** A chat message event crossed the bridge (`newChatStarted`, open/overlay frames). */
    fun onMessageEvent(fragment: LoopsAIChatFragment, event: Map<String, Any?>) {}

    /** The bot started (`true`) or stopped (`false`) responding. */
    fun onResponding(fragment: LoopsAIChatFragment, isResponding: Boolean) {}

    /**
     * A canonical analytics event crossed the bridge (CONTRACT Part A,
     * `channel:"mobile_app"`). Delivered as-is — the SDK dictates the shape.
     */
    fun onAnalyticsEvent(fragment: LoopsAIChatFragment, event: LoopsAnalyticsEvent) {}

    /** The active product quote changed (`null` when cleared). */
    fun onProductQuoteChanged(fragment: LoopsAIChatFragment, quote: LoopsProductQuote?) {}

    /** A conversation became active. */
    fun onConversationActive(fragment: LoopsAIChatFragment) {}

    /** An unrecoverable error occurred (load / bridge / session). */
    fun onError(fragment: LoopsAIChatFragment, error: LoopsError) {}

    /** The web runtime requested the container to close. */
    fun onCloseRequested(fragment: LoopsAIChatFragment) {
        fragment.activity?.onBackPressedDispatcher?.onBackPressed()
    }

    /** A foreign link / `openExternalUrl` should open outside the WebView. */
    fun onOpenUrlRequested(fragment: LoopsAIChatFragment, url: String) {
        fragment.openExternalUrl(url)
    }
}
