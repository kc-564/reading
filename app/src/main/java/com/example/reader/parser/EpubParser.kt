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
                    val safeId = hashBookId(bookId)
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
                val richText = htmlToRichText(html)
                if (richText.text.isBlank()) continue

                // Try to extract a title from HTML <title> or first <h1>-<h6>
                val chapterTitle = extractTitle(html) ?: itemref.idref
                chapters.add(
                    EpubChapter(
                        title = chapterTitle,
                        richText = richText,
                        charCount = richText.text.length
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
        var coverMetaId: String? = null
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
                        inMetadata && parser.name == "meta" -> {
                            val nameAttr = parser.getAttributeValue(null, "name")
                            val contentAttr = parser.getAttributeValue(null, "content")
                            if (nameAttr == "cover" && contentAttr != null) {
                                coverMetaId = contentAttr
                            }
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

        // EPUB2: <meta name="cover" content="itemId"> points to a manifest item.
        if (coverHref == null && coverMetaId != null) {
            coverHref = manifest[coverMetaId]?.href
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

    // ── HTML → rich text ──

    /**
     * Produces the plain-text body of an HTML chapter (block structure → paragraph breaks),
     * mirroring the previous `htmlToPlainText` output exactly. The [RichText.text] field is
     * defined to be byte-identical to this output so the layout cache, pagination keys,
     * bookmarks and search remain valid across the rich-text migration.
     */
    private fun htmlToPlainText(html: String): String {
        var text = html
        // Remove script/style blocks entirely.
        text = text
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        // Convert break tags into paragraph separators.
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        // Convert block-closing tags into paragraph separators (a paragraph boundary).
        text = text.replace(
            Regex("</(p|div|li|tr|blockquote|h[1-6]|pre|section|article)>", RegexOption.IGNORE_CASE),
            "\n"
        )
        // Convert block-opening tags into paragraph separators as well (helps <p>…</p>).
        text = text.replace(
            Regex("<(p|div|li|tr|blockquote|h[1-6]|pre|section|article)[^>]*>", RegexOption.IGNORE_CASE),
            "\n"
        )
        // Strip any remaining (inline) tags.
        text = text.replace(Regex("<[^>]*>"), " ")
        // Decode HTML entities (also collapses entity-wrapped whitespace reasonably).
        text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
        // Normalise whitespace: collapse runs of spaces/tabs, keep newlines, then collapse
        // 3+ newlines into a single blank line and trim.
        text = text.replace(Regex("[ \t]+"), " ")
        text = text.replace(Regex(" *\n *"), "\n")
        text = text.replace(Regex("\n{3,}"), "\n\n")
        return text.trim()
    }

    /**
     * Parses an HTML chapter into a [RichText]: the plain [RichText.text] (≡ [htmlToPlainText])
     * plus [SpanSpec]s locating headings / bold / italic runs.
     *
     * Offsets are recovered by normalising each tag's inner HTML with [htmlToPlainText] (so the
     * whitespace matches the canonical text) and locating that normalised substring within the
     * full plain text. This keeps styling best-effort while guaranteeing [RichText.text] identity.
     */
    private fun htmlToRichText(html: String): RichText {
        val plain = htmlToPlainText(html)
        val spans = computeSpans(html, plain)
        return RichText(plain, spans)
    }

    private fun computeSpans(html: String, plain: String): List<SpanSpec> {
        val spans = mutableListOf<SpanSpec>()

        // Headings h1..h6
        for (level in 1..6) {
            val regex = Regex("<h$level[^>]*>([\\s\\S]*?)</h$level>", RegexOption.IGNORE_CASE)
            regex.findAll(html).forEach { m ->
                val inner = htmlToPlainText(m.groupValues[1])
                if (inner.isBlank()) return@forEach
                val start = plain.indexOf(inner)
                if (start >= 0) spans.add(SpanSpec(start, start + inner.length, SpanType.valueOf("H$level")))
            }
        }

        // Strong / bold
        Regex("<(strong|b)[^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val inner = htmlToPlainText(m.groupValues[2])
            if (inner.isBlank()) return@forEach
            val start = plain.indexOf(inner)
            if (start >= 0) spans.add(SpanSpec(start, start + inner.length, SpanType.BOLD))
        }

        // Emphasis / italic
        Regex("<(em|i)[^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val inner = htmlToPlainText(m.groupValues[2])
            if (inner.isBlank()) return@forEach
            val start = plain.indexOf(inner)
            if (start >= 0) spans.add(SpanSpec(start, start + inner.length, SpanType.ITALIC))
        }

        return spans
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

    /**
     * Builds a filesystem-safe, fixed-length id for cover filenames from an arbitrary
     * book id (which may be a long file path). Uses SHA-256 hex to avoid over-long
     * filenames produced by URL-encoding full paths.
     */
    private fun hashBookId(bookId: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bookId.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

// ── Public data classes ──

    data class EpubMetadata(
        val title: String,
        val author: String?
    )

    /**
     * A single EPUB chapter.
     *
     * @property title     Chapter title.
     * @property richText  Rich-text payload (plain text + style spans). [RichText.text] is the
     *                     canonical plain body, byte-identical to the legacy plain-text output.
     * @property charCount Length of [RichText.text].
     */
    data class EpubChapter(
        val title: String,
        val richText: RichText,
        val charCount: Int
    )

    data class EpubResult(
        val metadata: EpubMetadata,
        val chapters: List<EpubChapter>,
        val coverPath: String?
    )
}
