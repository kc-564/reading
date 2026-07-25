package com.example.reader.ui.shelf

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.reader.db.BookEntity

/**
 * 3:4 book cover tile.
 *
 * - When [BookEntity.coverUri] points to a decodable local image it is shown cropped to fill.
 * - Otherwise a deterministic colour derived from the title is used with the title's first
 *   character as a placeholder glyph.
 * - The title is always rendered beneath the cover (F05 "书名常显").
 *
 * @param onOpen   Open the reader for this book.
 * @param onMenu   Open the per-book overflow menu (edit / delete).
 */
@Composable
fun BookCover(
    book: BookEntity,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val placeholderColor = remember(book.bookId) { colorFromTitle(book.title) }
    val bitmap = remember(book.coverUri) { book.coverUri?.let { BitmapFactory.decodeFile(it) } }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(6.dp))
                .background(placeholderColor)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = book.title.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
            // Overflow menu anchored to the top-end of the cover.
            Box(modifier = Modifier.fillMaxSize().padding(2.dp), contentAlignment = Alignment.TopEnd) {
                IconButton(onClick = onMenu, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!book.author.isNullOrBlank()) {
            Text(
                text = book.author!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Deterministic, pleasant colour derived from a title string. */
private fun colorFromTitle(title: String): Color {
    val hue = (title.hashCode().rem(360).let { if (it < 0) it + 360 else it }).toFloat()
    return Color.hsv(hue, 0.45f, 0.55f)
}
