package com.example.reader.crash

import android.app.Application
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter

class GlobalExceptionHandler(
    private val app: Application
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Capture crash details
            val stackTrace = StringWriter()
            val printWriter = PrintWriter(stackTrace)
            throwable.printStackTrace(printWriter)
            printWriter.flush()

            val crashInfo = buildString {
                appendLine("=== CRASH REPORT ===")
                appendLine("Time: ${System.currentTimeMillis()}")
                appendLine("Thread: ${thread.name} (${thread.id})")
                appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                appendLine()
                appendLine("Stack Trace:")
                appendLine(stackTrace.toString())
                appendLine()
                // Add cause chain
                var cause = throwable.cause
                var causeLevel = 1
                while (cause != null) {
                    appendLine("Caused by ($causeLevel): ${cause.javaClass.name}: ${cause.message}")
                    val causeWriter = StringWriter()
                    val causePrinter = PrintWriter(causeWriter)
                    cause.printStackTrace(causePrinter)
                    causePrinter.flush()
                    appendLine(causeWriter.toString())
                    cause = cause.cause
                    causeLevel++
                }
                appendLine("=== END CRASH REPORT ===")
            }

            // Write to file
            CrashLogger.write(app, crashInfo)
        } catch (e: Exception) {
            // If crash logging itself fails, fall back to default handler
            e.printStackTrace()
        } finally {
            // Pass to default handler or kill process
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                System.exit(1)
            }
        }
    }
}
