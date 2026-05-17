package com.github.cvzakharchenko.freddie.controller

internal object SuggestionCaretMapper {
    fun caretAfterLastAppliedBlock(
        originalText: String,
        replacementText: String,
    ): Int {
        if (originalText == replacementText) return replacementText.length

        val originalLines = lineInfos(originalText)
        val replacementLines = lineInfos(replacementText)
        val matches = lcsMatches(originalLines, replacementLines)
        var originalCursor = 0
        var replacementCursor = 0
        var lastBlock: ChangedLineBlock? = null

        for ((originalMatch, replacementMatch) in matches) {
            lastBlock =
                changedBlock(
                    originalStart = originalCursor,
                    originalEndExclusive = originalMatch,
                    replacementStart = replacementCursor,
                    replacementEndExclusive = replacementMatch,
                ) ?: lastBlock
            originalCursor = originalMatch + 1
            replacementCursor = replacementMatch + 1
        }
        lastBlock =
            changedBlock(
                originalStart = originalCursor,
                originalEndExclusive = originalLines.size,
                replacementStart = replacementCursor,
                replacementEndExclusive = replacementLines.size,
            ) ?: lastBlock

        return lastBlock?.caretOffset(originalLines, replacementLines, replacementText.length) ?: replacementText.length
    }

    private fun changedBlock(
        originalStart: Int,
        originalEndExclusive: Int,
        replacementStart: Int,
        replacementEndExclusive: Int,
    ): ChangedLineBlock? {
        if (originalStart == originalEndExclusive && replacementStart == replacementEndExclusive) return null
        return ChangedLineBlock(
            originalStartLine = originalStart,
            originalEndLineExclusive = originalEndExclusive,
            replacementStartLine = replacementStart,
            replacementEndLineExclusive = replacementEndExclusive,
        )
    }

    private fun ChangedLineBlock.caretOffset(
        originalLines: List<LineInfo>,
        replacementLines: List<LineInfo>,
        replacementLength: Int,
    ): Int =
        if (replacementStartLine < replacementEndLineExclusive) {
            val replacementLine = replacementLines[replacementEndLineExclusive - 1]
            val originalLine = originalLines.getOrNull(originalEndLineExclusive - 1)
            val lineCaretOffset =
                if (originalLine != null && originalLineCount == replacementLineCount) {
                    caretAfterChangedPart(originalLine.text, replacementLine.text)
                } else {
                    replacementLine.text.length
                }
            replacementLine.startOffset + lineCaretOffset
        } else {
            replacementLines.getOrNull(replacementStartLine)?.startOffset ?: replacementLength
        }

    private val ChangedLineBlock.originalLineCount: Int
        get() = originalEndLineExclusive - originalStartLine

    private val ChangedLineBlock.replacementLineCount: Int
        get() = replacementEndLineExclusive - replacementStartLine

    private fun caretAfterChangedPart(
        originalLine: String,
        replacementLine: String,
    ): Int {
        val prefixLength = commonPrefixLength(originalLine, replacementLine)
        val suffixLength = commonSuffixLength(originalLine, replacementLine, prefixLength)
        return (replacementLine.length - suffixLength).coerceAtLeast(prefixLength)
    }

    private fun commonPrefixLength(
        original: String,
        replacement: String,
    ): Int {
        val maxLength = minOf(original.length, replacement.length)
        var index = 0
        while (index < maxLength && original[index] == replacement[index]) {
            index++
        }
        return index
    }

    private fun commonSuffixLength(
        original: String,
        replacement: String,
        prefixLength: Int,
    ): Int {
        var length = 0
        while (
            length < original.length - prefixLength &&
            length < replacement.length - prefixLength &&
            original[original.lastIndex - length] == replacement[replacement.lastIndex - length]
        ) {
            length++
        }
        return length
    }

    private fun lcsMatches(
        originalLines: List<LineInfo>,
        replacementLines: List<LineInfo>,
    ): List<Pair<Int, Int>> {
        val originalSize = originalLines.size
        val replacementSize = replacementLines.size
        val lengths = Array(originalSize + 1) { IntArray(replacementSize + 1) }

        for (originalIndex in originalSize - 1 downTo 0) {
            for (replacementIndex in replacementSize - 1 downTo 0) {
                lengths[originalIndex][replacementIndex] =
                    if (originalLines[originalIndex].text == replacementLines[replacementIndex].text) {
                        lengths[originalIndex + 1][replacementIndex + 1] + 1
                    } else {
                        maxOf(
                            lengths[originalIndex + 1][replacementIndex],
                            lengths[originalIndex][replacementIndex + 1],
                        )
                    }
            }
        }

        val matches = mutableListOf<Pair<Int, Int>>()
        var originalIndex = 0
        var replacementIndex = 0
        while (originalIndex < originalSize && replacementIndex < replacementSize) {
            when {
                originalLines[originalIndex].text == replacementLines[replacementIndex].text -> {
                    matches.add(originalIndex to replacementIndex)
                    originalIndex++
                    replacementIndex++
                }
                lengths[originalIndex + 1][replacementIndex] >= lengths[originalIndex][replacementIndex + 1] -> {
                    originalIndex++
                }
                else -> {
                    replacementIndex++
                }
            }
        }

        return matches
    }

    private fun lineInfos(text: String): List<LineInfo> {
        if (text.isEmpty()) return emptyList()

        val lines = mutableListOf<LineInfo>()
        var lineStart = 0
        var index = 0
        while (index < text.length) {
            val separatorLength =
                when {
                    text[index] == '\r' && text.getOrNull(index + 1) == '\n' -> 2
                    text[index] == '\r' || text[index] == '\n' -> 1
                    else -> 0
                }
            if (separatorLength > 0) {
                lines.add(
                    LineInfo(
                        text = text.substring(lineStart, index),
                        startOffset = lineStart,
                    ),
                )
                index += separatorLength
                lineStart = index
            } else {
                index++
            }
        }
        if (lineStart < text.length) {
            lines.add(
                LineInfo(
                    text = text.substring(lineStart),
                    startOffset = lineStart,
                ),
            )
        }
        return lines
    }

    private data class ChangedLineBlock(
        val originalStartLine: Int,
        val originalEndLineExclusive: Int,
        val replacementStartLine: Int,
        val replacementEndLineExclusive: Int,
    )

    private data class LineInfo(
        val text: String,
        val startOffset: Int,
    )
}
