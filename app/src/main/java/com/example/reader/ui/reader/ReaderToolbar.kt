package com.example.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Bottom toolbar with quick actions that open the reader sheets / TOC drawer (all bottom-anchored
 * per the shared convention). Each button carries a TalkBack description (E04).
 */
@Composable
fun ReaderToolbar(
    onToc: () -> Unit,
    onSettings: () -> Unit,
    onBookmark: () -> Unit,
    onSearch: () -> Unit,
    onHighlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToc, modifier = Modifier.semantics { contentDescription = "打开目录" }) {
                Icon(Icons.Filled.Menu, contentDescription = null)
            }
            IconButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = "打开设置" }) {
                Icon(Icons.Filled.Settings, contentDescription = null)
            }
            IconButton(onClick = onBookmark, modifier = Modifier.semantics { contentDescription = "书签" }) {
                Icon(Icons.Filled.Bookmarks, contentDescription = null)
            }
            IconButton(onClick = onSearch, modifier = Modifier.semantics { contentDescription = "搜索" }) {
                Icon(Icons.Filled.Search, contentDescription = null)
            }
            IconButton(onClick = onHighlight, modifier = Modifier.semantics { contentDescription = "高亮" }) {
                Icon(Icons.Filled.Highlight, contentDescription = null)
            }
        }
    }
}
