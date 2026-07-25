package com.example.reader.feature.fonts

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.reader.ui.theme.FontFamilyKey
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages reader fonts (D04).
 *
 * Built-in families map to Android system font families. User-imported TTF/OTF files are
 * copied into `filesDir/fonts/` and resolved via [android.graphics.Typeface.Builder] +
 * [Font]. No commercial fonts are bundled.
 */
class FontManager(private val context: Context) {

    data class ImportedFont(
        val id: String,
        val name: String,
        val path: String
    )

    /** System font-family mapping for the built-in keys. */
    fun resolve(key: FontFamilyKey, importedId: String? = null): FontFamily = when (key) {
        FontFamilyKey.DEFAULT -> FontFamily.Default
        FontFamilyKey.SANS -> FontFamily.SansSerif
        FontFamilyKey.SERIF -> FontFamily.Serif
        FontFamilyKey.MONOSPACE -> FontFamily.Monospace
        FontFamilyKey.CUSTOM -> resolveCustom(importedId)
    }

    private fun resolveCustom(importedId: String?): FontFamily {
        val fonts = getImportedFonts()
        if (fonts.isEmpty()) return FontFamily.Default
        val target = fonts.firstOrNull { it.id == importedId } ?: fonts.first()
        return runCatching {
            FontFamily(Font(File(target.path)))
        }.getOrElse { FontFamily.Default }
    }

    /** Lists imported font metadata persisted in [com.example.reader.prefs.AppPrefs]. */
    fun getImportedFonts(): List<ImportedFont> {
        val json = runCatching {
            val prefs = com.example.reader.prefs.AppPrefs(context)
            kotlinx.coroutines.runBlocking { prefs.importedFonts.first() }
        }.getOrNull() ?: ""
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            val list = mutableListOf<ImportedFont>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ImportedFont(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        path = obj.getString("path")
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }

    /**
     * Imports a font from raw bytes, persisting it under `filesDir/fonts`.
     * @return the created [ImportedFont], or null on failure.
     */
    suspend fun importFont(name: String, bytes: ByteArray): ImportedFont? {
        return runCatching {
            val dir = File(context.filesDir, "fonts")
            if (!dir.exists()) dir.mkdirs()
            val safeName = name.replace(Regex("[^\\w.\\-]"), "_")
            val ext = if (safeName.endsWith(".ttf", true) || safeName.endsWith(".otf", true)) "" else ".ttf"
            val file = File(dir, safeName + ext)
            file.writeBytes(bytes)
            val id = "imp_${file.nameWithoutExtension}"
            val font = ImportedFont(id = id, name = file.nameWithoutExtension, path = file.absolutePath)
            appendImported(font)
            font
        }.getOrNull()
    }

    /**
     * Imports a font from an existing file path (e.g. a file picked via SAF).
     */
    suspend fun importFontFromPath(path: String): ImportedFont? {
        val src = File(path)
        if (!src.exists()) return null
        return importFont(src.name, src.readBytes())
    }

    /**
     * Imports a font from a `content://` URI (SAF picker result).
     */
    suspend fun importFontFromUri(context: Context, uri: Uri): ImportedFont? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                importFont("font_${System.nanoTime()}", input.readBytes())
            }
        }.getOrNull()
    }

    private suspend fun appendImported(font: ImportedFont) {
        val current = getImportedFonts().toMutableList()
        current.removeIf { it.id == font.id }
        current.add(font)
        val arr = JSONArray()
        current.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("path", it.path)
            })
        }
        com.example.reader.prefs.AppPrefs(context).setImportedFonts(arr.toString())
    }

    companion object {
        @Volatile
        private var instance: FontManager? = null

        fun getInstance(context: Context): FontManager =
            instance ?: synchronized(this) {
                instance ?: FontManager(context.applicationContext).also { instance = it }
            }
    }
}
