package com.loopsai.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.loopsai.chat.LoopsAIChat
import com.loopsai.chat.LoopsAIChatFragment
import com.loopsai.chat.LoopsAIChatListener
import com.loopsai.chat.LoopsError
import com.loopsai.example.databinding.ActivityMainBinding

/**
 * Home screen: collects every [LoopsConfig.Options] value and launches the chat
 * through each of the SDK's three integration styles, plus the data/session APIs.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Prefill the context fields with the sample data.
        binding.etProductCode.setText(LoopsConfig.sampleProduct["productCode"])
        binding.etProductName.setText(LoopsConfig.sampleProduct["productName"])
        binding.etUserId.setText(LoopsConfig.sampleUser["userId"])
        binding.etFirstName.setText(LoopsConfig.sampleUser["firstName"])
        binding.etEmail.setText(LoopsConfig.sampleUser["email"])

        binding.btnOpenEmbedded.setOnClickListener {
            startActivity(LoopsConfig.putInto(options(), Intent(this, ChatActivity::class.java)))
        }
        binding.btnOpenCompose.setOnClickListener {
            startActivity(LoopsConfig.putInto(options(), Intent(this, ComposeChatActivity::class.java)))
        }
        binding.btnOpenActivity.setOnClickListener {
            // The simplest integration — a full-screen, self-managed Activity.
            val config = LoopsConfig.build(options()) { /* analytics → see ChatActivity log */ }
            LoopsAIChat.openAsActivity(this, config, toastListener)
        }

        binding.btnClearCache.setOnClickListener {
            LoopsAIChat.clearWebCache()
            toast("Warm WebView pool cleared")
        }
        binding.btnResetData.setOnClickListener {
            LoopsAIChat.resetAllData(this) { toast("All chat data wiped") }
        }
    }

    private fun options() = LoopsConfig.Options(
        customEnvUrl = binding.etEnvUrl.text?.toString(),
        locale = binding.etLocale.text?.toString(),
        showCloseButton = binding.swShowClose.isChecked,
        startFresh = binding.swStartFresh.isChecked,
        keepAliveEnabled = binding.swKeepAlive.isChecked,
        virtualTryOn = binding.swVto.isChecked,
        searchEscalation = binding.swSearch.isChecked,
        productSuggestion = binding.swProductSuggestion.isChecked,
        outfitSuggestion = binding.swOutfit.isChecked,
        productCode = binding.etProductCode.text?.toString(),
        productName = binding.etProductName.text?.toString(),
        userId = binding.etUserId.text?.toString(),
        firstName = binding.etFirstName.text?.toString(),
        email = binding.etEmail.text?.toString(),
    )

    /** Minimal listener for the Activity-launch path (the chat owns its own window). */
    private val toastListener = object : LoopsAIChatListener {
        override fun onReady(fragment: LoopsAIChatFragment) = toast("Chat ready")
        override fun onError(fragment: LoopsAIChatFragment, error: LoopsError) =
            toast("Error: $error")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
