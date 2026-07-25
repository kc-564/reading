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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
 * Shows recent reading history (deduplicated) with BookCover thumbnails,
 * a reading-stats summary row (cumulative / weekly / monthly), and
 * quick entries to the full stats view and system settings.
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
    val totalStats by viewModel.totalStats.collectAsStateWithLifecycle(ReadingStats())
    val weeklyStats by viewModel.weeklyStats.collectAsStateWithLifecycle(ReadingStats())
    val monthlyStats by viewModel.monthlyStats.collectAsStateWithLifecycle(ReadingStats())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人中心") },
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
                        RecentBookCard(
                            book = book,
                            onClick = { onNavigateToReader(book.bookId) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
            }

            // ── Reading stats summary ──
            Text("阅读统计", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip(label = "累计阅读", value = formatMinutes(totalStats.totalMinutes))
                StatChip(label = "本周", value = formatMinutes(weeklyStats.totalMinutes))
                StatChip(label = "本月", value = formatMinutes(monthlyStats.totalMinutes))
            }
            Spacer(Modifier.height(12.dp))
            ProfileEntryRow(
                label = "查看详细统计",
                subtitle = "${totalStats.bookCount} 本书 · ${formatMinutes(totalStats.totalMinutes)}",
                onClick = onNavigateToStats
            )

            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            // ── System settings entry ──
            ProfileEntryRow(
                label = "系统设置",
                subtitle = "主题、字体、目录规则等",
                onClick = onNavigateToSettings
            )
        }
    }
}

// ── Private composables ──

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
 * Card used in the profile LazyRow for a recent book.
 * Shows cover thumbnail (coloured placeholder or image), title, and progress bar.
 */
@Composable
private fun RecentBookCard(
    book: BookEntity,
    onClick: () -> Unit
) {
    val placeholderColor = Color.hsv(
        (book.title.hashCode().rem(360).let { if (it < 0) it + 360 else it }).toFloat(),
        0.45f,
        0.55f
    )

    Card(
        modifier = Modifier.width(130.dp),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Cover area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
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
            Spacer(Modifier.height(6.dp))
            // Title
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            // Progress
            LinearProgressIndicator(
                progress = { book.progressPercent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${(book.progressPercent * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Formats total minutes into a human-readable string. */
private fun formatMinutes(minutes: Long): String {
    return when {
        minutes < 60 -> "${minutes}分钟"
        minutes < 1440 -> "%.1f小时".format(minutes / 60.0)
        else -> "%.1f天".format(minutes / 1440.0)
    }
}
