package com.github.cvzakharchenko.freddie.presentation

import com.github.cvzakharchenko.freddie.context.LineEndingNormalizer

internal data class ChangedBlock(
    val anchorOffsetInOriginal: Int,
    val originalStartLine: Int,
    val originalEndLineExclusive: Int,
    val replacementBlock: String,
) {
    val isDeletionOnly: Boolean
        get() = replacementBlock.isEmpty()

    companion object {
        fun between(
            original: String,
            replacement: String,
        ): ChangedBlock? {
            val normalizedOriginal = LineEndingNormalizer.normalizeToLfWithSourceOffsets(original)
            val originalLines = trimTrailingBlankBoundaryLines(lineInfos(normalizedOriginal.text))
            val replacementLines = trimTrailingBlankBoundaryLines(lineInfos(LineEndingNormalizer.normalizeToLf(replacement)))

            var prefix = 0
            while (
                prefix < originalLines.size &&
                prefix < replacementLines.size &&
                originalLines[prefix].text == replacementLines[prefix].text
            ) {
                prefix++
            }

            var suffix = 0
            while (
                suffix < originalLines.size - prefix &&
                suffix < replacementLines.size - prefix &&
                originalLines[originalLines.lastIndex - suffix].text == replacementLines[replacementLines.lastIndex - suffix].text
            ) {
                suffix++
            }

            if (prefix == originalLines.size && prefix == replacementLines.size) return null

            val anchorNormalizedOffset =
                when {
                    prefix < originalLines.size -> originalLines[prefix].startOffset
                    normalizedOriginal.text.isNotEmpty() -> normalizedOriginal.text.length
                    else -> 0
                }
            val replacementBlockLines = replacementLines.subList(prefix, replacementLines.size - suffix)
            return ChangedBlock(
                anchorOffsetInOriginal = normalizedOriginal.sourceOffset(anchorNormalizedOffset),
                originalStartLine = prefix,
                originalEndLineExclusive = originalLines.size - suffix,
                replacementBlock = replacementBlockLines.joinToString("\n") { it.text },
            )
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
                    lines.add(LineInfo(text.substring(lineStart, index), lineStart))
                    lineStart = index + 1
                }
                index++
            }
            if (lineStart < text.length) {
                lines.add(LineInfo(text.substring(lineStart), lineStart))
            }
            return lines
        }
    }
}

private data class LineInfo(
    val text: String,
    val startOffset: Int,
)
