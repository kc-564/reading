package com.example.reader.ui.shelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.reader.db.BookEntity

/**
 * Dialog to edit a book's metadata — title / author / cover path (F05).
 *
 * @param onSave Called with the edited values (author / coverUri are null when left blank).
 */
@Composable
fun BookMetaEditor(
    book: BookEntity,
    onSave: (title: String, author: String?, coverUri: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author ?: "") }
    var coverUri by remember { mutableStateOf(book.coverUri ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title,
                        author.takeIf { it.isNotBlank() },
                        coverUri.takeIf { it.isNotBlank() }
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("书籍信息") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("书名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("作者") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                OutlinedTextField(
                    value = coverUri,
                    onValueChange = { coverUri = it },
                    label = { Text("封面路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
