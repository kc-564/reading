package com.example.reader.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import com.example.reader.db.HighlightEntity
import com.example.reader.ui.theme.ReaderThemeColors
import com.example.reader.util.ParagraphSplitter

/**
 * Renders a single paginated page.
 *
 * Paragraphs are laid out individually (one [Text] per paragraph) with exact pixel spacing
 * ([paragraphSpacingPx]) and first-line indent ([firstLineIndentPx]) between them, matching the
 * measurement performed by [com.example.reader.engine.LayoutEngine] so line breaks never diverge.
 *
 * Highlight ranges that overlap this page are painted as background spans. The whole surface is
 * wrapped in a semantics node for TalkBack (E04).
 *
 * @param chapterText  Full chapter text (used to recover paragraph boundaries from [charStart]/[charEnd]).
 * @param charStart    Inclusive character offset of this page within [chapterText].
 * @param charEnd      Exclusive character offset of this page within [chapterText].
 */
@Composable
fun ReaderPage(
    chapterText: String,
    charStart: Int,
    charEnd: Int,
    textStyle: TextStyle,
    themeColors: ReaderThemeColors,
    rtl: Boolean,
    paragraphSpacingPx: Int,
    firstLineIndentPx: Int,
    highlights: List<HighlightEntity> = emptyList(),
    modifier: Modifier = Modifier
) {
    val safeStart = charStart.coerceAtLeast(0)
    val safeEnd = charEnd.coerceAtMost(chapterText.length).coerceAtLeast(safeStart)
    val pageText = if (safeEnd > safeStart) chapterText.substring(safeStart, safeEnd) else ""
    val paragraphs = if (pageText.isNotEmpty()) ParagraphSplitter.split(pageText) else emptyList()

    val indent = if (firstLineIndentPx > 0) TextIndent(firstLine = firstLineIndentPx.dp) else TextIndent.None

    val innerModifier = modifier
        .fillMaxSize()
        .padding(vertical = 4.dp)
        .semantics { contentDescription = "阅读正文，共 ${paragraphs.size} 段" }

    if (paragraphs.isEmpty()) {
        Box(modifier = innerModifier)
        return
    }

    Column(modifier = innerModifier) {
        paragraphs.forEachIndexed { index, paragraph ->
            val absStart = safeStart + paragraph.start
            val absEnd = safeStart + paragraph.end
            val annotated: AnnotatedString = buildAnnotatedString {
                append(paragraph.text)
                for (hl in highlights) {
                    val overlapStart = maxOf(hl.startChar, absStart)
                    val overlapEnd = minOf(hl.endChar, absEnd)
                    if (overlapStart < overlapEnd) {
                        val localStart = (overlapStart - absStart).coerceAtLeast(0)
                        val localEnd = (overlapEnd - absStart).coerceAtLeast(0)
                        addStyle(SpanStyle(background = Color(hl.colorArgb)), localStart, localEnd)
                    }
                }
            }
            Text(
                text = annotated,
                style = textStyle.copy(textIndent = indent, color = themeColors.onBackground),
                modifier = Modifier.fillMaxWidth()
            )
            if (index < paragraphs.lastIndex && paragraphSpacingPx > 0) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(paragraphSpacingPx.dp))
            }
        }
    }
}
