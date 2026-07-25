package com.example.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.reader.db.BookmarkEntity
import com.example.reader.feature.export.ExportManager
import kotlinx.coroutines.launch
import android.widget.Toast

/**
 * Bottom sheet listing bookmarks for the current book. Supports adding the *current* page as a
 * bookmark, jumping to a bookmark, and deleting one (D01).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkSheet(
    state: ReaderUiState.Ready,
    viewModel: ReaderViewModel,
    onJump: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val bookmarks by viewModel.bookmarksFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exportManager = remember { ExportManager(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("书签", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(8.dp))
            OutlinedButton(onClick = {
                val page = state.globalPages.getOrNull(state.currentGlobalPage)
                if (page != null) {
                    val preview = page.text.replace('\n', ' ').take(40)
                    viewModel.addBookmark(page.chapterIndex, page.localPageIndex, page.charStart, preview)
                }
            }) { Text("添加当前页书签") }
            Spacer(Modifier.padding(8.dp))
            LazyColumn {
                items(bookmarks) { bm: BookmarkEntity ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(bm.chapterIndex, bm.charOffset) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                bm.previewText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "第 ${bm.chapterIndex + 1} 章",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.removeBookmark(bm.bookmarkId) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除书签")
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val path = exportManager.exportBookmarks(viewModel.bookId, bookmarks)
                        Toast.makeText(
                            context,
                            if (path != null) "已导出书签" else "导出失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导出书签") }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
        }
    }
}
