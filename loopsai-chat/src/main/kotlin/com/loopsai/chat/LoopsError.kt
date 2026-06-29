package com.loopsai.chat

/**
 * Errors surfaced to the host through [LoopsAIChatListener.onError].
 *
 * Mirror of the iOS `LoopsError`.
 */
sealed class LoopsError(message: String) : Exception(message) {
    /** The WebView failed to load the chat runtime. */
    class Load(val underlying: String) : LoopsError(underlying)

    /** The bridge received a malformed or unsupported message. */
    class Bridge(val reason: String) : LoopsError(reason)

    /** Session bootstrap (`/api/widget/session`) failed. */
    class Session(val reason: String) : LoopsError(reason)
}
