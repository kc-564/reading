package com.example.reader.ui.reader

import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.draw.paint
import com.example.reader.R
import com.example.reader.db.HighlightEntity
import com.example.reader.engine.BitmapCache
import com.example.reader.engine.BitmapPool
import com.example.reader.engine.PageKey
import com.example.reader.engine.PageRange
import com.example.reader.engine.PageRenderer
import com.example.reader.engine.ReaderPagination
import com.example.reader.engine.ReaderStyleConfig
import com.example.reader.feature.animation.pageAnimation
import com.example.reader.feature.clickzone.ClickZoneHandler
import com.example.reader.feature.fonts.FontManager
import com.example.reader.ui.theme.ClickZoneAction
import com.example.reader.ui.theme.readerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.comparisons.maxOf
import kotlin.comparisons.minOf

/**
 * Core reader surface: a pre-baked Bitmap pager + tap zones + volume keys + RTL + animation.
 *
 * Rendering model (v1.2 — native, smooth):
 * Each page is pre-rendered to an [android.graphics.Bitmap] by [PageRenderer] (which uses
 * Android's native [android.text.StaticLayout]) and the pager flips by blitting those bitmaps
 * (GPU-composited). Because the per-page text is measured exactly once and cached as an image,
 * turning a page is a pure image swap — no Compose `Text` re-measurement per frame — which
 * removes the jank the previous `TextMeasurer` + `BasicText` approach caused.
 *
 * Bitmap lifecycle (P0-1 / T01): a single [BitmapCache] (wrapping `LruCache<PageKey,Bitmap>` +
 * [BitmapPool]) is the *only* owner of baked bitmaps, bounded by a device-tiered memory budget
 * (§7.2). The Compose `StateMap<Int, ImageBitmap>` is just an observable *view*; when the cache
 * evicts/recycles a page (window trim or [android.content.ComponentCallbacks2.onTrimMemory])
 * the view entry is detached before any draw, so a recycled bitmap is never painted.
 *
 * Sliding sensitivity: [HorizontalPager.userScrollEnabled] is disabled and paging is driven by a
 * horizontal drag detector — a drag past [MIN_SWIPE] (≈15–20% of the screen width) flips one
 * page with a smooth `animateScrollToPage`. Tapping still drives the click zones.
 *
 * v1.1: [onPanelToggle] is called when the user taps a non-page-flip zone (NONE action)
 * or the OPEN_MENU zone, toggling the reader panels (top bar + bottom bar) visibility.
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
    onPanelToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val fontManager = remember { FontManager.getInstance(context) }
    val resolvedFont = remember(styleConfig.fontFamily) { fontManager.resolve(styleConfig.fontFamily) }
    val textStyle = remember(styleConfig, resolvedFont, density) {
        styleConfig.toTextStyle(density, resolvedFont)
    }
    val themeColors = remember(styleConfig.themeMode) { readerColors(styleConfig.themeMode) }

    // Native typeface used by the StaticLayout-backed renderer (replaces the Compose TextMeasurer).
    // resolveTypefaceWithFallback ensures imported fonts missing a script (e.g. CJK) still fall
    // back to the system chain instead of rendering tofu (P1-4 / T05).
    val typeface = remember(styleConfig.fontFamily) { fontManager.resolveTypefaceWithFallback(styleConfig.fontFamily) }
    // Paint is shared by pagination (character ranges) and baking (bitmap); it is only ever
    // read, never mutated, so it is safe to reuse across background render threads.
    val textColor = if (styleConfig.textureKey == "wood") Color.White else themeColors.onBackground
    val textColorArgb = textColor.toArgb()
    val paint = remember(styleConfig, typeface, textColorArgb, density) {
        PageRenderer.buildTextPaint(styleConfig, textColorArgb, density, typeface)
    }
    // Background fill for each baked page. Transparent in texture modes so the paper/wood/linen
    // texture drawn behind the pager shows through; opaque theme colour otherwise.
    val bgColorArgb = if (styleConfig.textureKey != "none") {
        Color.Transparent.toArgb()
    } else {
        themeColors.background.toArgb()
    }

    // Background texture (F02). On the dark "wood" texture, force light text for readability.
    val textureKey = styleConfig.textureKey
    val texturePainter = when (textureKey) {
        "paper" -> painterResource(R.drawable.texture_paper)
        "wood" -> painterResource(R.drawable.texture_wood)
        "linen" -> painterResource(R.drawable.texture_linen)
        else -> null
    }

    val pages = state.globalPages
    val pagesState = rememberUpdatedState(pages)
    val globalPageState = rememberUpdatedState(state.currentGlobalPage)

    // ── Bitmap cache (P0-1 / T01) ── the single source of truth for baked page bitmaps.
    val bitmapCache = remember(context, viewModel) {
        BitmapCache(context.applicationContext, viewModel.bitmapCacheBudgetBytes)
    }
    DisposableEffect(bitmapCache) {
        onDispose { bitmapCache.dispose() }
    }

    // Pre-rendered page bitmaps, keyed by global page index. ONLY an observable view of the
    // cache; evicted/recycled entries are detached here before any draw (see bakeWindow).
    val bitmaps = remember { mutableStateMapOf<Int, ImageBitmap>() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx().toInt() }
        val maxHeightPx = with(density) { maxHeight.toPx().toInt() }
        val marginPx = styleConfig.pageMarginPx
        val verticalPx = with(density) { 8.dp.toPx().toInt() }
        // Inner text area (excludes page margins + vertical padding). This is the width/height
        // passed to BOTH pagination and rendering so line breaks are identical → no clipped pages.
        val contentWidthPx = (maxWidthPx - 2 * marginPx).coerceAtLeast(1)
        val contentHeightPx = (maxHeightPx - 2 * verticalPx).coerceAtLeast(1)

        // Immediate neighbourhood kept baked; BEYOND this the LRU trims.
        val window = 2
        // Idle pre-render look-ahead (P1-5 / T05): warm ±PRELOAD pages into the cache.
        val preload = 4

        // Bakes a single page's bitmap off the main thread, then publishes it on the UI thread
        // (SnapshotState writes must happen on the main thread).
        suspend fun bakeOne(index: Int) {
            val ps = pagesState.value
            if (index < 0 || index >= ps.size) return
            if (bitmaps.containsKey(index)) return
            val gen = state.paginationVersion
            val key = PageKey(gen, index)
            val gp = ps[index]
            val chapter = state.chapters.getOrNull(gp.chapterIndex) ?: return
            // Render from the chapter's shared display spannable (indent / spacing / rich styles)
            // so the baked bitmap matches pagination exactly; fall back to plain text if the
            // display spannable for this chapter is not yet available.
            val display = state.chapterLayouts[gp.chapterIndex] ?: chapter.getContent()
            val rendered = withContext(Dispatchers.Default) {
                bitmapCache.get(key) ?: run {
                    val bmp = PageRenderer.renderPageBitmap(
                        text = display,
                        range = PageRange(gp.charStart, gp.charEnd),
                        paint = paint,
                        pageWidthPx = contentWidthPx,
                        pageHeightPx = contentHeightPx,
                        bgColor = bgColorArgb,
                        cfg = styleConfig
                    )
                    bitmapCache.put(key, bmp)
                    bmp
                }
            }
            withContext(Dispatchers.Main.immediate) {
                bitmaps[index] = rendered.asImageBitmap()
            }
        }

        // Bakes the [window] pages around [center] and trims the cache to bounds, detaching any
        // recycled bitmap from the observable view so it is never drawn.
        suspend fun bakeWindow(center: Int) {
            val ps = pagesState.value
            if (ps.isEmpty()) return
            val from = maxOf(0, center - window)
            val to = minOf(ps.lastIndex, center + window)
            for (i in from..to) bakeOne(i)
            // Recycle everything outside ±(window*3) — keeps the idle pre-render warm while the
            // LRU budget bounds memory on very long books.
            bitmapCache.evictOutsideWindow(center, window * 3)
            withContext(Dispatchers.Main.immediate) {
                // Detach + recycle every bitmap the cache evicted, then drop any stale view entry.
                bitmapCache.drainEvicted().forEach { (idx, bmp) ->
                    bitmaps.remove(idx)
                    BitmapPool.release(bmp)
                }
                val cached = bitmapCache.cachedIndices(state.paginationVersion)
                bitmaps.keys.filter { it !in cached }.forEach { bitmaps.remove(it) }
            }
        }

        // (Re)paginate when chapters / style / size change. Incremental: each chapter is pushed
        // to the screen the moment its pages are ready (no waiting for the whole book), so the
        // first screen appears within ~1-2s and the rest paginate in the background.
        LaunchedEffect(state.chapters, styleConfig, contentWidthPx, contentHeightPx) {
            if (state.chapters.isNotEmpty() && contentWidthPx > 0 && contentHeightPx > 0) {
                val paginationToken = viewModel.beginIncrementalPagination()
                withContext(Dispatchers.Default) {
                    ReaderPagination().paginateBookIncremental(
                        chapters = state.chapters,
                        style = textStyle,
                        maxWidthPx = contentWidthPx,
                        maxHeightPx = contentHeightPx,
                        cfg = styleConfig,
                        density = density,
                        typeface = typeface,
                        cache = viewModel.layoutCache,
                        bookId = viewModel.bookId,
                        fingerprint = viewModel.fingerprint(),
                        onChapterReady = { chapterIndex, pages, display ->
                            if (viewModel.isActivePagination(paginationToken)) {
                                viewModel.appendChapterPages(chapterIndex, pages, display)
                            }
                        }
                    )
                }
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
                val gp = ps[pagerState.currentPage]
                viewModel.saveProgress(gp.chapterIndex, gp.charStart)
            }
        }

        // Eager bake when the reader becomes ready or the style / size changes: clear the stale
        // bitmaps AND the cache (old pagination generation → stale), then bake the resume
        // position (plus chapter 0) so the first screen is instant.
        LaunchedEffect(state.styleConfig, contentWidthPx, contentHeightPx) {
            bitmaps.clear()
            bitmapCache.clear()
            bitmapCache.drainEvicted().forEach { (_, bmp) -> BitmapPool.release(bmp) }
            val target = globalPageState.value.coerceIn(0, (pagesState.value.lastIndex).coerceAtLeast(0))
            bakeWindow(target)
            if (target != 0) bakeWindow(0)
        }

        // As chapters stream in (incremental pagination bumps paginationVersion per chapter), keep
        // the resume position's window baked — without clearing the cache.
        LaunchedEffect(state.paginationVersion) {
            val target = globalPageState.value.coerceIn(0, (pagesState.value.lastIndex).coerceAtLeast(0))
            bakeWindow(target)
        }

        // When the visible page changes (flip / jump), bake its neighbourhood. After the page
        // settles with no further scroll, idle pre-render the wider ±preload window into the LRU
        // (P1-5 / T05). The delay is cancelled by rapid flips, so it naturally fires only on rest.
        LaunchedEffect(pagerState.currentPage) {
            val center = pagerState.currentPage
            bakeWindow(center)
            delay(280)
            if (pagerState.currentPage == center) {
                val ps = pagesState.value
                if (ps.isNotEmpty()) {
                    val from = maxOf(0, center - preload)
                    val to = minOf(ps.lastIndex, center + preload)
                    for (i in from..to) bakeOne(i)
                }
            }
        }

        val rtl = styleConfig.rtl
        // "正在排版…" mask stays up until the first chapter's first page is baked.
        val showMask = pages.isEmpty() || bitmaps[0] == null

        fun handleZone(zone: ClickZoneAction) {
            when (zone) {
                ClickZoneAction.PREVIOUS_PAGE -> coroutineScope.launch {
                    pagerState.scrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                }

                ClickZoneAction.NEXT_PAGE -> coroutineScope.launch {
                    pagerState.scrollToPage(
                        (pagerState.currentPage + 1).coerceAtMost(pagesState.value.lastIndex.coerceAtLeast(0))
                    )
                }

                // v1.1: OPEN_MENU now toggles the reader panels instead of opening settings.
                // NONE also toggles panels for zones that aren't mapped to any specific action.
                ClickZoneAction.OPEN_MENU -> onPanelToggle()
                ClickZoneAction.OPEN_TOC -> onOpenToc()
                ClickZoneAction.NONE -> onPanelToggle()
            }
        }

        CompositionLocalProvider(
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (texturePainter != null) Modifier.paint(texturePainter)
                        else Modifier.background(themeColors.background)
                    )
                    // Tap zones (resolved from the tap position).
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val tapZone = ClickZoneHandler.zoneFromOffset(offset.x / w, offset.y / h)
                            val action = ClickZoneHandler.resolve(tapZone, styleConfig.clickZones)
                            handleZone(action)
                        }
                    }
                    // Horizontal swipe-to-flip. A short drag (>= MIN_SWIPE) flips exactly one page
                    // with a smooth animation. userScrollEnabled=false on the pager means this
                    // detector fully owns horizontal gestures.
                    .pointerInput(Unit) {
                        var totalDx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDx = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDx += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                val minSwipePx = with(density) { MIN_SWIPE.toPx() }
                                if (abs(totalDx) >= minSwipePx) {
                                    val ps = pagesState.value
                                    if (ps.isNotEmpty()) {
                                        val cur = pagerState.currentPage
                                        val max = ps.lastIndex
                                        val target = if (totalDx < 0f) {
                                            (cur + 1).coerceAtMost(max)
                                        } else {
                                            (cur - 1).coerceAtLeast(0)
                                        }
                                        if (target != cur) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(target) }
                                        }
                                    }
                                }
                                totalDx = 0f
                            },
                            onDragCancel = { totalDx = 0f }
                        )
                    }
            ) {
                if (showMask) {
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
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false
                    ) { page ->
                        val gp = pages.getOrNull(page) ?: return@HorizontalPager
                        val bmp = bitmaps[page]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pageAnimation(styleConfig.pageAnimation, pagerState, page)
                                .padding(horizontal = marginPx.dp, vertical = verticalPx.dp)
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // Lightweight placeholder so a late page never flashes black.
                                Box(Modifier.fillMaxSize().background(themeColors.background))
                            }
                        }
                        // Ensure this page and its neighbours are baked when it is composed.
                        LaunchedEffect(page) { bakeWindow(page) }
                    }
                }
            }
        }
    }
}

/** Minimum horizontal drag (dp) that triggers a page flip. ≈15–20% of a typical phone width. */
private val MIN_SWIPE = 24.dp
