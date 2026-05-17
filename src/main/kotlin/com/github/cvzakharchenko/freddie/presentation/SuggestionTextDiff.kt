package com.github.cvzakharchenko.freddie.presentation

import com.github.cvzakharchenko.freddie.context.LineEndingNormalizer

internal data class SuggestionBlockTextDiff(
    val replacementSegments: List<SuggestionTextSegment>,
    val deletedRanges: List<SuggestionDeletedRange>,
) {
    val replacementText: String
        get() = replacementSegments.joinToString(separator = "") { it.text }
}

internal data class SuggestionTextSegment(
    val text: String,
    val kind: SuggestionTextSegmentKind,
)

internal enum class SuggestionTextSegmentKind {
    EQUAL,
    INSERTED,
}

internal data class SuggestionDeletedRange(
    val startOffsetInOriginal: Int,
    val endOffsetInOriginal: Int,
)

internal object SuggestionTextDiff {
    fun between(
        original: String,
        replacement: String,
        block: ChangedBlock,
    ): SuggestionBlockTextDiff {
        val normalizedOriginal = LineEndingNormalizer.normalizeToLfWithSourceOffsets(original)
        val originalLines = lineInfos(normalizedOriginal.text)
        val replacementLines = lineInfos(LineEndingNormalizer.normalizeToLf(replacement))
        val originalBlockLines = originalLines.subList(block.originalStartLine, block.originalEndLineExclusive)
        val replacementBlockLines = replacementLines.subList(block.replacementStartLine, block.replacementEndLineExclusive)
        val segments = mutableListOf<SuggestionTextSegment>()
        val deletedRanges = mutableListOf<SuggestionDeletedRange>()
        val pairedLineCount = minOf(originalBlockLines.size, replacementBlockLines.size)

        for (index in 0 until pairedLineCount) {
            if (segments.isNotEmpty()) {
                addSegment(segments, "\n", SuggestionTextSegmentKind.EQUAL)
            }
            val lineDiff =
                compareLines(
                    originalLine = originalBlockLines[index],
                    replacementLine = replacementBlockLines[index],
                    normalizedOriginal = normalizedOriginal,
                )
            lineDiff.replacementSegments.forEach { addSegment(segments, it.text, it.kind) }
            deletedRanges.addAll(lineDiff.deletedRanges)
        }

        for (index in pairedLineCount until replacementBlockLines.size) {
            if (segments.isNotEmpty()) {
                addSegment(segments, "\n", SuggestionTextSegmentKind.EQUAL)
            }
            insertedLineSegments(replacementBlockLines[index].text)
                .forEach { addSegment(segments, it.text, it.kind) }
        }

        for (index in pairedLineCount until originalBlockLines.size) {
            deletedRanges.addAll(
                deletedTokenRanges(
                    originalLine = originalBlockLines[index],
                    normalizedOriginal = normalizedOriginal,
                ),
            )
        }

        return SuggestionBlockTextDiff(
            replacementSegments = segments,
            deletedRanges = deletedRanges,
        )
    }

    fun limitVisibleLines(segments: List<SuggestionTextSegment>): List<SuggestionTextSegment> {
        val text = segments.joinToString(separator = "") { it.text }
        val lines = text.lineSequence().take(MAX_VISIBLE_LINES + 1).toList()
        if (lines.size <= MAX_VISIBLE_LINES) return segments

        val visiblePrefix = lines.take(MAX_VISIBLE_LINES).joinToString("\n")
        val limited = sliceSegments(segments, visiblePrefix.length).toMutableList()
        addSegment(limited, "\n...", SuggestionTextSegmentKind.EQUAL)
        return limited
    }

