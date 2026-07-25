package com.example.reader.ui.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.reader.data.ReadFilter
import com.example.reader.data.SortMode

/**
 * Sort + read/unread filter controls for the shelf (C04 / F05).
 *
 * Two chip rows: one for [SortMode] (recent / title / progress / added) and one for
 * [ReadFilter] (all / read / unread).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilterBar(
    sortMode: SortMode,
    readFilter: ReadFilter,
    onSortChanged: (SortMode) -> Unit,
    onFilterChanged: (ReadFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("排序", style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth())
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(SortMode.entries.toList()) { mode ->
                FilterChip(
                    selected = mode == sortMode,
                    onClick = { onSortChanged(mode) },
                    label = { Text(sortLabel(mode)) }
                )
            }
        }

        Text("筛选", style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth())
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ReadFilter.entries.toList()) { filter ->
                FilterChip(
                    selected = filter == readFilter,
                    onClick = { onFilterChanged(filter) },
                    label = { Text(filterLabel(filter)) }
                )
            }
        }
    }
}

private fun sortLabel(mode: SortMode): String = when (mode) {
    SortMode.LAST_OPENED -> "最近打开"
    SortMode.TITLE -> "书名"
    SortMode.PROGRESS -> "阅读进度"
    SortMode.ADDED -> "添加时间"
}

private fun filterLabel(filter: ReadFilter): String = when (filter) {
    ReadFilter.ALL -> "全部"
    ReadFilter.READ -> "已读"
    ReadFilter.UNREAD -> "未读"
}
