package com.example.reader.ui.reader

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reader.engine.LayoutEngine
import com.example.reader.engine.PageInfo

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
    val textMeasurer = rememberTextMeasurer()
    val layoutEngine = remember { LayoutEngine(textMeasurer) }

    when (val state = uiState) {
        is ReaderUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "正在加载书籍...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is ReaderUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "加载失败: ${state.message}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

        is ReaderUiState.Ready -> {
            ReaderContent(
                state = state,
                layoutEngine = layoutEngine,
                onNavigateBack = onNavigateBack,
                onProgressChanged = { chapterIndex, charOffset ->
                    viewModel.saveProgress(chapterIndex, charOffset)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState.Ready,
    layoutEngine: LayoutEngine,
    onNavigateBack: () -> Unit,
    onProgressChanged: (Int, Int) -> Unit
) {
    val currentChapter = state.chapters[state.currentChapterIndex]
    val chapterText = currentChapter.getContent()
    val textStyle = MaterialTheme.typography.bodyLarge

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }

        val pages: List<PageInfo> = remember(chapterText, maxWidthPx, maxHeightPx, textStyle) {
            layoutEngine.paginate(chapterText, textStyle, maxWidthPx, maxHeightPx)
        }

        // Find the page index to restore based on currentCharOffset
        val pageToRestore = remember(state.currentCharOffset, pages.size) {
            if (pages.isEmpty()) 0
            else {
                val idx = pages.indexOfFirst { page ->
                    state.currentCharOffset in page.startCharIndex until page.endCharIndex
                }
                if (idx >= 0) idx else 0
            }
        }

        val pagerState = rememberPagerState(pageCount = { pages.size })

        // Restore to the saved position after layout is ready
        LaunchedEffect(pageToRestore, pages.size) {
            if (pages.isNotEmpty() && pageToRestore < pages.size) {
                withFrameNanos { }
                pagerState.scrollToPage(pageToRestore)
            }
        }

        // Save progress whenever the user flips to a new page
        LaunchedEffect(pagerState.currentPage) {
            if (pages.isNotEmpty() && pagerState.currentPage < pages.size) {
                val page = pages[pagerState.currentPage]
                onProgressChanged(state.currentChapterIndex, page.startCharIndex)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentChapter.title,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                // Reading status bar
                val currentPercent = if (pages.isNotEmpty()) {
                    (pagerState.currentPage + 1).toFloat() / pages.size.toFloat() * 100f
                } else 0f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentChapter.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${pagerState.currentPage + 1}/${pages.size} · ${"%.0f".format(currentPercent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) { paddingValues ->
            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "空章节",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) { page ->
                    ReaderPage(
                        text = pages[page].text,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