    private fun compareLines(
        originalLine: DiffLineInfo,
        replacementLine: DiffLineInfo,
        normalizedOriginal: LineEndingNormalizer.NormalizedText,
    ): SuggestionBlockTextDiff {
        val commonPrefixLength = commonPrefixLength(originalLine.text, replacementLine.text)
        val commonSuffixLength = commonSuffixLength(originalLine.text, replacementLine.text, commonPrefixLength)
        val originalChangedEndOffset = originalLine.text.length - commonSuffixLength
        val originalTokens = SuggestionTextTokenizer.tokenize(originalLine.text)
        val replacementTokens = SuggestionTextTokenizer.tokenize(replacementLine.text)
        val matches = lcsMatches(originalTokens, replacementTokens)
        val matchedOriginalIndexes = matches.mapTo(mutableSetOf()) { it.first }
        val matchedReplacementIndexes = matches.mapTo(mutableSetOf()) { it.second }
        val originalIndexByReplacementIndex = matches.associate { (originalIndex, replacementIndex) -> replacementIndex to originalIndex }
        val segments = mutableListOf<SuggestionTextSegment>()
        val deletedRanges = mutableListOf<SuggestionDeletedRange>()

        replacementTokens.forEachIndexed { index, token ->
            val originalToken = originalIndexByReplacementIndex[index]?.let { originalTokens[it] }
            when {
                token.isWhitespace -> addSegment(segments, token.text, SuggestionTextSegmentKind.EQUAL)
                originalToken != null -> addMatchedTokenSegments(segments, originalToken, token)
                index in matchedReplacementIndexes -> addSegment(segments, token.text, SuggestionTextSegmentKind.EQUAL)
                else -> addSegment(segments, token.text, SuggestionTextSegmentKind.INSERTED)
            }
        }

        originalTokens.forEachIndexed { index, token ->
            if (index !in matchedOriginalIndexes && !token.isWhitespace) {
                val deleteStartOffset = maxOf(token.startOffset, commonPrefixLength)
                val deleteEndOffset = minOf(token.endOffset, originalChangedEndOffset)
                if (deleteStartOffset < deleteEndOffset) {
                    deletedRanges.add(
                        SuggestionDeletedRange(
                            startOffsetInOriginal =
                                normalizedOriginal.sourceOffset(originalLine.startOffset + deleteStartOffset),
                            endOffsetInOriginal = normalizedOriginal.sourceOffset(originalLine.startOffset + deleteEndOffset),
                        ),
                    )
                }
            }
        }

        return SuggestionBlockTextDiff(
            forceEqualAffixes(segments, commonPrefixLength, commonSuffixLength),
            deletedRanges,
        )
    }

    private fun insertedLineSegments(line: String): List<SuggestionTextSegment> =
        SuggestionTextTokenizer
            .tokenize(line)
            .map { token ->
                SuggestionTextSegment(
                    text = token.text,
                    kind = SuggestionTextSegmentKind.INSERTED,
                )
            }

    private fun deletedTokenRanges(
        originalLine: DiffLineInfo,
        normalizedOriginal: LineEndingNormalizer.NormalizedText,
    ): List<SuggestionDeletedRange> =
        SuggestionTextTokenizer
            .tokenize(originalLine.text)
            .filterNot { it.isWhitespace }
            .map { token ->
                SuggestionDeletedRange(
                    startOffsetInOriginal = normalizedOriginal.sourceOffset(originalLine.startOffset + token.startOffset),
                    endOffsetInOriginal = normalizedOriginal.sourceOffset(originalLine.startOffset + token.endOffset),
                )
            }

