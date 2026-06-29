package com.loopsai.chat.internal

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

internal object WebMessageListenerBridge {

    fun isSupported(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    }

    fun attach(
        webView: WebView,
        allowedHosts: Set<String>,
        bridge: LoopsAIChatBridge
    ) {
        val originRules = allowedHosts.map { "https://$it" }.toSet()

        WebViewCompat.addWebMessageListener(
            webView,
            "loopsAIBridge",
            originRules
        ) { _, message, sourceOrigin, _, _ ->
            if (!isOriginAllowed(sourceOrigin, allowedHosts)) return@addWebMessageListener
            val data = message.data ?: return@addWebMessageListener
            bridge.handleMessage(data)
        }
    }

    private fun isOriginAllowed(origin: Uri, allowedHosts: Set<String>): Boolean {
        val host = origin.host ?: return false
        return allowedHosts.contains(host)
    }
}
