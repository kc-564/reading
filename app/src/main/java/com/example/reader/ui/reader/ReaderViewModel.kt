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
import com.example.reader.engine.GlobalPage
import com.example.reader.engine.LayoutCache
import com.example.reader.engine.ReaderPagination
import com.example.reader.engine.ReaderStyleConfig
import com.example.reader.feature.search.SearchEngine
import com.example.reader.feature.search.SearchHit
import com.example.reader.feature.stats.ReadingStatsTracker
import com.example.reader.parser.EncodingDetector
import com.example.reader.parser.LruEncodingCache
import com.example.reader.parser.TxtParser
import com.example.reader.prefs.AppPrefs
import com.example.reader.util.FileFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * ViewModel for the reader screen.
 *
 * Responsibilities (Phase 2):
 * - Load + parse + restore progress.
 * - Hold the [ReaderPagination] result and re-paginate when layout config changes while
 *   **preserving the character offset** (never jumps back to page 0).
 * - Full-book percentage / global page mapping.
 * - Bookmarks, full-text search, and reading-stats heartbeat.
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

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val defaultPref = prefs.defaultEncoding.first()
                val encoding: Charset = encodingCache.getOrPut(bookPath, defaultPref) {
                    EncodingDetector.detect(bookPath, defaultPref) ?: StandardCharsets.UTF_8
                }

                val chapters = parser.parse(bookPath, encoding)
                if (chapters.isEmpty()) {
                    _uiState.value = ReaderUiState.Error("无法读取文件或文件为空")
                    return@launch
                }

                val totalChars = chapters.sumOf { it.totalCharCount.toLong() }
                val existingBook = repository.getBook(bookPath)
                val (chapterIndex, charOffset) = if (existingBook != null) {
                    existingBook.lastChapterIndex.coerceIn(0, chapters.size - 1) to existingBook.lastCharOffset
                } else {
                    val file = File(bookPath)
                    val newBook = BookEntity(
                        bookId = bookPath,
                        filePath = bookPath,
                        fileName = file.name,
                        title = file.nameWithoutExtension,
                        format = "txt",
                        sizeBytes = file.length(),
                        encoding = encoding.name(),
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
                    encoding = encoding.name(),
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
        val targetPage = if (lastPagination != null) {
            pagination.globalPageOf(lastPagination, chapterIndex, charOffset)
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
