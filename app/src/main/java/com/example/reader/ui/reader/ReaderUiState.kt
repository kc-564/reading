package com.example.reader.ui.reader

import com.example.reader.engine.GlobalPage
import com.example.reader.engine.ReaderStyleConfig
import com.example.reader.parser.Chapter

/**
 * Sealed interface representing the possible states of the reader screen.
 */
sealed interface ReaderUiState {
    /** The book is being loaded and parsed. */
    data object Loading : ReaderUiState

    /** An error occurred while loading the book. */
    data class Error(val message: String) : ReaderUiState

    /**
     * The book has been successfully loaded and is ready for reading.
     *
     * Pagination now spans the whole book: [globalPages] is a flat, cross-chapter list used
     * directly by the pager; [totalPages]/[currentGlobalPage]/[globalPercent] describe the
     * book-wide position. Changing layout parameters re-paginates but preserves
     * [currentCharOffset] (so [currentGlobalPage] is recomputed, never reset to 0).
     */
    data class Ready(
        val chapters: List<Chapter>,
        val currentChapterIndex: Int,
        val currentCharOffset: Int,
        val totalChars: Long,
        val encoding: String,
        val styleConfig: ReaderStyleConfig,
        val perChapterPageCounts: List<Int>,
        val totalPages: Int,
        val currentGlobalPage: Int,
        val globalPercent: Float,
        val globalPages: List<GlobalPage>,
        /**
         * Per-chapter display [CharSequence] (the spannable carrying indent / spacing / rich
         * styles) used to bake each page's bitmap. Keyed by chapter index. Rendering reads this
         * instead of re-deriving the text so pagination and baking stay pixel-identical.
         */
        val chapterLayouts: Map<Int, CharSequence> = emptyMap(),
        /** Bumped every time pagination is (re)computed so the pager can re-target. */
        val paginationVersion: Int = 0
    ) : ReaderUiState
}
