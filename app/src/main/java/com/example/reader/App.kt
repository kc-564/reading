package com.example.reader

import android.app.Application
import com.example.reader.crash.GlobalExceptionHandler
import com.example.reader.db.AppDatabase
import com.example.reader.engine.LayoutSignature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        initCrashHandler()
        // Purge stale layout-cache rows produced by a previous formatting version (e.g. old "v3"
        // keys) so a formatting bump (FORMATTING_VERSION_ID) self-heals without leaking dead rows.
        purgeStaleLayoutCache()
    }

    private fun initCrashHandler() {
        val handler = GlobalExceptionHandler(this)
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    /**
     * One-shot, fire-and-forget purge of the layout_cache table. Runs only when the persisted
     * purge version differs from [LayoutSignature.FORMATTING_VERSION_ID], so it executes at most
     * once per formatting change. Safe to run before any book is opened (the cache is only
     * written during pagination, which starts only after the reader screen is launched).
     */
    private fun purgeStaleLayoutCache() {
        val prefs = getSharedPreferences(PREF_LAYOUT_CACHE, MODE_PRIVATE)
        val purgedVid = prefs.getInt(KEY_PURGED_VERSION, 0)
        if (purgedVid == LayoutSignature.FORMATTING_VERSION_ID) return
        purgeScope.launch {
            runCatching { AppDatabase.getInstance(this@App).layoutCacheDao().deleteAll() }
            prefs.edit().putInt(KEY_PURGED_VERSION, LayoutSignature.FORMATTING_VERSION_ID).apply()
        }
    }

    private val purgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        lateinit var instance: App
            private set

        private const val PREF_LAYOUT_CACHE = "reader_layout_cache"
        private const val KEY_PURGED_VERSION = "purged_formatting_version"
    }
}
