package com.example.reader.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Bottom status bar showing the current chapter title and the book-wide page position
 * (current / total · percent). TalkBack descriptions are provided for accessibility (E04).
 */
@Composable
fun ReaderStatusBar(
    state: ReaderUiState.Ready,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val currentPage = pagerState.currentPage.coerceAtLeast(0)
    val total = state.totalPages
    val percent = if (total > 0) (currentPage + 1).toFloat() / total * 100f else 0f
    val chapterIndex = state.globalPages.getOrNull(currentPage)?.chapterIndex ?: state.currentChapterIndex
    val chapterTitle = state.chapters.getOrNull(chapterIndex)?.title ?: ""

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "当前章节：$chapterTitle" }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${currentPage + 1}/$total · ${"%.0f".format(percent)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics {
                    contentDescription = "第 ${currentPage + 1} 页，共 $total 页，进度 ${"%.0f".format(percent)} 百分比"
                }
            )
        }
    }
}
