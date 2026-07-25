package com.example.reader.ui.reader

import com.example.reader.parser.Chapter

/**
 * Sealed interface representing the possible states of the reader screen.
 */
sealed interface ReaderUiState {
    /** The book is being loaded and parsed. */
    data object Loading : ReaderUiState

    /** An error occurred while loading the book. */
    data class Error(val message: String) : ReaderUiState

    /** The book has been successfully loaded and is ready for reading. */
    data class Ready(
        val chapters: List<Chapter>,
        val currentChapterIndex: Int,
        val currentCharOffset: Int,
        val totalChars: Long,
        val encoding: String
    ) : ReaderUiState
}
