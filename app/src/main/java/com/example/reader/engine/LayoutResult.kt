package com.example.reader.engine

/**
 * A single paginated page produced by [LayoutEngine].
 *
 * @property startCharIndex Inclusive character offset in the *chapter* text where the page starts.
 * @property endCharIndex   Exclusive character offset in the chapter text where the page ends.
 * @property text           Visible text of the page (paragraphs joined by `\n`).
 * @property chapterIndex   Index of the chapter this page belongs to.
 * @property paragraphIndex Index of the first paragraph rendered on this page.
 */
data class PageInfo(
    val startCharIndex: Int,
    val endCharIndex: Int,
    val text: String,
    val chapterIndex: Int = 0,
    val paragraphIndex: Int = 0
)

/**
 * All pages of a single chapter.
 */
data class ChapterPages(
    val chapterIndex: Int,
    val pages: List<PageInfo>
)

/**
 * Result of paginating one chapter: its pages plus the **display** [CharSequence] (the
 * [android.text.SpannableString] carrying indent / spacing / rich-style spans) that both
 * pagination and rendering consumed. Keeping the same instance guarantees pixel-identical pages.
 */
data class ChapterRender(
    val chapterIndex: Int,
    val pages: List<PageInfo>,
    val display: CharSequence
)

/**
 * Book-level pagination result.
 *
 * @property chapters           Per-chapter page lists.
 * @property perChapterPageCounts Number of pages in each chapter (index-aligned with [chapters]).
 * @property totalPages          Total number of pages across the whole book.
 */
data class BookPagination(
    val chapters: List<ChapterPages>,
    val perChapterPageCounts: List<Int>,
    val totalPages: Int
)

/**
 * A flat, ready-to-render page across the whole book (cross-chapter).
 */
data class GlobalPage(
    val globalIndex: Int,
    val chapterIndex: Int,
    val localPageIndex: Int,
    val charStart: Int,
    val charEnd: Int,
    val text: String
)
