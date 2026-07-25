package com.example.reader.feature.animation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.reader.ui.theme.PageAnimationMode

/**
 * Page-turn animation helpers (E03). No third-party libraries are used.
 *
 * - [PageAnimationMode.SMOOTH]: the pager's native slide (no extra modifier).
 * - [PageAnimationMode.NONE]: same as smooth (the BOM pager has no snap toggle); the default
 *   slide remains. Documented as a known limitation.
 * - [PageAnimationMode.FLIP3D]: a `rotationY` 3D-flip approximation during the transition.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pageAnimation(
    mode: PageAnimationMode,
    pagerState: PagerState,
    page: Int
): Modifier {
    if (mode != PageAnimationMode.FLIP3D) return this
    return this.graphicsLayer {
        val pageOffset = pagerState.currentPageOffsetFraction
        val direction = if (page <= pagerState.currentPage) 1f else -1f
        rotationY = pageOffset * 30f * direction
        cameraDistance = 12f * density
    }
}
