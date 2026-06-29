package com.loopsai.chat.internal

import com.loopsai.chat.LoopsAIChatConfig
import com.loopsai.chat.LoopsAIChatListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process handoff for the non-`Parcelable` [LoopsAIChatConfig] (it carries
 * analytics closures) and the [LoopsAIChatListener].
 *
 * The iOS VC receives its config + delegate directly in its initializer; Android
 * fragments/activities are reconstructed from a `Bundle`/`Intent`, which cannot
 * carry closures. We pass a small string token through the `Bundle`/`Intent` and
 * resolve the live objects here. Survives configuration changes (process alive);
 * if the process is killed the entry is gone and the host re-launches — session
 * identity is restored from [com.loopsai.chat.LoopsSessionStore], not the config.
 */
internal object LoopsAIChatRegistry {
    const val ARG_TOKEN = "loopsai_token"

    private val configs = ConcurrentHashMap<String, LoopsAIChatConfig>()
    private val listeners = ConcurrentHashMap<String, LoopsAIChatListener>()

    fun register(config: LoopsAIChatConfig, listener: LoopsAIChatListener?): String {
        val token = UUID.randomUUID().toString()
        configs[token] = config
        listener?.let { listeners[token] = it }
        return token
    }

    fun config(token: String?): LoopsAIChatConfig? = token?.let { configs[it] }

    fun listener(token: String?): LoopsAIChatListener? = token?.let { listeners[it] }

    fun setListener(token: String?, listener: LoopsAIChatListener?) {
        token ?: return
        if (listener == null) listeners.remove(token) else listeners[token] = listener
    }

    fun release(token: String?) {
        token ?: return
        configs.remove(token)
        listeners.remove(token)
    }
}
