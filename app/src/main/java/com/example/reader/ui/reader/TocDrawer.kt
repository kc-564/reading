package com.example.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.reader.parser.Chapter

/**
 * Left navigation drawer showing the chapter list for quick jump (v1.1).
 * TOC rule toggles have been migrated to the global Settings screen.
 */
@Composable
fun TocDrawer(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    bookId: String,
    onChapterClick: (Int) -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("目录", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(chapters) { idx, ch ->
                    Text(
                        text = ch.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterClick(idx) }
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (idx == currentChapterIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}
