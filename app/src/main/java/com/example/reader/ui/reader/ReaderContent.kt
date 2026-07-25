package com.example.reader.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.paint
import androidx.compose.ui.input.key.KeyEvent
import com.example.reader.R
import com.example.reader.db.HighlightEntity
import com.example.reader.engine.GlobalPage
import com.example.reader.engine.ReaderPagination
import com.example.reader.engine.ReaderStyleConfig
import com.example.reader.feature.animation.pageAnimation
import com.example.reader.ui.theme.ClickZoneAction
import com.example.reader.feature.clickzone.ClickZoneHandler
import com.example.reader.feature.fonts.FontManager
import com.example.reader.ui.theme.ReaderThemeColors
import com.example.reader.ui.theme.readerColors
import kotlinx.coroutines.launch

/**
 * Core reader surface: paginated pager + tap zones + volume keys + RTL + animation.
 *
 * Pagination runs here (where the [androidx.compose.ui.text.TextMeasurer] lives) and is pushed
 * back into the ViewModel via [ReaderViewModel.applyPagination]. Changing the layout config
 * re-paginates while preserving the character offset, so the pager re-targets the same position
 * instead of jumping to page 0.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderContent(
    state: ReaderUiState.Ready,
    viewModel: ReaderViewModel,
    styleConfig: ReaderStyleConfig,
    highlightsByChapter: Map<Int, List<HighlightEntity>>,
    pagerState: PagerState,
    onOpenMenu: () -> Unit,
    onOpenToc: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope()
    val fontManager = remember { FontManager.getInstance(context) }
    val resolvedFont = remember(styleConfig.fontFamily) { fontManager.resolve(styleConfig.fontFamily) }
    val textStyle = remember(styleConfig, resolvedFont, density) {
        styleConfig.toTextStyle(density, resolvedFont)
    }
    val themeColors = remember(styleConfig.themeMode) { readerColors(styleConfig.themeMode) }

    // Background texture (F02). On the dark "wood" texture, force light text for readability.
    val textureKey = styleConfig.textureKey
    val texturePainter = when (textureKey) {
        "paper" -> painterResource(R.drawable.texture_paper)
        "wood" -> painterResource(R.drawable.texture_wood)
        "linen" -> painterResource(R.drawable.texture_linen)
        else -> null
    }
    val pageColors = if (textureKey == "wood") {
        themeColors.copy(onBackground = Color.White, titleColor = Color.White, secondary = Color.White)
    } else {
        themeColors
    }

    val pages = state.globalPages
    val pagesState = rememberUpdatedState(pages)
    val globalPageState = rememberUpdatedState(state.currentGlobalPage)
    val boxSize = remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx().toInt() }
        val maxHeightPx = with(density) { maxHeight.toPx().toInt() }

        // (Re)paginate when chapters / style / size change.
        LaunchedEffect(state.chapters, styleConfig, maxWidthPx, maxHeightPx) {
            if (state.chapters.isNotEmpty() && maxWidthPx > 0 && maxHeightPx > 0) {
                val bp = ReaderPagination().paginateBook(
                    chapters = state.chapters,
                    style = textStyle,
                    maxWidthPx = maxWidthPx,
                    maxHeightPx = maxHeightPx,
                    cfg = styleConfig,
                    measurer = measurer,
                    cache = viewModel.layoutCache,
                    bookId = viewModel.bookId,
                    fingerprint = viewModel.fingerprint()
                )
                viewModel.applyPagination(bp)
            }
        }

        // Re-target the pager whenever pagination is recomputed (initial load, config change, jump).
        LaunchedEffect(state.paginationVersion) {
            val ps = pagesState.value
            if (ps.isNotEmpty()) {
                pagerState.scrollToPage(globalPageState.value.coerceIn(0, ps.lastIndex))
            }
        }

        // Persist progress on every settled page.
        LaunchedEffect(pagerState.currentPage) {
            val ps = pagesState.value
            if (ps.isNotEmpty() && pagerState.currentPage in ps.indices) {
                val gp: GlobalPage = ps[pagerState.currentPage]
                viewModel.saveProgress(gp.chapterIndex, gp.charStart)
            }
        }

        val rtl = styleConfig.rtl
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (texturePainter != null) Modifier.paint(texturePainter)
                        else Modifier.background(themeColors.background)
                    )
                    .onSizeChanged { boxSize.value = it }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val w = boxSize.value.width.toFloat().coerceAtLeast(1f)
                            val h = boxSize.value.height.toFloat().coerceAtLeast(1f)
                            val zone = ClickZoneHandler.zoneFromOffset(offset.x / w, offset.y / h)
                            when (ClickZoneHandler.resolve(zone, styleConfig.clickZones)) {
                                ClickZoneAction.PREVIOUS_PAGE -> coroutineScope.launch {
                                    pagerState.scrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                }

                                ClickZoneAction.NEXT_PAGE -> coroutineScope.launch {
                                    pagerState.scrollToPage(
                                        (pagerState.currentPage + 1).coerceAtMost(pagesState.value.lastIndex.coerceAtLeast(0))
                                    )
                                }

                                ClickZoneAction.OPEN_MENU -> onOpenMenu()
                                ClickZoneAction.OPEN_TOC -> onOpenToc()
                                ClickZoneAction.NONE -> {}
                            }
                        }
                    }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                        when (keyEvent.key) {
                            Key.VolumeUp -> {
                                coroutineScope.launch {
                                    pagerState.scrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                }
                                true
                            }

                            Key.VolumeDown -> {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(
                                        (pagerState.currentPage + 1).coerceAtMost(pagesState.value.lastIndex.coerceAtLeast(0))
                                    )
                                }
                                true
                            }

                            else -> false
                        }
                    }
            ) {
                if (pages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "正在排版…",
                            color = themeColors.onBackground,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val gp = pages.getOrNull(page) ?: return@HorizontalPager
                        val chapter = state.chapters.getOrNull(gp.chapterIndex) ?: return@HorizontalPager
                        ReaderPage(
                            chapterText = chapter.getContent(),
                            charStart = gp.charStart,
                            charEnd = gp.charEnd,
                            textStyle = textStyle,
                            themeColors = pageColors,
                            rtl = rtl,
                            paragraphSpacingPx = styleConfig.paragraphSpacingPx,
                            firstLineIndentPx = styleConfig.firstLineIndentPx,
                            highlights = highlightsByChapter[gp.chapterIndex] ?: emptyList(),
                            modifier = Modifier
                                .fillMaxSize()
                                .pageAnimation(styleConfig.pageAnimation, pagerState, page)
                                .padding(horizontal = styleConfig.pageMarginPx.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
