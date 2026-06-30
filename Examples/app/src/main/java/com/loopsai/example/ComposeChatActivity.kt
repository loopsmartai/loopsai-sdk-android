package com.loopsai.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.loopsai.chat.LoopsAIChatView

/**
 * The Jetpack Compose integration path: a single `LoopsAIChatView` composable.
 * Callbacks are surfaced as toasts to keep the screen focused on the SDK view.
 */
class ComposeChatActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = LoopsConfig.build(LoopsConfig.optionsFrom(intent)) { /* analytics */ }

        setContent {
            MaterialTheme {
                LoopsAIChatView(
                    config = config,
                    modifier = Modifier.fillMaxSize(),
                    onClose = { finish() },
                    onReady = { toast("Chat ready") },
                    onProductQuoteChanged = { quote -> toast("Quote: ${quote?.code ?: "cleared"}") },
                    onAnalyticsEvent = { event -> toast(event.event) },
                    onOpenUrl = { url ->
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    },
                    onError = { error -> toast("Error: $error") },
                )
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
