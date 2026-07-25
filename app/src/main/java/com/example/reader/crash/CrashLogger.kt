package com.example.reader.crash

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    private const val CRASH_DIR = "crashes"
    private const val MAX_CRASH_FILES = 20

    fun write(context: Context, crashInfo: String): File {
        val crashDir = File(context.filesDir, CRASH_DIR)
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(Date())
        val crashFile = File(crashDir, "crash_$timestamp.log")

        crashFile.writeText(crashInfo)

        // Clean up old crash files if exceeding max count
        cleanupOldCrashes(crashDir)

        return crashFile
    }

    fun getCrashLogs(context: Context): List<File> {
        val crashDir = File(context.filesDir, CRASH_DIR)
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles()
            ?.filter { it.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun clearCrashLogs(context: Context) {
        val crashDir = File(context.filesDir, CRASH_DIR)
        if (crashDir.exists()) {
            crashDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun cleanupOldCrashes(crashDir: File) {
        val files = crashDir.listFiles()
            ?.filter { it.name.endsWith(".log") }
            ?.sortedBy { it.lastModified() }
            ?: return

        if (files.size > MAX_CRASH_FILES) {
            files.take(files.size - MAX_CRASH_FILES).forEach { it.delete() }
        }
    }
}
