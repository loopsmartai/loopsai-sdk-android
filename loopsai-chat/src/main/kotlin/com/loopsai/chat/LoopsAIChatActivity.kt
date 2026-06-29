package com.loopsai.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.loopsai.chat.internal.LoopsAIChatRegistry

/**
 * Full-screen host activity for [LoopsAIChat.openAsActivity]. The config + host
 * listener are resolved from the in-process registry by token (they cannot be
 * `Parcelable` — they carry analytics closures), mirroring the iOS VC receiving
 * its config + delegate in-process.
 */
class LoopsAIChatActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TOKEN = "loopsai_token"

        internal fun start(context: Context, token: String) {
            val intent = Intent(context, LoopsAIChatActivity::class.java).apply {
                putExtra(EXTRA_TOKEN, token)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loopsai_chat)

        val token = intent.getStringExtra(EXTRA_TOKEN)
        // If the config didn't survive process death, the registry is empty —
        // close cleanly rather than crash (the host re-launches).
        if (LoopsAIChatRegistry.config(token) == null) {
            finish()
            return
        }

        if (savedInstanceState != null) return

        val fragment = LoopsAIChatFragment.forToken(token!!)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
