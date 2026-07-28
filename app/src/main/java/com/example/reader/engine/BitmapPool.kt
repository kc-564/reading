package com.example.reader.engine

import android.graphics.Bitmap
import android.graphics.Bitmap.Config

/**
 * Thread-safe pool of reusable [Bitmap]s keyed by exact `(width, height, config)`.
 *
 * Why this exists: the reader bakes one [Bitmap] per page. On long books / fast flips the
 * previous hand-rolled window only ever created fresh bitmaps and let the GC reap them, which
 * produced allocation spikes and GC jitter. Reusing a same-sized [Bitmap] (after fully
 * re-painting it) keeps allocation flat — the pool is the "recycle bin" the [BitmapCache]
 * drains evicted bitmaps into.
 *
 * Contract:
 * - [acquire] returns a bitmap of the requested size, reusing a pooled one when possible and
 *   otherwise creating a new mutable [Bitmap].
 * - [release] returns a bitmap to the pool (or recycles it when the pool is full / the bitmap
 *   is already recycled).
 * - All access is guarded by a single lock so it is safe to call from [kotlinx.coroutines.Dispatchers.Default]
 *   (render) and the main thread (publish / trim) concurrently.
 */
object BitmapPool {

    private val lock = Any()
    private val pool = mutableListOf<Bitmap>()
    private const val MAX_POOL_SIZE = 12

    /**
     * Returns a mutable bitmap of the given dimensions. Prefers a pooled bitmap with identical
     * size/config; otherwise allocates a fresh one.
     */
    fun acquire(width: Int, height: Int, config: Config): Bitmap {
        synchronized(lock) {
            val it = pool.iterator()
            while (it.hasNext()) {
                val b = it.next()
                if (!b.isRecycled && b.width == width && b.height == height && b.config == config) {
                    it.remove()
                    return b
                }
            }
        }
        return Bitmap.createBitmap(width, height, config)
    }

    /**
     * Returns a bitmap to the pool for later reuse. Already-recycled bitmaps are ignored; when
     * the pool is at capacity the bitmap is recycled immediately to free native memory.
     */
    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        synchronized(lock) {
            if (pool.size < MAX_POOL_SIZE) {
                pool.add(bitmap)
            } else {
                bitmap.recycle()
            }
        }
    }

    /** Drops every pooled bitmap, reclaiming native memory immediately. */
    fun clear() {
        synchronized(lock) {
            val snapshot = pool.toList()
            pool.clear()
            for (b in snapshot) {
                if (!b.isRecycled) b.recycle()
            }
        }
    }
}
