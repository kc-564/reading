package com.example.reader.parser

import java.io.File
import java.nio.charset.Charset

/**
 * LRU (Least Recently Used) cache for encoding detection results.
 *
 * Key format: "$path:$size:$mtime:$defaultPref"
 * Value: Detected Charset
 *
 * The cache is thread-safe and has a maximum capacity of 100 entries.
 */
class LruEncodingCache(private val maxSize: Int = 100) {

    private val cache = object : LinkedHashMap<String, Charset>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Charset>?): Boolean {
            return size > maxSize
        }
    }

    /**
     * Retrieves a cached encoding result for the given file and preferences.
     *
     * @param filePath Absolute path to the file
     * @param defaultPref User's default encoding preference
     * @return Cached Charset if available, or null if not in cache
     */
    @Synchronized
    fun get(filePath: String, defaultPref: String): Charset? {
        val key = buildKey(filePath, defaultPref)
        return cache[key]
    }

    /**
     * Stores an encoding detection result in the cache.
     *
     * @param filePath Absolute path to the file
     * @param defaultPref User's default encoding preference
     * @param charset Detected Charset to cache
     */
    @Synchronized
    fun put(filePath: String, defaultPref: String, charset: Charset) {
        val key = buildKey(filePath, defaultPref)
        cache[key] = charset
    }

    /**
     * Retrieves and caches in one call. If the cache misses, calls the provided loader function.
     *
     * @param filePath Absolute path to the file
     * @param defaultPref User's default encoding preference
     * @param loader Function that performs the actual encoding detection on cache miss
     * @return The detected Charset (from cache or freshly detected)
     */
    @Synchronized
    fun getOrPut(filePath: String, defaultPref: String, loader: () -> Charset): Charset {
        val key = buildKey(filePath, defaultPref)
        return cache.getOrPut(key) { loader() }
    }

    /**
     * Clears all entries from the cache.
     */
    @Synchronized
    fun clear() {
        cache.clear()
    }

    /**
     * Returns the current number of entries in the cache.
     */
    @Synchronized
    fun size(): Int {
        return cache.size
    }

    /**
     * Removes a specific entry from the cache.
     */
    @Synchronized
    fun remove(filePath: String, defaultPref: String) {
        val key = buildKey(filePath, defaultPref)
        cache.remove(key)
    }

    /**
     * Builds the cache key from file metadata and default preference.
     */
    private fun buildKey(filePath: String, defaultPref: String): String {
        val file = File(filePath)
        return if (file.exists()) {
            "$filePath:${file.length()}:${file.lastModified()}:$defaultPref"
        } else {
            "$filePath:0:0:$defaultPref"
        }
    }
}
