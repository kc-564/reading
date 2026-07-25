package com.example.reader.engine

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.reader.ui.theme.ClickZoneConfig
import com.example.reader.ui.theme.FontFamilyKey
import com.example.reader.ui.theme.PageAnimationMode
import com.example.reader.ui.theme.ReadingMode
import com.example.reader.ui.theme.ThemeMode
import com.example.reader.ui.theme.toKey

/**
 * Single source of truth for reader typography / layout parameters.
 *
 * The **same** [TextStyle] produced by [toTextStyle] must be used for both measuring
 * (in [LayoutEngine]) and rendering (in [com.example.reader.ui.reader.ReaderPage]) so
 * that line-break points never diverge when the user changes fonts or font size.
 *
 * [layoutHash] contains **only** fields that affect pagination. [ThemeMode] is excluded
 * (themes change colors, not layout) so a theme switch does not invalidate the layout cache.
 */
data class ReaderStyleConfig(
    val fontScale: Float = 1.0f,
    val lineSpacing: Float = 1.6f,
    val paragraphSpacingPx: Int = 8,
    val letterSpacing: Float = 0.5f,
    val pageMarginPx: Int = 16,
    val alignment: TextAlign = TextAlign.Start,
    val firstLineIndentPx: Int = 0,
    val fontFamily: FontFamilyKey = FontFamilyKey.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val brightness: Float = -1f,
    val pageAnimation: PageAnimationMode = PageAnimationMode.SMOOTH,
    val readingMode: ReadingMode = ReadingMode.PAGED,
    val rtl: Boolean = false,
    val clickZones: ClickZoneConfig = ClickZoneConfig(),
    val textureKey: String = "none"
) {
    /**
     * Builds the unique [TextStyle] shared by measurement and rendering.
     *
     * @param density            Density used to resolve `sp` units (use [LocalDensity.current]).
     * @param resolvedFontFamily Optional already-resolved [FontFamily] for the configured
     *                           [fontFamily] (custom imports need a [android.graphics.Typeface]).
     *                           Falls back to [FontFamily.Default] when null.
     */
    fun toTextStyle(density: Density, resolvedFontFamily: FontFamily? = null): TextStyle {
        val base: TextUnit = 18.sp * fontScale
        return TextStyle(
            fontFamily = resolvedFontFamily ?: FontFamily.Default,
            fontSize = base,
            lineHeight = base * lineSpacing,
            letterSpacing = letterSpacing.sp,
            textAlign = alignment
        )
    }

    /**
     * Stable hash of only the fields that influence pagination. Excludes [themeMode],
     * [brightness], [pageAnimation], [readingMode], [rtl], [clickZones] and [textureKey].
     */
    fun layoutHash(): String = buildString {
        append("fs=").append(fontScale).append(';')
        append("ls=").append(lineSpacing).append(';')
        append("ps=").append(paragraphSpacingPx).append(';')
        append("lt=").append(letterSpacing).append(';')
        append("pm=").append(pageMarginPx).append(';')
        append("al=").append(alignment.toKey()).append(';')
        append("fi=").append(firstLineIndentPx).append(';')
        append("ff=").append(fontFamily.storageKey)
    }

    companion object {
        /** Base font size (sp) before [fontScale] is applied. */
        const val BASE_FONT_SP = 18
    }
}
