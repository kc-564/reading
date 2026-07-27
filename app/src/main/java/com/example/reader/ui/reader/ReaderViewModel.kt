package com.example.reader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.reader.data.BookRepository
import com.example.reader.data.BookmarkRepository
import com.example.reader.data.StatsRepository
import com.example.reader.db.AppDatabase
import com.example.reader.db.BookEntity
import com.example.reader.engine.BookPagination
import com.example.reader.engine.ChapterPages
import com.example.reader.engine.GlobalPage
import com.example.reader.engine.PageInfo
import com.example.reader.engine.LayoutCache
import com.example.reader.engine.ReaderPagination
import com.example.reader.engine.ReaderStyleConfig
import com.example.reader.feature.search.SearchEngine
import com.example.reader.feature.search.SearchHit
import com.example.reader.feature.stats.ReadingStatsTracker
import com.example.reader.parser.Chapter
import com.example.reader.parser.EncodingDetector
import com.example.reader.parser.EpubParser
import com.example.reader.parser.LruEncodingCache
import com.example.reader.parser.TxtParser
import com.example.reader.parser.sanitizeBookTitle
import com.example.reader.prefs.AppPrefs
import com.example.reader.util.FileFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * ViewModel for the reader screen.
 *
 * Responsibilities (Phase 2 → v1.1):
 * - Load + parse + restore progress.
 * - Hold the [ReaderPagination] result and re-paginate when layout config changes while
 *   **preserving the character offset** (never jumps back to page 0).
 * - Full-book percentage / global page mapping.
 * - Bookmarks, full-text search, and reading-stats heartbeat.
 *
 * v1.1 additions:
 * - [applyStyleConfig] writes layout parameters to [AppPrefs], which flows back and
 *   triggers re-pagination.
 */
