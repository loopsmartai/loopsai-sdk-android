package com.loopsai.chat.internal

/**
 * Process-wide pool of long-lived chat engines (CONTRACT B — performance).
 *
 * A small LRU of engines is kept warm so the common case — the user closing the
 * chat and reopening it to the same agent — reuses the already-running runtime
 * with no network and no JS re-execution. Keyed by agent + environment + locale; a
 * host app almost always loads a single agent.
 *
 * Mirror of the iOS `LoopsChatWebViewPool`.
 */
internal object LoopsChatWebViewPool {

    private const val MAX_WARM = 2

    private val warm = HashMap<String, LoopsChatEngine>()
    private val lru = ArrayList<String>()

    /**
     * Borrow a warm engine for [key], or `null` if none is reusable. Only an engine
     * that is **idle** (no live presenter) and **ready** (completed the `ready`
     * handshake) is handed out, so a reuse can reveal instantly. The caller sets
     * `engine.presenter` immediately after.
     */
    @Synchronized
    fun borrow(key: String): LoopsChatEngine? {
        val engine = warm[key] ?: return null
        if (!engine.isAvailable || !engine.isWebReady) return null
        touch(key)
        return engine
    }

    /** Register a freshly-created engine so a later presentation can reuse it. */
    @Synchronized
    fun store(key: String, engine: LoopsChatEngine) {
        val existing = warm[key]
        // Replacing an idle engine for the same key (e.g. a not-yet-ready engine
        // abandoned on rotation): tear it down so its WebView is not leaked. A still
        // presented engine is left alone — its fragment disposes it on teardown.
        if (existing != null && existing !== engine && existing.isAvailable) {
            existing.teardown()
        }
        warm[key] = engine
        touch(key)
        evictIfNeeded()
    }

    /** True if [engine] is the current pooled engine for its key. */
    @Synchronized
    fun isPooled(engine: LoopsChatEngine): Boolean = warm.containsValue(engine)

    /** Release a presented engine back to the warm pool (clears the presenter). */
    @Synchronized
    fun release(engine: LoopsChatEngine) {
        engine.presenter = null
    }

    /** Drop a warm engine and tear down its WebView. */
    @Synchronized
    fun purge(key: String) {
        warm.remove(key)?.teardown()
        lru.remove(key)
    }

    /** Drop every warm engine. Exposed to hosts via `LoopsAIChat.clearWebCache()`. */
    @Synchronized
    fun purgeAll() {
        warm.values.forEach { it.teardown() }
        warm.clear()
        lru.clear()
    }

    private fun touch(key: String) {
        lru.remove(key)
        lru.add(key)
    }

    private fun evictIfNeeded() {
        // Never evict an engine that is currently on screen; walk oldest → newest.
        while (warm.size > MAX_WARM) {
            val victim = lru.firstOrNull { warm[it]?.isAvailable == true } ?: break
            purge(victim)
        }
    }
}
