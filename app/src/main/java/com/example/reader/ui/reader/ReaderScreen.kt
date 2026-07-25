package com.example.reader.ui.reader

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reader.db.AppDatabase
import com.example.reader.feature.highlight.HighlightManager
import kotlinx.coroutines.launch

/**
 * Reader screen orchestrator (v1.1). The old Scaffold + persistent bars have been replaced by
 * a Column layout with [AnimatedVisibility]-wrapped [ReaderTopBar] and [ReaderBottomBar].
 *
 * Panels are hidden by default; tapping the center (or non-page-flip zones) toggles them.
 * The [ModalNavigationDrawer] still hosts the TOC on the left side.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    bookPath: String,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModel.provideFactory(application, bookPath)
    )
    val uiState by viewModel.uiState.collectAsState()
    val styleConfig by viewModel.styleConfig.collectAsState()

    when (val state = uiState) {
        is ReaderUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
                    Text("正在加载书籍…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is ReaderUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "加载失败: ${state.message}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        is ReaderUiState.Ready -> {
            val pagerState = rememberPagerState(pageCount = { state.globalPages.size })
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            val highlightManager = remember { HighlightManager(AppDatabase.getInstance(context)) }
            val highlights by highlightManager.getFlow(bookPath).collectAsState(initial = emptyList())
            val highlightsByChapter = remember(highlights) { highlights.groupBy { it.chapterIndex } }

            var sheet: ReaderSheet? by remember { mutableStateOf(null) }

            // ── Collapsible panel state ──
            var panelsVisible by remember { mutableStateOf(false) }

            val bookTitle = state.chapters.getOrNull(state.currentChapterIndex)?.title ?: ""

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    TocDrawer(
                        chapters = state.chapters,
                        currentChapterIndex = state.currentChapterIndex,
                        bookId = bookPath,
                        onChapterClick = { idx ->
                            viewModel.jumpTo(idx, 0)
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Top bar (animated) ──
                    AnimatedVisibility(
                        visible = panelsVisible,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it }
                    ) {
                        ReaderTopBar(
                            bookTitle = bookTitle,
                            onBack = onNavigateBack,
                            onSearch = { sheet = ReaderSheet.Search },
                            onBookmarks = { sheet = ReaderSheet.Bookmark }
                        )
                    }

                    // ── Reading content ──
                    ReaderContent(
                        state = state,
                        viewModel = viewModel,
                        styleConfig = styleConfig,
                        highlightsByChapter = highlightsByChapter,
                        pagerState = pagerState,
                        onOpenMenu = { /* panels handle this now */ },
                        onOpenToc = { scope.launch { drawerState.open() } },
                        onPanelToggle = { panelsVisible = !panelsVisible },
                        modifier = Modifier.weight(1f)
                    )

                    // ── Bottom bar (animated) ──
                    AnimatedVisibility(
                        visible = panelsVisible,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        Column {
                            ReaderBottomBar(
                                currentChapterIndex = state.currentChapterIndex,
                                totalChapters = state.chapters.size,
                                onToc = { scope.launch { drawerState.open() } }
                            )
                            ReaderStatusBar(state = state, pagerState = pagerState)
                        }
                    }
                }
            }

            // ── Bottom sheets (unchanged) ──
            when (sheet) {
                ReaderSheet.Settings -> SettingsSheet(onDismiss = { sheet = null })
                ReaderSheet.Bookmark -> BookmarkSheet(
                    state = state,
                    viewModel = viewModel,
                    onJump = { ci, off -> viewModel.jumpTo(ci, off) },
                    onDismiss = { sheet = null }
                )

                ReaderSheet.Search -> SearchSheet(
                    viewModel = viewModel,
                    onJump = { ci, off -> viewModel.jumpTo(ci, off) },
                    onDismiss = { sheet = null }
                )

                ReaderSheet.Highlight -> HighlightSheet(
                    state = state,
                    bookId = bookPath,
                    onDismiss = { sheet = null }
                )

                null -> { /* no sheet */ }
            }
        }
    }
}

/** Which bottom sheet is currently open in the reader. */
private sealed interface ReaderSheet {
    object Settings : ReaderSheet
    object Bookmark : ReaderSheet
    object Search : ReaderSheet
    object Highlight : ReaderSheet
}
