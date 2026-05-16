package com.github.cvzakharchenko.freddie.presentation

import com.github.cvzakharchenko.freddie.context.LineEndingNormalizer

internal data class ChangedBlock(
    val anchorOffsetInOriginal: Int,
    val originalStartLine: Int,
    val originalEndLineExclusive: Int,
    val replacementStartLine: Int,
    val replacementEndLineExclusive: Int,
    val replacementBlock: String,
) {
    val isDeletionOnly: Boolean
        get() = replacementBlock.isEmpty()

    companion object {
        fun between(
            original: String,
            replacement: String,
        ): ChangedBlock? = allBetween(original, replacement).firstOrNull()

        fun allBetween(
            original: String,
            replacement: String,
        ): List<ChangedBlock> {
            val normalizedOriginal = LineEndingNormalizer.normalizeToLfWithSourceOffsets(original)
            val originalLines = trimTrailingBlankBoundaryLines(lineInfos(normalizedOriginal.text))
            val replacementLines = trimTrailingBlankBoundaryLines(lineInfos(LineEndingNormalizer.normalizeToLf(replacement)))
            val matches = lcsMatches(originalLines, replacementLines)
            val blocks = mutableListOf<ChangedBlock>()

            var originalCursor = 0
            var replacementCursor = 0
            for ((originalMatch, replacementMatch) in matches) {
                addChangedBlock(
                    blocks = blocks,
                    normalizedOriginal = normalizedOriginal,
                    originalLines = originalLines,
                    replacementLines = replacementLines,
                    originalStart = originalCursor,
                    originalEndExclusive = originalMatch,
                    replacementStart = replacementCursor,
                    replacementEndExclusive = replacementMatch,
                )
                originalCursor = originalMatch + 1
                replacementCursor = replacementMatch + 1
            }
            addChangedBlock(
                blocks = blocks,
                normalizedOriginal = normalizedOriginal,
                originalLines = originalLines,
                replacementLines = replacementLines,
                originalStart = originalCursor,
                originalEndExclusive = originalLines.size,
                replacementStart = replacementCursor,
                replacementEndExclusive = replacementLines.size,
            )

            return blocks
        }

        fun dropLastLineTouchingBlocks(
            original: String,
            replacement: String,
        ): FilteredReplacement {
            val normalizedOriginal = LineEndingNormalizer.normalizeToLfWithSourceOffsets(original)
            val normalizedReplacement = LineEndingNormalizer.normalizeToLf(replacement)
            val originalLines = lineInfos(normalizedOriginal.text)
            val replacementLines = lineInfos(normalizedReplacement)
            val diffOriginalLines = trimTrailingBlankBoundaryLines(originalLines)
            val diffReplacementLines = trimTrailingBlankBoundaryLines(replacementLines)
            val blocks = allBetween(original, replacement)
            if (blocks.isEmpty()) {
                return FilteredReplacement(
                    text = normalizedReplacement,
                    keptBlockCount = 0,
                    droppedBlockCount = 0,
                )
            }

            val keptBlocks =
                blocks.filterNot { it.touchesOriginalLastLine(diffOriginalLines.size) }
            if (keptBlocks.size == blocks.size) {
                return FilteredReplacement(
                    text = normalizedReplacement,
                    keptBlockCount = blocks.size,
                    droppedBlockCount = 0,
                )
            }

            val rebuiltLines = mutableListOf<LineInfo>()
            var originalCursor = 0
            for (block in blocks) {
                rebuiltLines.addAll(originalLines.subList(originalCursor, block.originalStartLine))
                if (block in keptBlocks) {
                    rebuiltLines.addAll(replacementLines.subList(block.replacementStartLine, block.replacementEndLineExclusive))
                } else {
                    rebuiltLines.addAll(originalLines.subList(block.originalStartLine, block.originalEndLineExclusive))
                }
                originalCursor = block.originalEndLineExclusive
            }
            rebuiltLines.addAll(originalLines.subList(originalCursor, originalLines.size))

            return FilteredReplacement(
                text = rebuiltLines.joinToText(),
                keptBlockCount = keptBlocks.size,
                droppedBlockCount = blocks.size - keptBlocks.size,
            )
        }

        private fun addChangedBlock(
            blocks: MutableList<ChangedBlock>,
            normalizedOriginal: LineEndingNormalizer.NormalizedText,
            originalLines: List<LineInfo>,
            replacementLines: List<LineInfo>,
            originalStart: Int,
            originalEndExclusive: Int,
            replacementStart: Int,
            replacementEndExclusive: Int,
        ) {
            if (originalStart == originalEndExclusive && replacementStart == replacementEndExclusive) return

            val anchorNormalizedOffset =
                when {
                    originalStart < originalLines.size -> originalLines[originalStart].startOffset
                    normalizedOriginal.text.isNotEmpty() -> normalizedOriginal.text.length
                    else -> 0
                }
            blocks.add(
                ChangedBlock(
                    anchorOffsetInOriginal = normalizedOriginal.sourceOffset(anchorNormalizedOffset),
                    originalStartLine = originalStart,
                    originalEndLineExclusive = originalEndExclusive,
                    replacementStartLine = replacementStart,
                    replacementEndLineExclusive = replacementEndExclusive,
                    replacementBlock =
                        replacementLines
                            .subList(replacementStart, replacementEndExclusive)
                            .joinToString("\n") { it.text },
                ),
            )
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

        private fun trimTrailingBlankBoundaryLines(lines: List<LineInfo>): List<LineInfo> {
            var endExclusive = lines.size
            while (endExclusive > 0 && lines[endExclusive - 1].text.isEmpty()) {
                endExclusive--
            }
            return lines.subList(0, endExclusive)
        }

        private fun lineInfos(text: String): List<LineInfo> {
            if (text.isEmpty()) return emptyList()

            val lines = mutableListOf<LineInfo>()
            var lineStart = 0
            var index = 0
            while (index < text.length) {
                if (text[index] == '\n') {
                    lines.add(LineInfo(text.substring(lineStart, index), lineStart, "\n"))
                    lineStart = index + 1
                }
                index++
            }
            if (lineStart < text.length) {
                lines.add(LineInfo(text.substring(lineStart), lineStart, ""))
            }
            return lines
        }

        private fun ChangedBlock.touchesOriginalLastLine(originalLineCount: Int): Boolean {
            if (originalLineCount == 0) return false
            val lastLine = originalLineCount - 1
            return originalStartLine >= lastLine || originalEndLineExclusive > lastLine
        }

        private fun List<LineInfo>.joinToText(): String =
            buildString {
                for (line in this@joinToText) {
                    append(line.text)
                    append(line.separator)
                }
            }
    }
}

internal data class FilteredReplacement(
    val text: String,
    val keptBlockCount: Int,
    val droppedBlockCount: Int,
)

private data class LineInfo(
    val text: String,
    val startOffset: Int,
    val separator: String,
)
