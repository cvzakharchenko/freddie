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

        return lastBlock?.caretOffset(replacementLines, replacementText.length) ?: replacementText.length
    }

    private fun changedBlock(
        originalStart: Int,
        originalEndExclusive: Int,
        replacementStart: Int,
        replacementEndExclusive: Int,
    ): ChangedLineBlock? {
        if (originalStart == originalEndExclusive && replacementStart == replacementEndExclusive) return null
        return ChangedLineBlock(replacementStart, replacementEndExclusive)
    }

    private fun ChangedLineBlock.caretOffset(
        replacementLines: List<LineInfo>,
        replacementLength: Int,
    ): Int =
        if (replacementStartLine < replacementEndLineExclusive) {
            replacementLines[replacementEndLineExclusive - 1].endOffset
        } else {
            replacementLines.getOrNull(replacementStartLine)?.startOffset ?: replacementLength
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
                        endOffset = index,
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
                    endOffset = text.length,
                ),
            )
        }
        return lines
    }

    private data class ChangedLineBlock(
        val replacementStartLine: Int,
        val replacementEndLineExclusive: Int,
    )

    private data class LineInfo(
        val text: String,
        val startOffset: Int,
        val endOffset: Int,
    )
}
