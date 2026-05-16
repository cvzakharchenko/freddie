package com.github.cvzakharchenko.freddie.controller

import com.github.cvzakharchenko.freddie.context.LineEndingNormalizer
import com.github.cvzakharchenko.freddie.presentation.ChangedBlock

internal enum class PartialAcceptKind {
    WORD,
    LINE,
}

internal data class PartialAcceptResult(
    val text: String,
    val completed: Boolean,
)

internal object PartialSuggestionAcceptance {
    fun accept(
        currentText: String,
        replacementText: String,
        kind: PartialAcceptKind,
    ): PartialAcceptResult? {
        val currentLf = LineEndingNormalizer.normalizeToLf(currentText)
        val replacementLf = LineEndingNormalizer.normalizeToLf(replacementText)
        if (currentLf == replacementLf) return null

        val block = ChangedBlock.allBetween(currentLf, replacementLf).firstOrNull() ?: return null
        val currentLines = lineInfos(currentLf)
        val replacementLines = lineInfos(replacementLf)
        val accepted = acceptedLines(kind, block, currentLines, replacementLines)
        val consumedOriginalLineCount =
            when {
                block.originalStartLine >= block.originalEndLineExclusive -> 0
                else -> 1
            }
        val consumedOriginalEnd =
            (block.originalStartLine + consumedOriginalLineCount)
                .coerceAtMost(block.originalEndLineExclusive)

        val newLf =
            buildString {
                currentLines.subList(0, block.originalStartLine).forEach { append(it.textWithSeparator) }
                accepted.forEach { append(it.textWithSeparator) }
                currentLines.subList(consumedOriginalEnd, currentLines.size).forEach { append(it.textWithSeparator) }
            }
        if (newLf == currentLf) return null

        val targetLineSeparator =
            LineEndingNormalizer.dominantLineSeparator(replacementText)
                ?: LineEndingNormalizer.dominantLineSeparator(currentText)
                ?: "\n"
        val newText = LineEndingNormalizer.convertLfToLineSeparator(newLf, targetLineSeparator)
        return PartialAcceptResult(
            text = newText,
            completed = newLf == replacementLf,
        )
    }

    private fun acceptedLines(
        kind: PartialAcceptKind,
        block: ChangedBlock,
        currentLines: List<LineInfo>,
        replacementLines: List<LineInfo>,
    ): List<LineInfo> {
        if (block.replacementStartLine >= block.replacementEndLineExclusive) {
            return emptyList()
        }

        val replacementLine = replacementLines[block.replacementStartLine]
        if (kind == PartialAcceptKind.LINE) {
            return listOf(replacementLine)
        }

        val currentLine =
            currentLines
                .getOrNull(block.originalStartLine)
                ?.takeIf { block.originalStartLine < block.originalEndLineExclusive }
        return listOf(
            LineInfo(
                text = acceptedWordPrefix(currentLine?.text.orEmpty(), replacementLine.text),
                separator = replacementLine.separator,
            ),
        )
    }

    private fun acceptedWordPrefix(
        currentLine: String,
        replacementLine: String,
    ): String {
        var index = commonPrefixLength(currentLine, replacementLine)
        if (index >= replacementLine.length) return replacementLine

        while (index < replacementLine.length && replacementLine[index].isWhitespace()) {
            index++
        }
        if (index >= replacementLine.length) return replacementLine

        if (replacementLine[index].isWordPart()) {
            while (index < replacementLine.length && replacementLine[index].isWordPart()) {
                index++
            }
        } else {
            index++
        }
        return replacementLine.substring(0, index)
    }

    private fun commonPrefixLength(
        left: String,
        right: String,
    ): Int {
        val max = minOf(left.length, right.length)
        var index = 0
        while (index < max && left[index] == right[index]) {
            index++
        }
        return index
    }

    private fun Char.isWordPart(): Boolean = isLetterOrDigit() || this == '_'

    private fun lineInfos(text: String): List<LineInfo> {
        if (text.isEmpty()) return emptyList()

        val lines = mutableListOf<LineInfo>()
        var lineStart = 0
        var index = 0
        while (index < text.length) {
            if (text[index] == '\n') {
                lines.add(LineInfo(text.substring(lineStart, index), "\n"))
                lineStart = index + 1
            }
            index++
        }
        if (lineStart < text.length) {
            lines.add(LineInfo(text.substring(lineStart), ""))
        }
        return lines
    }

    private data class LineInfo(
        val text: String,
        val separator: String,
    ) {
        val textWithSeparator: String
            get() = text + separator
    }
}
