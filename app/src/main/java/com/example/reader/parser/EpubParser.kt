package com.example.reader.parser

import android.content.Context
import android.text.Html
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile

/**
 * Lightweight EPUB parser using only Android built-in APIs
 * ([java.util.zip.ZipFile] + [org.xmlpull.v1.XmlPullParser]).
 *
 * Parses container.xml → OPF → metadata/manifest/spine, extracts plain-text
 * chapters, and optionally a cover image.
 */
object EpubParser {

    private const val TAG = "EpubParser"

    /** Container XML path inside every valid EPUB. */
    private const val CONTAINER_PATH = "META-INF/container.xml"

    /**
     * Parses an EPUB file and returns structured result.
     *
     * @param context Android context (for resolving filesDir for cover storage).
     * @param file    The EPUB file on disk.
     * @param bookId  Unique book identifier (used for cover filename).
     * @return [EpubResult] with parsed metadata, chapters, and optional cover path.
     */
    fun parse(context: Context, file: File, bookId: String): EpubResult {
        val zip = ZipFile(file)
        return try {
            // 1. Find OPF path from container.xml
            val opfPath = findOpfPath(zip)
                ?: throw IllegalArgumentException("EPUB container.xml missing or invalid rootfile")

            // 2. Resolve OPF base directory (OPF paths are relative to its own directory)
            val opfDir = File(opfPath).parent?.let { "$it/" } ?: ""

            // 3. Parse OPF
            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalArgumentException("OPF entry not found: $opfPath")
            val opfXml = zip.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).readText()
            val opfData = parseOpf(opfXml)

            // 4. Extract cover image
            var coverPath: String? = null
            val coverHref = opfData.coverHref
            if (coverHref != null) {
                val resolvedHref = resolveHref(opfDir, coverHref)
                val coverEntry = zip.getEntry(resolvedHref)
                if (coverEntry != null) {
                    val bytes = zip.getInputStream(coverEntry).readBytes()
                    val dir = File(context.filesDir, "cover")
                    if (!dir.exists()) dir.mkdirs()
                    val safeId = java.net.URLEncoder.encode(bookId, "UTF-8")
                    val coverFile = File(dir, "$safeId.png")
                    coverFile.writeBytes(bytes)
                    coverPath = coverFile.absolutePath
                }
            }

            // 5. Parse chapters from spine items
            val chapters = mutableListOf<EpubChapter>()
            for (itemref in opfData.spine) {
                val item = opfData.manifest[itemref.idref]
                if (item == null) {
                    Log.w(TAG, "Spine itemref idref=${itemref.idref} not found in manifest")
                    continue
                }
                val href = resolveHref(opfDir, item.href)
                val htmlEntry = zip.getEntry(href)
                if (htmlEntry == null) {
                    Log.w(TAG, "HTML entry not found: $href")
                    continue
                }
                val html = zip.getInputStream(htmlEntry).bufferedReader(Charsets.UTF_8).readText()
                val plainText = htmlToPlainText(html)
                if (plainText.isBlank()) continue

                // Try to extract a title from HTML <title> or first <h1>-<h6>
                val chapterTitle = extractTitle(html) ?: itemref.idref
                chapters.add(
                    EpubChapter(
                        title = chapterTitle,
                        content = plainText,
                        charCount = plainText.length
                    )
                )
            }

            if (chapters.isEmpty()) {
                Log.w(TAG, "No chapters parsed from EPUB")
            }

            EpubResult(
                metadata = EpubMetadata(
                    title = opfData.title.ifBlank { file.nameWithoutExtension },
                    author = opfData.author
                ),
                chapters = chapters,
                coverPath = coverPath
            )
        } finally {
            runCatching { zip.close() }
        }
    }

    // ── Container XML ──

    private fun findOpfPath(zip: ZipFile): String? {
        val entry = zip.getEntry(CONTAINER_PATH) ?: return null
        val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
        val parser = newPullParser(xml)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (fullPath != null) return fullPath
            }
        }
        return null
    }

    // ── OPF parsing ──

    private data class OpfData(
        val title: String,
        val author: String?,
        val coverHref: String?,
        val manifest: Map<String, ManifestItem>,
        val spine: List<SpineItemref>
    )

    private data class ManifestItem(val id: String, val href: String, val mediaType: String)
    private data class SpineItemref(val idref: String)

    private fun parseOpf(xml: String): OpfData {
        var title = ""
        var author: String? = null
        var coverHref: String? = null
        val manifest = mutableMapOf<String, ManifestItem>()
        val spine = mutableListOf<SpineItemref>()

        val parser = newPullParser(xml)
        var inMetadata = false
        var inManifest = false
        var inSpine = false
        var currentTag = ""

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when {
                        parser.name == "metadata" || parser.name.endsWith(":metadata") ->
                            inMetadata = true
                        parser.name == "manifest" -> inManifest = true
                        parser.name == "spine" -> inSpine = true
                        inMetadata && (parser.name == "title" || parser.name.endsWith(":title")) -> {
                            title = parser.nextText().trim()
                        }
                        inMetadata && (parser.name == "creator" || parser.name.endsWith(":creator")) -> {
                            author = parser.nextText().trim().takeIf { it.isNotBlank() }
                        }
                        inManifest && parser.name == "item" -> {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                            val properties = parser.getAttributeValue(null, "properties") ?: ""
                            manifest[id] = ManifestItem(id, href, mediaType)
                            // Detect cover image: id contains "cover" or properties="cover-image"
                            if (coverHref == null) {
                                val idLower = id.lowercase()
                                val propsLower = properties.lowercase()
                                if (idLower.contains("cover") || propsLower.contains("cover-image")) {
                                    coverHref = href
                                }
                            }
                        }
                        inSpine && parser.name == "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref") ?: ""
                            if (idref.isNotBlank()) {
                                spine.add(SpineItemref(idref))
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "metadata", "manifest", "spine" -> {
                            // Also handle namespaced endings
                        }
                    }
                    if (parser.name == "metadata" || parser.name.endsWith(":metadata"))
                        inMetadata = false
                    if (parser.name == "manifest") inManifest = false
                    if (parser.name == "spine") inSpine = false
                }
            }
        }

        // If no cover found by id/properties, try the first image in manifest
        if (coverHref == null) {
            coverHref = manifest.values
                .firstOrNull { it.mediaType.startsWith("image/") }?.href
        }

        // If title is still empty, try dc:title namespace variant
        if (title.isBlank()) {
            title = parseDcTitleFallback(xml)
        }

        return OpfData(title, author, coverHref, manifest, spine)
    }

    /** Fallback for dc:title when the simple element-name match didn't work. */
    private fun parseDcTitleFallback(xml: String): String {
        val parser = newPullParser(xml)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                val name = parser.name
                if (name == "dc:title" || name == "title") {
                    return parser.nextText().trim()
                }
            }
        }
        return ""
    }

    // ── HTML → plain text ──

    /**
     * Strips HTML tags and decodes entities to produce plain text.
     * Uses regex for tag removal + [android.text.Html] for entity decoding.
     */
    private fun htmlToPlainText(html: String): String {
        // Remove script/style blocks
        val noScripts = html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        // Strip all HTML tags
        val stripped = noScripts.replace(Regex("<[^>]*>"), " ")
        // Decode HTML entities
        return Html.fromHtml(stripped, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Extracts a title from HTML <title> or first heading tag. */
    private fun extractTitle(html: String): String? {
        // Try <title>
        val titleMatch = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
            .find(html)
        if (titleMatch != null) {
            val t = Html.fromHtml(titleMatch.groupValues[1], Html.FROM_HTML_MODE_LEGACY)
                .toString().trim()
            if (t.isNotBlank()) return t
        }
        // Try first <h1>-<h6>
        val headingMatch = Regex("<h[1-6][^>]*>([\\s\\S]*?)</h[1-6]>", RegexOption.IGNORE_CASE)
            .find(html)
        if (headingMatch != null) {
            val t = Html.fromHtml(headingMatch.groupValues[1], Html.FROM_HTML_MODE_LEGACY)
                .toString().trim()
            if (t.isNotBlank()) return t
        }
        return null
    }

    // ── Helpers ──

    private fun newPullParser(xml: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        return parser
    }

    /**
     * Resolves a relative href against an OPF directory.
     * e.g. opfDir="OEBPS/", href="Text/chapter1.xhtml" → "OEBPS/Text/chapter1.xhtml"
     */
    private fun resolveHref(opfDir: String, href: String): String {
        if (href.startsWith("/")) return href.removePrefix("/")
        // Handle "../" up-references
        var result = opfDir + href
        while (result.contains("/../")) {
            result = result.replace(Regex("[^/]+/\\.\\./"), "")
        }
        return result.replace("//", "/")
    }
}

// ── Public data classes ──

data class EpubMetadata(
    val title: String,
    val author: String?
)

data class EpubChapter(
    val title: String,
    val content: String,
    val charCount: Int
)

data class EpubResult(
    val metadata: EpubMetadata,
    val chapters: List<EpubChapter>,
    val coverPath: String?
)
