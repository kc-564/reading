package com.example.reader.engine

import android.text.Layout

/**
 * Stable, cache-affecting signature of every typography parameter that changes how text is
 * laid out / broken into pages.
 *
 * Any field change produces a different [hash], which flows into [com.example.reader.engine.LayoutCache]
 * and therefore invalidates (misses) the cached pagination for that book — a font-size or margin
 * tweak automatically re-paginates once and then caches correctly.
 *
 * **Excluded on purpose** (they change colour/animation only, never line breaks): theme mode,
 * brightness, page animation, reading mode, rtl, click zones, texture key. Including them would
 * wrongly trigger a full-book re-pagination on, say, a theme switch.
 *
 * [FORMATTING_VERSION_ID] is bumped only on an *algorithm-level* pagination change (e.g. the
 * StaticLayout→StaticLayout migration, or a change to how paragraph spacing is encoded). Bumping
 * it invalidates every prior cached range at once (FBReader-style self-healing).
 */
data class LayoutSignature(
    val fontScale: Float,
    val lineSpacing: Float,
    val paragraphSpacingPx: Int,
    val letterSpacing: Float,
    val pageMarginPx: Int,
    val alignmentKey: String,
    val firstLineIndentPx: Int,
    val fontFamilyKey: String,
    // ── P1-1 / P1-3 extensions (still layout-affecting) ──
    val breakStrategy: Int = Layout.BREAK_STRATEGY_HIGH_QUALITY,
    val hyphenation: Int = Layout.HYPHENATION_FREQUENCY_NORMAL,
    val richVersion: Int = 1
) {
    /**
     * Human-readable, `;`-separated key (mirrors the old [com.example.reader.engine.ReaderStyleConfig.layoutHash]
     * format). Two signatures hash equal iff every layout-affecting field is equal.
     */
    fun hash(): String = buildString {
        append("fs=").append(fontScale).append(';')
        append("ls=").append(lineSpacing).append(';')
        append("ps=").append(paragraphSpacingPx).append(';')
        append("lt=").append(letterSpacing).append(';')
        append("pm=").append(pageMarginPx).append(';')
        append("al=").append(alignmentKey).append(';')
        append("fi=").append(firstLineIndentPx).append(';')
        append("ff=").append(fontFamilyKey).append(';')
        append("bs=").append(breakStrategy).append(';')
        append("hy=").append(hyphenation).append(';')
        append("rv=").append(richVersion)
    }

    companion object {
        /** Algorithm-level version; bump only when pagination logic itself changes. */
        const val FORMATTING_VERSION_ID = 1
    }
}
