package com.github.cvzakharchenko.freddie.presentation

internal data class SuggestionTextToken(
    val text: String,
    val leadingWhitespace: String,
    val coreText: String,
    val startOffset: Int,
    val coreStartOffset: Int,
    val endOffset: Int,
    val kind: SuggestionTextTokenKind,
) {
    val isWhitespace: Boolean
        get() = kind == SuggestionTextTokenKind.WHITESPACE
}

internal enum class SuggestionTextTokenKind {
    WORD,
    SYMBOLS,
    WHITESPACE,
}

internal object SuggestionTextTokenizer {
    fun tokenize(text: String): List<SuggestionTextToken> {
        if (text.isEmpty()) return emptyList()

        val tokens = mutableListOf<SuggestionTextToken>()
        var index = 0
        while (index < text.length) {
            val start = index
            while (index < text.length && text[index].isWhitespace()) {
                index++
            }
            if (index >= text.length) {
                tokens.add(
                    SuggestionTextToken(
                        text = text.substring(start, index),
                        leadingWhitespace = text.substring(start, index),
                        coreText = "",
                        startOffset = start,
                        coreStartOffset = index,
                        endOffset = index,
                        kind = SuggestionTextTokenKind.WHITESPACE,
                    ),
                )
                break
            }

            val coreStart = index
            val kind = tokenKind(text[index])
            index++
            while (index < text.length && tokenKind(text[index]) == kind) {
                index++
            }
            tokens.add(
                SuggestionTextToken(
                    text = text.substring(start, index),
                    leadingWhitespace = text.substring(start, coreStart),
                    coreText = text.substring(coreStart, index),
                    startOffset = start,
                    coreStartOffset = coreStart,
                    endOffset = index,
                    kind = kind,
                ),
            )
        }
        return tokens
    }

    fun nextTokenEnd(
        text: String,
        startOffset: Int,
    ): Int {
        var index = startOffset.coerceIn(0, text.length)
        while (index < text.length && text[index].isWhitespace()) {
            index++
        }
        if (index >= text.length) return text.length

        val kind = tokenKind(text[index])
        index++
        while (index < text.length && tokenKind(text[index]) == kind) {
            index++
        }
        return index
    }

    private fun tokenKind(char: Char): SuggestionTextTokenKind =
        when {
            char.isWhitespace() -> SuggestionTextTokenKind.WHITESPACE
            char.isLetterOrDigit() || char == '_' -> SuggestionTextTokenKind.WORD
            else -> SuggestionTextTokenKind.SYMBOLS
        }
}