    private fun lcsMatches(
        originalTokens: List<SuggestionTextToken>,
        replacementTokens: List<SuggestionTextToken>,
    ): List<Pair<Int, Int>> {
        val originalSize = originalTokens.size
        val replacementSize = replacementTokens.size
        val lengths = Array(originalSize + 1) { IntArray(replacementSize + 1) }

        for (originalIndex in originalSize - 1 downTo 0) {
            for (replacementIndex in replacementSize - 1 downTo 0) {
                lengths[originalIndex][replacementIndex] =
                    if (tokensMatch(originalTokens[originalIndex], replacementTokens[replacementIndex])) {
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
                tokensMatch(originalTokens[originalIndex], replacementTokens[replacementIndex]) -> {
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

    private fun tokensMatch(
        original: SuggestionTextToken,
        replacement: SuggestionTextToken,
    ): Boolean =
        if (original.isWhitespace || replacement.isWhitespace) {
            original.text == replacement.text
        } else {
            original.kind == replacement.kind && original.coreText == replacement.coreText
        }

    private fun addMatchedTokenSegments(
        segments: MutableList<SuggestionTextSegment>,
        originalToken: SuggestionTextToken,
        replacementToken: SuggestionTextToken,
    ) {
        val commonWhitespaceLength = commonPrefixLength(originalToken.leadingWhitespace, replacementToken.leadingWhitespace)
        addSegment(
            segments,
            replacementToken.leadingWhitespace.take(commonWhitespaceLength),
            SuggestionTextSegmentKind.EQUAL,
        )
        addSegment(
            segments,
            replacementToken.leadingWhitespace.drop(commonWhitespaceLength),
            SuggestionTextSegmentKind.INSERTED,
        )
        addSegment(segments, replacementToken.coreText, SuggestionTextSegmentKind.EQUAL)
    }

    private fun commonPrefixLength(
        original: String,
        replacement: String,
    ): Int {
        val max = minOf(original.length, replacement.length)
        var index = 0
        while (index < max && original[index] == replacement[index]) {
            index++
        }
        return index
    }

    private fun commonSuffixLength(
        original: String,
        replacement: String,
        prefixLength: Int,
    ): Int {
        val max = minOf(original.length, replacement.length) - prefixLength
        var suffix = 0
        while (
            suffix < max &&
            original[original.lastIndex - suffix] == replacement[replacement.lastIndex - suffix]
        ) {
            suffix++
        }
        return suffix
    }

    private fun forceEqualAffixes(
        segments: List<SuggestionTextSegment>,
        prefixLength: Int,
        suffixLength: Int,
    ): List<SuggestionTextSegment> =
        forceSuffixEqual(
            segments = forcePrefixEqual(segments, prefixLength),
            suffixLength = suffixLength,
        )

    private fun forcePrefixEqual(
        segments: List<SuggestionTextSegment>,
        prefixLength: Int,
    ): List<SuggestionTextSegment> {
        if (prefixLength <= 0) return segments

        val result = mutableListOf<SuggestionTextSegment>()
        var remainingPrefix = prefixLength
        for (segment in segments) {
            when {
                remainingPrefix <= 0 -> addSegment(result, segment.text, segment.kind)
                segment.text.length <= remainingPrefix -> {
                    addSegment(result, segment.text, SuggestionTextSegmentKind.EQUAL)
                    remainingPrefix -= segment.text.length
                }
                else -> {
                    addSegment(result, segment.text.take(remainingPrefix), SuggestionTextSegmentKind.EQUAL)
                    addSegment(result, segment.text.drop(remainingPrefix), segment.kind)
                    remainingPrefix = 0
                }
            }
        }
        return result
    }

    private fun forceSuffixEqual(
        segments: List<SuggestionTextSegment>,
        suffixLength: Int,
    ): List<SuggestionTextSegment> {
        if (suffixLength <= 0) return segments

        val reversed = mutableListOf<SuggestionTextSegment>()
        var remainingSuffix = suffixLength
        for (segment in segments.asReversed()) {
            when {
                remainingSuffix <= 0 -> addSegment(reversed, segment.text.reversed(), segment.kind)
                segment.text.length <= remainingSuffix -> {
                    addSegment(reversed, segment.text.reversed(), SuggestionTextSegmentKind.EQUAL)
                    remainingSuffix -= segment.text.length
                }
                else -> {
                    val unchangedSuffix = segment.text.takeLast(remainingSuffix)
                    val changedPrefix = segment.text.dropLast(remainingSuffix)
                    addSegment(reversed, unchangedSuffix.reversed(), SuggestionTextSegmentKind.EQUAL)
                    addSegment(reversed, changedPrefix.reversed(), segment.kind)
                    remainingSuffix = 0
                }
            }
        }

        return reversed
            .asReversed()
            .map { it.copy(text = it.text.reversed()) }
            .fold(mutableListOf()) { result, segment ->
                addSegment(result, segment.text, segment.kind)
                result
            }
    }

    private fun sliceSegments(
        segments: List<SuggestionTextSegment>,
        endOffset: Int,
    ): List<SuggestionTextSegment> {
        val result = mutableListOf<SuggestionTextSegment>()
        var remaining = endOffset
        for (segment in segments) {
            if (remaining <= 0) break
            if (segment.text.length <= remaining) {
                addSegment(result, segment.text, segment.kind)
                remaining -= segment.text.length
            } else {
                addSegment(result, segment.text.take(remaining), segment.kind)
                remaining = 0
            }
        }
        return result
    }

    private fun addSegment(
        segments: MutableList<SuggestionTextSegment>,
        text: String,
        kind: SuggestionTextSegmentKind,
    ) {
        if (text.isEmpty()) return
        val last = segments.lastOrNull()
        if (last != null && last.kind == kind) {
            segments[segments.lastIndex] = last.copy(text = last.text + text)
        } else {
            segments.add(SuggestionTextSegment(text, kind))
        }
    }

    private fun lineInfos(text: String): List<DiffLineInfo> {
        if (text.isEmpty()) return emptyList()

        val lines = mutableListOf<DiffLineInfo>()
        var lineStart = 0
        var index = 0
        while (index < text.length) {
            if (text[index] == '\n') {
                lines.add(DiffLineInfo(text.substring(lineStart, index), lineStart))
                lineStart = index + 1
            }
            index++
        }
        if (lineStart < text.length) {
            lines.add(DiffLineInfo(text.substring(lineStart), lineStart))
        }
        return lines
    }

    private const val MAX_VISIBLE_LINES = 40
}

private data class DiffLineInfo(
    val text: String,
    val startOffset: Int,
)
