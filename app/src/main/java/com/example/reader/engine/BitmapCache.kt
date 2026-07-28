package com.example.reader.engine

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.LruCache
import com.example.reader.engine.BitmapPool

/**
 * Explicit, memory-budgeted LRU cache of baked page [Bitmap]s.
 *
 * Replaces the previous `mutableStateMapOf<Int, ImageBitmap>` + hand-written ±2 window in
 * [com.example.reader.ui.reader.ReaderContent]. The cache is the **single source of truth** for
 * a bitmap's lifetime:
 *
 * - [sizeOf] accounts for real byte usage (`bitmap.byteCount`), so the budget is in bytes not
 *   page count.
 * - When the LRU evicts an entry it routes the [Bitmap] to [BitmapPool] (recycle bin) instead of
 *   leaking it; Compose's observable `StateMap` only *views* the cache and mirrors evictions so a
 *   recycled bitmap is never drawn.
 * - [trimMemory] reacts to system memory pressure by shedding the oldest pages (see §7.2).
 *
 * @property memoryBudgetBytes Maximum bytes the cache may hold before evicting the LRU page.
 */
class BitmapCache(context: Context, private val memoryBudgetBytes: Long) : ComponentCallbacks2 {

    private val appContext: Context = context.applicationContext
    private val lock = Any()

    /** Evicted `(pageIndex, bitmap)` pairs awaiting detachment from the Compose view + recycle. */
    private val evictedQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<Int, Bitmap>>()

    private val lru = object : LruCache<PageKey, Bitmap>(
        memoryBudgetBytes.coerceAtMost(Int.MAX_VALUE.toLong()).coerceAtLeast(1).toInt()
    ) {
        override fun sizeOf(key: PageKey, value: Bitmap): Int = value.byteCount

        override fun entryRemoved(
            evicted: Boolean,
            key: PageKey,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (!oldValue.isRecycled) {
                // Detach from the Compose view + recycle/pool on the main thread (see drainEvicted).
                evictedQueue.add(key.index to oldValue)
            }
        }
    }

    init {
        appContext.registerComponentCallbacks(this)
    }

    /**
     * Returns the cached bitmap for [key], or null on miss.
     */
    fun get(key: PageKey): Bitmap? = synchronized(lock) { lru.get(key) }

    /**
     * Inserts [bitmap] under [key]. May evict the least-recently-used page(s) to stay within
     * the memory budget; evicted bitmaps are recycled via [BitmapPool] (recorded for the view
     * to detach).
     */
    fun put(key: PageKey, bitmap: Bitmap) {
        synchronized(lock) { lru.put(key, bitmap) }
    }

    /**
     * Indices of pages currently cached for the given pagination [generation]. Used by the UI to
     * drop any published bitmap whose cache entry is gone.
     */
    fun cachedIndices(generation: Int): Set<Int> = synchronized(lock) {
        lru.snapshot().keys.filter { it.generation == generation }.map { it.index }.toSet()
    }

    /**
     * Removes every cached page whose index lies outside
     * `[center - halfWindow, center + halfWindow]`, recycling the evicted bitmaps. The evicted
     * indices are surfaced through [drainEvicted] so the Compose view can stop drawing them.
     */
    fun evictOutsideWindow(center: Int, halfWindow: Int) {
        synchronized(lock) {
            val keys = lru.snapshot().keys.toList()
            for (k in keys) {
                if (k.index < center - halfWindow || k.index > center + halfWindow) {
                    lru.remove(k) // -> entryRemoved -> queued for recycle
                }
            }
        }
    }

    /**
     * Drains evicted `(index, bitmap)` pairs. The caller MUST remove `index` from its observable
     * view and then [BitmapPool.release] the bitmap (now safe, since nothing draws it).
     */
    fun drainEvicted(): List<Pair<Int, Bitmap>> {
        val out = mutableListOf<Pair<Int, Bitmap>>()
        var pair: Pair<Int, Bitmap>? = evictedQueue.poll()
        while (pair != null) {
            out.add(pair)
            pair = evictedQueue.poll()
        }
        return out
    }

    /** Empties the cache, recycling every bitmap (recorded for the view to detach). */
    fun clear() {
        synchronized(lock) { lru.evictAll() }
    }

    /**
     * Responds to [ComponentCallbacks2.onTrimMemory] by shedding the oldest pages:
     * `UI_HIDDEN` keeps ~50%, `MODERATE` ~25%, `COMPLETE` drops everything and clears the pool.
     */
    fun trimMemory(level: Int) {
        synchronized(lock) {
            when {
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                    lru.evictAll()
                    BitmapPool.clear()
                }
                level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> evictTo(0.25f)
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> evictTo(0.5f)
                else -> { /* lower levels: keep working set */ }
            }
        }
    }

    /** Unregisters system callbacks and releases pooled bitmaps. Call on disposal. */
    fun dispose() {
        appContext.unregisterComponentCallbacks(this)
        BitmapPool.clear()
    }

    private fun evictTo(ratio: Float) {
        val target = (memoryBudgetBytes * ratio).coerceAtLeast(1).toInt()
        // LruCache.snapshot() preserves LRU order (least-recently-used first).
        val keys = synchronized(lock) { lru.snapshot().keys.toList() }
        for (k in keys) {
            if (lru.size() <= target) break
            lru.remove(k) // -> entryRemoved -> queued for recycle
        }
    }

    override fun onTrimMemory(level: Int) = trimMemory(level)

    override fun onLowMemory() = trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    companion object {
        /** Device-tier memory budget (bytes) following §7.2 of the optimisation design. */
        fun budgetForDevice(context: Context): Long {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalGb = memInfo.totalMem / (1024L * 1024L * 1024L)
            val mb = when {
                am.memoryClass <= 64 -> 24 // low (<3GB-class devices)
                totalGb > 6 -> 96 // high-RAM devices
                else -> 48 // default mid tier
            }
            return mb.toLong() * 1024L * 1024L
        }
    }
}

/**
 * Identifies a cached page bitmap. [generation] is the pagination version (bumped on every
 * (re)pagination), so a re-paginated book's old pages naturally miss and get re-baked.
 */
data class PageKey(val generation: Int, val index: Int)
