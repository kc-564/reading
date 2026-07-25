package com.example.reader.ui.reader

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A single page of text within the reader, rendered with text selection support.
 *
 * @param text     The text content to display on this page.
 * @param modifier Modifier for the outer container.
 */
@Composable
fun ReaderPage(text: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
