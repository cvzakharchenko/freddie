package com.github.cvzakharchenko.freddie.context

object LineEndingNormalizer {
    data class PreparedReplacement(
        val applicationText: String,
        val changedLineEndings: Boolean,
        val changedLeadingLineEnding: Boolean,
        val changedTrailingLineEnding: Boolean,
        val targetLineSeparator: String,
    )

    data class NormalizedText(
        val text: String,
        val sourceOffsetByNormalizedOffset: IntArray,
    ) {
        fun sourceOffset(normalizedOffset: Int): Int =
            sourceOffsetByNormalizedOffset[normalizedOffset.coerceIn(sourceOffsetByNormalizedOffset.indices)]
    }

    fun prepareReplacementForEditableRegion(
        mercuryReplacement: String,
        originalEditableRegion: String,
        documentText: String,
    ): PreparedReplacement {
        val normalizedOriginal = normalizeToLf(originalEditableRegion)
        val normalizedReplacement = normalizeToLf(mercuryReplacement)
        val leadingAlignedReplacement = alignLeadingLineEndingPresence(normalizedReplacement, normalizedOriginal)
        val trailingAlignedReplacement = alignTrailingLineEndingPresence(leadingAlignedReplacement, normalizedOriginal)
        val targetLineSeparator = dominantLineSeparator(documentText) ?: "\n"
        val applicationText = convertLfToLineSeparator(trailingAlignedReplacement, targetLineSeparator)

        return PreparedReplacement(
            applicationText = applicationText,
            changedLineEndings = mercuryReplacement != normalizeLike(mercuryReplacement, documentText),
            changedLeadingLineEnding = leadingAlignedReplacement != normalizedReplacement,
            changedTrailingLineEnding = trailingAlignedReplacement != leadingAlignedReplacement,
            targetLineSeparator = targetLineSeparator,
        )
    }

    fun normalizeLike(
        text: String,
        referenceText: String,
    ): String {
        val separator = dominantLineSeparator(referenceText) ?: return text
        return text.replace(LINE_ENDING_PATTERN, separator)
    }

    fun normalizeToLf(text: String): String = text.replace(LINE_ENDING_PATTERN, "\n")

    fun normalizeToLfWithSourceOffsets(text: String): NormalizedText {
        val normalized = StringBuilder(text.length)
        val sourceOffsets = ArrayList<Int>(text.length + 1)
        sourceOffsets.add(0)

        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        normalized.append('\n')
                        index += 2
                    } else {
                        normalized.append('\n')
                        index++
                    }
                }
                else -> {
                    normalized.append(text[index])
                    index++
                }
            }
            sourceOffsets.add(index)
        }

        return NormalizedText(
            text = normalized.toString(),
            sourceOffsetByNormalizedOffset = sourceOffsets.toIntArray(),
        )
    }

    fun dominantLineSeparator(text: String): String? {
        var crlf = 0
        var lf = 0
        var cr = 0
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        crlf++
                        index += 2
                    } else {
                        cr++
                        index++
                    }
                }
                '\n' -> {
                    lf++
                    index++
                }
                else -> index++
            }
        }

        val max = maxOf(crlf, lf, cr)
        if (max == 0) return null
        return when (max) {
            crlf -> "\r\n"
            lf -> "\n"
            else -> "\r"
        }
    }

    fun convertLfToLineSeparator(
        text: String,
        lineSeparator: String,
    ): String =
        if (lineSeparator == "\n") text else text.replace("\n", lineSeparator)

    private fun alignTrailingLineEndingPresence(
        replacement: String,
        original: String,
    ): String {
        val originalTrailingLineEndings = trailingLineEndingCount(original)
        val replacementTrailingLineEndings = trailingLineEndingCount(replacement)
        if (originalTrailingLineEndings == replacementTrailingLineEndings) {
            return replacement
        }

        return replacement.dropLast(replacementTrailingLineEndings) + "\n".repeat(originalTrailingLineEndings)
    }

    private fun alignLeadingLineEndingPresence(
        replacement: String,
        original: String,
    ): String {
        if (original.startsWith("\n") || !replacement.startsWith("\n")) return replacement

        val withoutLeadingBlank = replacement.dropWhile { it == '\n' }
        if (withoutLeadingBlank.isEmpty()) return replacement

        val originalFirstLine = original.substringBefore('\n')
        if (originalFirstLine.isEmpty()) return replacement
        return if (withoutLeadingBlank.startsWith(originalFirstLine)) withoutLeadingBlank else replacement
    }

    private fun trailingLineEndingCount(text: String): Int {
        var count = 0
        var index = text.length - 1
        while (index >= 0 && text[index] == '\n') {
            count++
            index--
        }
        return count
    }

    private val LINE_ENDING_PATTERN = Regex("\\r\\n|\\r|\\n")
}
