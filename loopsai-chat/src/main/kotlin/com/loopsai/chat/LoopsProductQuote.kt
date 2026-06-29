package com.loopsai.chat

/**
 * A product quote surfaced by the web runtime (CONTRACT B.2 `productQuoteChanged`).
 * A `null` quote (in the callback) means the quote was cleared.
 *
 * Mirror of the iOS `LoopsProductQuote`.
 */
data class LoopsProductQuote(
    val code: String,
    val name: String? = null,
    val image: String? = null,
    val vtoEnabled: Boolean = false
)
