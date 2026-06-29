package com.loopsai.chat

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import com.loopsai.chat.internal.LoopsAIChatRegistry

/**
 * Jetpack Compose wrapper around [LoopsAIChatFragment]. Provide a
 * [LoopsAIChatConfig] and opt into host callbacks via the lambdas. Mirror of the
 * iOS SwiftUI `LoopsAIChatView`.
 */
@Composable
fun LoopsAIChatView(
    config: LoopsAIChatConfig,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    onReady: (() -> Unit)? = null,
    onResponding: ((Boolean) -> Unit)? = null,
    onProductQuoteChanged: ((LoopsProductQuote?) -> Unit)? = null,
    onAnalyticsEvent: ((LoopsAnalyticsEvent) -> Unit)? = null,
    onError: ((LoopsError) -> Unit)? = null
) {
    // One stable token per config instance; released when this composable leaves.
    val token = remember(config) { LoopsAIChatRegistry.register(config, null) }
    DisposableEffect(token) {
        onDispose { LoopsAIChatRegistry.release(token) }
    }

    val listener = remember(onClose, onOpenUrl, onReady, onResponding, onProductQuoteChanged, onAnalyticsEvent, onError) {
        object : LoopsAIChatListener {
            override fun onReady(fragment: LoopsAIChatFragment) {
                onReady?.invoke()
            }

            override fun onResponding(fragment: LoopsAIChatFragment, isResponding: Boolean) {
                onResponding?.invoke(isResponding)
            }

            override fun onProductQuoteChanged(fragment: LoopsAIChatFragment, quote: LoopsProductQuote?) {
                onProductQuoteChanged?.invoke(quote)
            }

            override fun onAnalyticsEvent(fragment: LoopsAIChatFragment, event: LoopsAnalyticsEvent) {
                onAnalyticsEvent?.invoke(event)
            }

            override fun onError(fragment: LoopsAIChatFragment, error: LoopsError) {
                onError?.invoke(error)
            }

            override fun onCloseRequested(fragment: LoopsAIChatFragment) {
                if (onClose != null) onClose() else super.onCloseRequested(fragment)
            }

            override fun onOpenUrlRequested(fragment: LoopsAIChatFragment, url: String) {
                if (onOpenUrl != null) onOpenUrl(url) else super.onOpenUrlRequested(fragment, url)
            }
        }
    }

    AndroidFragment<LoopsAIChatFragment>(
        modifier = modifier,
        arguments = Bundle().apply { putString(LoopsAIChatRegistry.ARG_TOKEN, token) },
        onUpdate = { fragment -> fragment.listener = listener }
    )
}
