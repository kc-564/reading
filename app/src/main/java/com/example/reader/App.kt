package com.example.reader

import android.app.Application
import com.example.reader.crash.GlobalExceptionHandler

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        initCrashHandler()
    }

    private fun initCrashHandler() {
        val handler = GlobalExceptionHandler(this)
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
