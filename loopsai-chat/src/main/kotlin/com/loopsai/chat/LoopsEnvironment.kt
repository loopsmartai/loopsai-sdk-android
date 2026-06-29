package com.loopsai.chat

/**
 * Where the SDK loads the web runtime from (CONTRACT B.6 origin allowlist).
 *
 * Mirror of the iOS `LoopsEnvironment`.
 */
sealed class LoopsEnvironment {
    /** `chat.loopsai.com` — production. */
    data object Production : LoopsEnvironment()

    /** Self-host / on-prem. The host is added to the origin allowlist. */
    data class Custom(val url: String) : LoopsEnvironment()

    val baseUrl: String
        get() = when (this) {
            is Production -> "https://chat.loopsai.com"
            is Custom -> url
        }
}
