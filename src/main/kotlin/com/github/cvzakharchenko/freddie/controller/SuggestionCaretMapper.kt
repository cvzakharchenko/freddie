package com.github.cvzakharchenko.freddie.controller

internal object SuggestionCaretMapper {
    fun mapCaretOffset(
        originalText: String,
        replacementText: String,
        caretOffsetInOriginal: Int,
    ): Int {
        val originalCaret = caretOffsetInOriginal.coerceIn(0, originalText.length)
        val textBeforeCaret = originalText.substring(0, originalCaret)
        val textAfterCaret = originalText.substring(originalCaret)

        if (textBeforeCaret.isNotEmpty() && replacementText.startsWith(textBeforeCaret)) {
            return textBeforeCaret.length
        }

        if (textAfterCaret.isNotEmpty() && replacementText.endsWith(textAfterCaret)) {
            return replacementText.length - textAfterCaret.length
        }

        val afterCaretIndex = textAfterCaret.takeIf { it.isNotEmpty() }?.let { replacementText.indexOf(it) } ?: -1
        if (afterCaretIndex >= 0) {
            return afterCaretIndex
        }

        val beforeCaretIndex = textBeforeCaret.takeIf { it.isNotEmpty() }?.let { replacementText.lastIndexOf(it) } ?: -1
        if (beforeCaretIndex >= 0) {
            return beforeCaretIndex + textBeforeCaret.length
        }

        return originalCaret.coerceIn(0, replacementText.length)
    }
}
