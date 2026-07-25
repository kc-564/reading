package com.example.reader.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Top bar shown when the reader panels are visible (v1.1).
 *
 * Displays a back arrow, the book title (from the current chapter), and an
 * overflow menu with entries for in-book search and bookmarks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    bookTitle: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = TopAppBarDefaults.mediumScrollElevation
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { overflowExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多操作"
                )
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("本书搜索") },
                    onClick = {
                        overflowExpanded = false
                        onSearch()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("书签") },
                    onClick = {
                        overflowExpanded = false
                        onBookmarks()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Bookmark, contentDescription = null)
                    }
                )
            }
        }
    }
}
