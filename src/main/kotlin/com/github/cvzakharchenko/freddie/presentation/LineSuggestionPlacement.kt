package com.github.cvzakharchenko.freddie.presentation

internal data class LineSuggestionPlacement(
    val renderOffset: Int,
    val showAbove: Boolean,
)

internal object LineSuggestionPlacementPlanner {
    fun create(
        documentText: CharSequence,
        editableStartOffset: Int,
        changedBlock: ChangedBlock,
    ): LineSuggestionPlacement {
        val anchorOffset =
            (editableStartOffset + changedBlock.anchorOffsetInOriginal)
                .coerceIn(0, documentText.length)
        val changedOriginalLineCount = changedBlock.originalEndLineExclusive - changedBlock.originalStartLine
        if (changedOriginalLineCount > 0) {
            val firstChangedLine = lineNumberAt(documentText, anchorOffset)
            val lastChangedLine =
                (firstChangedLine + changedOriginalLineCount - 1)
                    .coerceAtMost(lastLineNumber(documentText))
            return LineSuggestionPlacement(
                renderOffset = lineEndOffset(documentText, lastChangedLine),
                showAbove = false,
            )
        }

        val anchorLine = lineNumberAt(documentText, anchorOffset)
        return if (anchorLine > 0) {
            LineSuggestionPlacement(
                renderOffset = lineEndOffset(documentText, anchorLine - 1),
                showAbove = false,
            )
        } else {
            LineSuggestionPlacement(
                renderOffset = anchorOffset,
                showAbove = true,
            )
        }
    }

    private fun lineNumberAt(
        text: CharSequence,
        offset: Int,
    ): Int {
        val safeOffset = offset.coerceIn(0, text.length)
        var line = 0
        for (index in 0 until safeOffset) {
            if (text[index] == '\n') line++
        }
        return line
    }

    private fun lineEndOffset(
        text: CharSequence,
        lineNumber: Int,
    ): Int {
        val startOffset = lineStartOffset(text, lineNumber)
        var index = startOffset
        while (index < text.length && text[index] != '\n') {
            index++
        }
        return index
    }

    private fun lineStartOffset(
        text: CharSequence,
        lineNumber: Int,
    ): Int {
        if (lineNumber <= 0) return 0

        var currentLine = 0
        for (index in text.indices) {
            if (text[index] == '\n') {
                currentLine++
                if (currentLine == lineNumber) return index + 1
            }
        }
        return text.length
    }

    private fun lastLineNumber(text: CharSequence): Int = lineNumberAt(text, text.length)
}
