package com.example.reader.feature.wifi

import android.content.Context
import android.util.Log
import com.example.reader.R
import com.example.reader.feature.import.ImportManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.HashMap

/**
 * Embedded HTTP server for "WiFi 传书" (E02).
 *
 * Built on [NanoHTTPD] (pure Java, Android-friendly). On `GET /` it returns the upload page
 * (`res/raw/wifi_upload.html`); on `POST /` it parses the multipart body, copies each uploaded
 * file into an import pass and lets [ImportManager.importArchives] extract + upsert books.
 *
 * Everything is best-effort and wrapped so a malformed request never crashes the app.
 */
class WifiServer(port: Int, context: Context) : NanoHTTPD(port) {

    private val appContext = context.applicationContext
    private val importManager = ImportManager(appContext)

    /** Number of books imported since the server started. Read by the ViewModel for display. */
    @Volatile
    var lastImportedCount: Int = 0
        private set

    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.method) {
                Method.GET -> servePage()
                Method.POST -> handleUpload(session)
                else -> newFixedLengthResponse(
                    Response.Status.METHOD_NOT_ALLOWED,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Method not allowed"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "serve failed", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    private fun servePage(): Response {
        val html = runCatching {
            appContext.resources.openRawResource(R.raw.wifi_upload).bufferedReader().use { it.readText() }
        }.getOrElse { DEFAULT_HTML }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val paths = files.values.filter { it.isNotBlank() && File(it).exists() }
        val count = if (paths.isNotEmpty()) {
            runBlocking { runCatching { importManager.importArchives(paths) }.getOrDefault(0) }
        } else {
            0
        }
        lastImportedCount = count
        val body = """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>导入完成</title></head>
            <body style="font-family:sans-serif;text-align:center;margin-top:48px;color:#333">
            <h3>导入完成：$count 本</h3>
            <p><a href="/">继续上传</a></p>
            </body></html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)
    }

    companion object {
        private const val TAG = "WifiServer"
        private const val DEFAULT_HTML =
            "<!DOCTYPE html><html><body style='text-align:center;margin-top:48px'>" +
                "<h3>WiFi 传书</h3><p>上传页面加载失败。</p></body></html>"
    }
}
