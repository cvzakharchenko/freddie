package com.github.cvzakharchenko.freddie.context

import com.intellij.openapi.editor.Document

data class EditableRegion(
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val endLine: Int,
    val originalText: String,
    val beforeCursor: String,
    val afterCursor: String,
)

object EditableRegionSelector {
    private const val LINES_BEFORE = 5
    private const val LINES_AFTER = 10

    fun select(
        document: Document,
        caretOffset: Int,
    ): EditableRegion {
        val safeCaret = caretOffset.coerceIn(0, document.textLength)
        val caretLine = document.getLineNumber(safeCaret)
        val startLine = (caretLine - LINES_BEFORE).coerceAtLeast(0)
        val endLine = (caretLine + LINES_AFTER).coerceAtMost((document.lineCount - 1).coerceAtLeast(0))
        val startOffset = document.getLineStartOffset(startLine)
        val endOffset =
            if (endLine + 1 < document.lineCount) {
                document.getLineStartOffset(endLine + 1)
            } else {
                document.textLength
            }
        val text = document.charsSequence
        val original = text.subSequence(startOffset, endOffset).toString()
        return EditableRegion(
            startOffset = startOffset,
            endOffset = endOffset,
            startLine = startLine,
            endLine = endLine,
            originalText = original,
            beforeCursor = text.subSequence(startOffset, safeCaret).toString(),
            afterCursor = text.subSequence(safeCaret, endOffset).toString(),
        )
    }
}
