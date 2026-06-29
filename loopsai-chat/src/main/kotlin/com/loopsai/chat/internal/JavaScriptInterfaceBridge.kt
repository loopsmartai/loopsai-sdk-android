package com.loopsai.chat.internal

import android.webkit.JavascriptInterface
import android.webkit.WebView

internal class JavaScriptInterfaceBridge(
    private val bridge: LoopsAIChatBridge
) {

    companion object {
        const val JS_INTERFACE_NAME = "loopsAIBridge"

        fun attach(webView: WebView, bridge: LoopsAIChatBridge) {
            val jsInterface = JavaScriptInterfaceBridge(bridge)
            webView.addJavascriptInterface(jsInterface, JS_INTERFACE_NAME)
        }

        fun detach(webView: WebView) {
            webView.removeJavascriptInterface(JS_INTERFACE_NAME)
        }
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        bridge.handleMessage(message)
    }
}
