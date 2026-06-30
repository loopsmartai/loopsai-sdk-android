package com.loopsai.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.loopsai.chat.LoopsAIChat
import com.loopsai.chat.LoopsAIChatContext
import com.loopsai.chat.LoopsAIChatFragment
import com.loopsai.chat.LoopsAIChatListener
import com.loopsai.chat.LoopsAnalyticsEvent
import com.loopsai.chat.LoopsError
import com.loopsai.chat.LoopsProductQuote
import com.loopsai.example.databinding.ActivityChatBinding

/**
 * Embeds the chat as a Fragment and drives every public method on
 * [LoopsAIChatFragment], while logging every [LoopsAIChatListener] callback.
 */
class ChatActivity : AppCompatActivity(), LoopsAIChatListener {

    private lateinit var binding: ActivityChatBinding
    private var chat: LoopsAIChatFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Survive config changes: reuse the fragment the FragmentManager restored.
        chat = supportFragmentManager.findFragmentById(R.id.chatContainer) as? LoopsAIChatFragment
        if (chat == null) {
            val config = LoopsConfig.build(LoopsConfig.optionsFrom(intent)) { logUi(it) }
            chat = LoopsAIChat.open(supportFragmentManager, R.id.chatContainer, config, this)
        } else {
            chat?.listener = this
        }

        wireControls()
    }

    private fun wireControls() = with(binding) {
        btnSend.setOnClickListener {
            val text = etMessage.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                chat?.sendMessage(text)
                etMessage.text?.clear()
            }
        }
        btnSuggestSize.setOnClickListener { chat?.suggestSize() }
        btnVto.setOnClickListener { chat?.startVirtualTryOn(LoopsConfig.sampleProduct) }
        btnTryFromQuote.setOnClickListener { chat?.startTryOnFromQuote() }
        btnQuote.setOnClickListener { chat?.quoteProduct(LoopsConfig.sampleProduct) }
        btnClearQuote.setOnClickListener { chat?.clearProductQuote() }
        btnSearch.setOnClickListener { chat?.openWithSearch("summer dress", productsOnly = false) }
        btnSyncCustomer.setOnClickListener { chat?.syncCustomerDetails("cust_42") }
        btnFont.setOnClickListener { chat?.setWebsiteFont("Inter, sans-serif") }
        btnUpdateContext.setOnClickListener {
            chat?.updateContext(
                LoopsAIChatContext(productContext = mapOf("productCode" to "SKU-67890"))
            )
        }
        btnNewConversation.setOnClickListener { chat?.startNewConversation() }
        btnCloseOverlays.setOnClickListener { chat?.closeOverlays() }
        btnConsentOn.setOnClickListener { chat?.setAnalyticsConsent(true) }
        btnConsentOff.setOnClickListener { chat?.setAnalyticsConsent(false) }
    }

    // MARK: - LoopsAIChatListener

    override fun onReady(fragment: LoopsAIChatFragment) = logUi("onReady")
    override fun onResponding(fragment: LoopsAIChatFragment, isResponding: Boolean) =
        logUi("onResponding: $isResponding")
    override fun onProductQuoteChanged(fragment: LoopsAIChatFragment, quote: LoopsProductQuote?) =
        logUi("onProductQuoteChanged: ${quote?.code ?: "cleared"}")
    override fun onAnalyticsEvent(fragment: LoopsAIChatFragment, event: LoopsAnalyticsEvent) =
        logUi("onAnalyticsEvent: ${event.event}")
    override fun onMessageEvent(fragment: LoopsAIChatFragment, event: Map<String, Any?>) =
        logUi("onMessageEvent: ${event["action"] ?: event}")
    override fun onConversationActive(fragment: LoopsAIChatFragment) = logUi("onConversationActive")
    override fun onError(fragment: LoopsAIChatFragment, error: LoopsError) = logUi("onError: $error")

    override fun onCloseRequested(fragment: LoopsAIChatFragment) {
        logUi("onCloseRequested")
        finish()
    }

    override fun onOpenUrlRequested(fragment: LoopsAIChatFragment, url: String) {
        logUi("onOpenUrlRequested: $url")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun logUi(line: String) {
        binding.tvLog.append("$line\n")
        binding.logScroll.post { binding.logScroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }
}