class ReaderViewModel(
    application: Application,
    private val bookPath: String
) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = BookRepository(db)
    private val bookmarkRepository = BookmarkRepository(db)
    private val statsRepository = StatsRepository(db)
    private val parser = TxtParser()
    private val encodingCache = LruEncodingCache()
    private val prefs = AppPrefs(application)
    private val pagination = ReaderPagination()
    private val _layoutCache = LayoutCache(db.layoutCacheDao())

    /** Exposed so the Composable's pagination pass can hit the layout cache. */
    val layoutCache: LayoutCache get() = _layoutCache
    private val searchEngine = SearchEngine()
    private val statsTracker = ReadingStatsTracker(statsRepository, viewModelScope)

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /** Live reader style configuration from preferences. */
    val styleConfig: StateFlow<ReaderStyleConfig> = prefs.styleConfigFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderStyleConfig()
    )

    /** Bookmarks for the current book. */
    fun bookmarksFlow() = bookmarkRepository.getBookmarksFlow(bookPath)

    private var cachedBook: BookEntity? = null

    /** Last computed pagination result, used to resolve global pages on jump. */
    private var lastPagination: BookPagination? = null

    /** Accumulates per-chapter pages during an incremental pagination pass. */
    private val _accChapters = mutableListOf<ChapterPages>()

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingBook = repository.getBook(bookPath)

                // Determine the book format: prefer the stored format, else infer from extension.
                val format = if (existingBook != null) {
                    existingBook.format
                } else {
                    val file = File(bookPath)
                    if (file.extension.equals("epub", true)) "epub" else file.extension.lowercase().ifBlank { "txt" }
                }

                // Parse chapters according to the book format.
                var chapters: List<Chapter> = emptyList()
                var encodingName = StandardCharsets.UTF_8.name()
                var epubTitle: String? = null
                var epubAuthor: String? = null
                if (format == "epub") {
                    val epubResult = EpubParser.parse(getApplication(), File(bookPath), bookPath)
                    chapters = epubResult.chapters.map { ch ->
                        val lines = ch.content.split('\n')
                        Chapter(
                            title = ch.title,
                            startLineIndex = 0,
                            lineCount = lines.size,
                            contentLines = lines
                        )
                    }
                    epubTitle = epubResult.metadata.title
                    epubAuthor = epubResult.metadata.author
                } else {
                    val defaultPref = prefs.defaultEncoding.first()
                    val encoding: Charset = encodingCache.getOrPut(bookPath, defaultPref) {
                        EncodingDetector.detect(bookPath, defaultPref) ?: StandardCharsets.UTF_8
                    }
                    chapters = parser.parse(bookPath, encoding)
                    encodingName = encoding.name()
                }

                if (chapters.isEmpty()) {
                    _uiState.value = ReaderUiState.Error("无法读取文件或文件为空")
                    return@launch
                }

                val totalChars = chapters.sumOf { it.totalCharCount.toLong() }
                val (chapterIndex, charOffset) = if (existingBook != null) {
                    existingBook.lastChapterIndex.coerceIn(0, chapters.size - 1) to existingBook.lastCharOffset
                } else {
                    val file = File(bookPath)
                    val newBook = BookEntity(
                        bookId = bookPath,
                        filePath = bookPath,
                        fileName = file.name,
                        title = sanitizeBookTitle(
                            if (format == "epub" && !epubTitle.isNullOrBlank()) epubTitle!!
                            else file.nameWithoutExtension
                        ),
                        author = if (format == "epub") epubAuthor else null,
                        format = format,
                        sizeBytes = file.length(),
                        encoding = encodingName,
                        lastOpenedAt = System.currentTimeMillis(),
                        totalChapters = chapters.size,
                        totalChars = totalChars
                    )
                    repository.upsertBook(newBook)
                    0 to 0
                }
                cachedBook = existingBook ?: repository.getBook(bookPath)
                repository.setRead(bookPath, true)

                val initialPercent = repository.calculatePercent(chapterIndex, charOffset, chapters, totalChars)
                repository.recordOpen(bookPath, initialPercent)

                statsTracker.start(bookPath)

                _uiState.value = ReaderUiState.Ready(
                    chapters = chapters,
                    currentChapterIndex = chapterIndex,
                    currentCharOffset = charOffset,
                    totalChars = totalChars,
                    encoding = encodingName,
                    styleConfig = ReaderStyleConfig(),
                    perChapterPageCounts = emptyList(),
                    totalPages = 0,
                    currentGlobalPage = 0,
                    globalPercent = initialPercent,
                    globalPages = emptyList(),
                    paginationVersion = 0
                )
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error("加载失败: ${e.message}")
            }
        }
    }

    /**
     * Applies a freshly computed [BookPagination]. Called from the Composable once the
     * [androidx.compose.ui.text.TextMeasurer] is available. Preserves the retained
     * `(currentChapterIndex, currentCharOffset)` so the global page is recomputed rather
     * than reset to 0.
     */
    fun applyPagination(bp: BookPagination) {
        val state = _uiState.value
        if (state !is ReaderUiState.Ready) return
        val cfg = _styleConfigValue()
        val gPage = pagination.globalPageOf(bp, state.currentChapterIndex, state.currentCharOffset)
        val percent = pagination.globalPercentOf(bp, state.currentChapterIndex, state.currentCharOffset, state.totalChars)
        val flat: List<GlobalPage> = pagination.flatten(bp)
        lastPagination = bp
        _uiState.value = state.copy(
            styleConfig = cfg,
            perChapterPageCounts = bp.perChapterPageCounts,
            totalPages = bp.totalPages,
            currentGlobalPage = gPage,
            globalPercent = percent,
            globalPages = flat,
            paginationVersion = state.paginationVersion + 1
        )
    }

    /**
     * Monotonic token identifying the current incremental pagination pass. A superseded pass
     * (e.g. a style change that re-triggered pagination before the previous one finished) is
     * detected via [isActivePagination] so its late [appendChapterPages] callbacks are dropped
     * instead of interleaving with the latest pass and corrupting the accumulated pages.
     */
    @Volatile
    private var _paginationToken: Int = 0

    /**
     * Begins an incremental (chapter-by-chapter) pagination pass.
     *
     * Returns a pass token; callers must drop any [appendChapterPages] callback whose token no
     * longer matches [isActivePagination], so a superseded re-pagination cannot interleave.
     *
     * Clears the accumulated chapter buffer. On a *re-pagination* (e.g. a style change) we
     * re-seed it from the previous [BookPagination] so already-rendered pages stay visible and
     * are overwritten chapter-by-chapter as the new layout arrives — avoiding a blank flash.
     * On first open [lastPagination] is null, so the "正在排版…" mask stays up until chapter 0
     * lands.
     */
    fun beginIncrementalPagination(): Int {
        _accChapters.clear()
        lastPagination?.let { _accChapters.addAll(it.chapters) }
        return ++_paginationToken
    }

    /** True if [token] is the currently active pagination pass. */
    fun isActivePagination(token: Int): Boolean = token == _paginationToken

    /**
     * Receives one chapter's freshly computed pages from
     * [ReaderPagination.paginateBookIncremental] and folds them into the live UI state so the
     * pager can render immediately without waiting for the rest of the book.
     *
     * This is invoked from [kotlinx.coroutines.Dispatchers.Default] (the pagination coroutine).
     * The [BookPagination] maths run on that background thread; the
     * [androidx.compose.runtime.State] write is dispatched to [kotlinx.coroutines.Dispatchers.Main]
     * so Compose state is only mutated on the main thread.
     */
    fun appendChapterPages(chapterIndex: Int, pages: List<PageInfo>) {
        val incoming = ChapterPages(chapterIndex, pages)
        val existing = _accChapters.indexOfFirst { it.chapterIndex == chapterIndex }
        if (existing >= 0) _accChapters[existing] = incoming else _accChapters.add(incoming)

        // Build the partial BookPagination from everything paginated so far.
        val chapterPagesSnapshot = ArrayList(_accChapters)
        val perChapterPageCounts = chapterPagesSnapshot.map { it.pages.size }
        val totalPages = perChapterPageCounts.sum()
        val bp = BookPagination(chapterPagesSnapshot, perChapterPageCounts, totalPages)

        val state = _uiState.value
        if (state !is ReaderUiState.Ready) return
        val cfg = _styleConfigValue()
        val gPage = pagination.globalPageOf(bp, state.currentChapterIndex, state.currentCharOffset)
        val percent = pagination.globalPercentOf(
            bp, state.currentChapterIndex, state.currentCharOffset, state.totalChars
        )
        val flat: List<GlobalPage> = pagination.flatten(bp)
        lastPagination = bp

        viewModelScope.launch(Dispatchers.Main.immediate) {
            val s = _uiState.value
            if (s !is ReaderUiState.Ready) return@launch
            _uiState.value = s.copy(
                styleConfig = cfg,
                perChapterPageCounts = bp.perChapterPageCounts,
                totalPages = bp.totalPages,
                currentGlobalPage = gPage,
                globalPercent = percent,
                globalPages = flat,
                paginationVersion = s.paginationVersion + 1
            )
        }
    }

    /** Current style config value (read outside composition). */
    private fun _styleConfigValue(): ReaderStyleConfig = _styleConfigCache

    private var _styleConfigCache: ReaderStyleConfig = ReaderStyleConfig()

    /** Mirror the live prefs flow into a plain field so [applyPagination] can read it. */
    init {
        viewModelScope.launch {
            prefs.styleConfigFlow.collect { _styleConfigCache = it }
        }
    }

    /**
     * Applies a new [ReaderStyleConfig] by writing its individual fields to [AppPrefs].
     * The prefs flow back to [styleConfigFlow] which triggers re-pagination preserving
     * the reading position.
     */
    fun applyStyleConfig(config: ReaderStyleConfig) {
        viewModelScope.launch {
            prefs.setFontScale(config.fontScale)
            prefs.setLineSpacing(config.lineSpacing)
            prefs.setParagraphSpacing(config.paragraphSpacingPx)
            prefs.setLetterSpacing(config.letterSpacing)
            prefs.setPageMargin(config.pageMarginPx)
            prefs.setAlignment(
                when (config.alignment) {
                    androidx.compose.ui.text.style.TextAlign.Start -> "start"
                    androidx.compose.ui.text.style.TextAlign.Center -> "center"
                    androidx.compose.ui.text.style.TextAlign.End -> "end"
                    androidx.compose.ui.text.style.TextAlign.Justify -> "justify"
                    else -> "start"
                }
            )
            prefs.setFirstLineIndent(config.firstLineIndentPx)
            prefs.setFontFamily(config.fontFamily.storageKey)
            prefs.setThemeMode(config.themeMode.storageKey)
            prefs.setBrightness(config.brightness)
            prefs.setPageAnimation(config.pageAnimation.storageKey)
            prefs.setRtl(config.rtl)
            prefs.setClickZones(com.example.reader.ui.theme.ClickZoneConfig.toKey(config.clickZones))
            prefs.setTextureKey(config.textureKey)
        }
    }

    /**
     * Saves current reading progress (called on every page flip). Also ticks the stats timer.
     */
    fun saveProgress(chapterIndex: Int, charOffset: Int) {
        val state = _uiState.value
        if (state !is ReaderUiState.Ready) return
        _uiState.value = state.copy(currentChapterIndex = chapterIndex, currentCharOffset = charOffset)
        viewModelScope.launch(Dispatchers.IO) {
            val percent = repository.calculatePercent(chapterIndex, charOffset, state.chapters, state.totalChars)
            repository.updateProgress(bookPath, System.currentTimeMillis(), percent, chapterIndex, charOffset)
            repository.recordOpen(bookPath, percent)
            statsTracker.tick()
        }
    }

    /**
     * Jumps to a chapter + char offset and re-targets the pager to the matching global page.
     */
    fun jumpTo(chapterIndex: Int, charOffset: Int) {
        val state = _uiState.value
        if (state !is ReaderUiState.Ready) return
        val p = lastPagination
        val targetPage = if (p != null) {
            pagination.globalPageOf(p, chapterIndex, charOffset)
        } else state.currentGlobalPage
        _uiState.value = state.copy(
            currentChapterIndex = chapterIndex,
            currentCharOffset = charOffset,
            currentGlobalPage = targetPage,
            paginationVersion = state.paginationVersion + 1
        )
        viewModelScope.launch(Dispatchers.IO) {
            val percent = repository.calculatePercent(chapterIndex, charOffset, state.chapters, state.totalChars)
            repository.updateProgress(bookPath, System.currentTimeMillis(), percent, chapterIndex, charOffset)
        }
    }

    /** Full-text search across all chapters. */
    fun search(query: String): List<SearchHit> {
        val state = _uiState.value
        if (state !is ReaderUiState.Ready || query.isBlank()) return emptyList()
        return searchEngine.search(state.chapters, query)
    }

    /** Adds a bookmark at the current position. */
    fun addBookmark(chapterIndex: Int, pageIndex: Int, charOffset: Int, previewText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepository.addBookmark(
                com.example.reader.db.BookmarkEntity(
                    bookId = bookPath,
                    chapterIndex = chapterIndex,
                    pageIndex = pageIndex,
                    charOffset = charOffset,
                    previewText = previewText,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { bookmarkRepository.removeBookmark(id) }
    }

    /** Exposes the layout-cache key builder so the Composable can reuse the same key. */
    fun buildCacheKey(fingerprint: String, cfg: ReaderStyleConfig, w: Int, h: Int): String =
        _layoutCache.buildKey(fingerprint, cfg, w, h)

    fun fingerprint(): String = FileFingerprint.compute(bookPath)

    /** Book id (= file path) used as the layout-cache / bookmark / stats key. */
    val bookId: String get() = bookPath

    override fun onCleared() {
        super.onCleared()
        statsTracker.flush()
    }

    companion object {
        fun provideFactory(application: Application, bookPath: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReaderViewModel(application, bookPath) as T
            }
    }
}
