package com.example.reader.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reader.db.BookEntity

/**
 * Profile / personal screen (v1.1).
 *
 * Shows recent reading history (deduplicated), quick entries to reading stats
 * and system settings. The WiFi transfer and reading stats that were in the
 * Shelf top bar have been migrated here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToReader: (String) -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val viewModel: ProfileViewModel = viewModel()
    val recentBooks by viewModel.recentDistinctBooks.collectAsStateWithLifecycle(emptyList())
    val totalReadingMinutes by viewModel.totalReadingMinutes.collectAsStateWithLifecycle(0L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // ── Recent reading (deduplicated) ──
            if (recentBooks.isNotEmpty()) {
                Text("最近阅读", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(recentBooks, key = { it.bookId }) { book ->
                        Box(modifier = Modifier.width(120.dp)) {
                            RecentBookItem(
                                book = book,
                                onClick = { onNavigateToReader(book.bookId) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
            }

            // ── Reading stats entry ──
            ProfileEntryRow(
                label = "阅读统计",
                subtitle = "累计 ${totalReadingMinutes} 分钟",
                onClick = onNavigateToStats
            )
            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // ── System settings entry ──
            ProfileEntryRow(
                label = "系统设置",
                subtitle = "主题、字体、目录规则等",
                onClick = onNavigateToSettings
            )
        }
    }
}

@Composable
private fun ProfileEntryRow(
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null
        )
    }
}

/**
 * Simplified recent-book item used in the profile LazyRow.
 * Shows a coloured placeholder tile with the book title.
 */
@Composable
private fun RecentBookItem(
    book: BookEntity,
    onClick: () -> Unit
) {
    val placeholderColor = Color.hsv(
        (book.title.hashCode().rem(360).let { if (it < 0) it + 360 else it }).toFloat(),
        0.45f,
        0.55f
    )

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(placeholderColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = book.title.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
