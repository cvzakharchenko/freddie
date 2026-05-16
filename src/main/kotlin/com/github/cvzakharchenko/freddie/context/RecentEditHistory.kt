package com.github.cvzakharchenko.freddie.context

import kotlin.math.max
import kotlin.math.min

private data class RecentEdit(
    val filePath: String,
    val originalText: String,
    val newText: String,
) {
    val formattedDiff: String? = UnifiedDiffBuilder.build(filePath, originalText, newText)
    val changedLines: ChangedLineRanges = ChangedLineRanges.between(originalText, newText)
}

class RecentEditHistory {
    private val edits = mutableListOf<RecentEdit>()

    fun recordEdit(
        filePath: String,
        originalText: String,
        newText: String,
    ) {
        if (originalText == newText) return

        val candidate = RecentEdit(filePath, originalText, newText)
        if (!keepEdit(candidate)) {
            dropOverlappingEdits(candidate)
            return
        }

        val previousIndex = edits.indexOfLast { it.canCoalesceWith(candidate) }
        if (previousIndex >= 0) {
            val previous = edits[previousIndex]
            val patchedNewText = previous.apply(candidate)
            edits.removeAt(previousIndex)

            when {
                patchedNewText == null -> edits.add(candidate)
                patchedNewText == previous.originalText -> Unit
                else -> addMergedOrSplit(previous, candidate, patchedNewText)
            }
            trimOldestEdits()
            return
        }

        edits.add(candidate)
        trimOldestEdits()
    }

    fun formattedDiffs(): List<String> =
        formattedDiffsWithinBudget(Int.MAX_VALUE, Int.MAX_VALUE).diffsOldestToNewest

    fun formattedDiffsWithinBudget(
        budgetChars: Int = ContextBudget.RECENT_EDITS_CHARS,
        budgetTokens: Int = ContextBudget.RECENT_EDITS_TOKENS,
    ): RecentEditSelection {
        var usedChars = 0
        var droppedItems = 0
        var droppedChars = 0
        val keptNewestToOldest = mutableListOf<String>()

        for (edit in edits.asReversed()) {
            val diff = edit.formattedDiff ?: continue
            val cost = diff.length
            if (cost > budgetChars - usedChars) {
                droppedItems++
                droppedChars += cost
                continue
            }

            keptNewestToOldest.add(diff)
            usedChars += cost
        }

        return RecentEditSelection(
            diffsOldestToNewest = keptNewestToOldest.asReversed(),
            budget =
                ContextBudgetDebugInfo(
                    budgetTokens = budgetTokens,
                    budgetChars = budgetChars,
                    usedChars = usedChars,
                    droppedChars = droppedChars,
                    keptItems = keptNewestToOldest.size,
                    droppedItems = droppedItems,
                ),
        )
    }

    private fun RecentEdit.canCoalesceWith(candidate: RecentEdit): Boolean {
        if (filePath != candidate.filePath) return false
        if (changedLines.newRange.lineGapTo(candidate.changedLines.oldRange) > MAX_COALESCE_GAP_LINES) return false
        return canApply(candidate)
    }

    private fun dropOverlappingEdits(candidate: RecentEdit) {
        edits.removeAll {
            it.filePath == candidate.filePath &&
                it.changedLines.newRange.lineGapTo(candidate.changedLines.oldRange) <= MAX_COALESCE_GAP_LINES
        }
    }

    private fun addMergedOrSplit(
        previous: RecentEdit,
        candidate: RecentEdit,
        patchedNewText: String,
    ) {
        val combined = RecentEdit(previous.filePath, previous.originalText, patchedNewText)
        if (keepEdit(combined)) {
            edits.add(combined)
            return
        }

        edits.add(previous)
        edits.add(candidate)
    }

    private fun RecentEdit.canApply(candidate: RecentEdit): Boolean {
        val previousCurrentLines = sliceLines(newText, candidate.changedLines.oldRange) ?: return false
        val candidateOriginalLines = sliceLines(candidate.originalText, candidate.changedLines.oldRange) ?: return false
        return previousCurrentLines == candidateOriginalLines
    }

    private fun RecentEdit.apply(candidate: RecentEdit): String? {
        val oldRange = candidate.changedLines.oldRange
        val previousLines = newText.split('\n')
        if (oldRange.start !in 0..previousLines.size || oldRange.end !in 0..previousLines.size) return null

        val newLines = sliceLines(candidate.newText, candidate.changedLines.newRange) ?: return null
        return buildList {
            addAll(previousLines.subList(0, oldRange.start))
            addAll(newLines)
            addAll(previousLines.subList(oldRange.end, previousLines.size))
        }.joinToString("\n")
    }

    private fun sliceLines(
        text: String,
        range: LineRange,
    ): List<String>? {
        val lines = text.split('\n')
        if (range.start !in 0..lines.size || range.end !in 0..lines.size || range.start > range.end) return null
        return lines.subList(range.start, range.end)
    }

    private fun keepEdit(edit: RecentEdit): Boolean {
        val diff = edit.formattedDiff ?: return false
        return diff.length <= MAX_DIFF_CHARS && changedLineCount(diff) <= MAX_CHANGED_LINES
    }

    private fun trimOldestEdits() {
        while (edits.size > MAX_EDITS) {
            edits.removeAt(0)
        }
    }

    private fun changedLineCount(diff: String): Int =
        diff
            .lineSequence()
            .count { (it.startsWith("+") && !it.startsWith("+++")) || (it.startsWith("-") && !it.startsWith("---")) }

    companion object {
        private const val MAX_EDITS = 8
        private const val MAX_DIFF_CHARS = 20_000
        private const val MAX_CHANGED_LINES = 24
        private const val MAX_COALESCE_GAP_LINES = 5
    }
}

data class RecentEditSelection(
    val diffsOldestToNewest: List<String>,
    val budget: ContextBudgetDebugInfo,
)

private data class ChangedLineRanges(
    val oldRange: LineRange,
    val newRange: LineRange,
) {
    companion object {
        fun between(
            originalText: String,
            newText: String,
        ): ChangedLineRanges {
            val oldLines = originalText.split('\n')
            val newLines = newText.split('\n')

            var prefix = 0
            while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) {
                prefix++
            }

            var suffix = 0
            while (
                suffix < oldLines.size - prefix &&
                suffix < newLines.size - prefix &&
                oldLines[oldLines.lastIndex - suffix] == newLines[newLines.lastIndex - suffix]
            ) {
                suffix++
            }

            return ChangedLineRanges(
                oldRange = LineRange(prefix, oldLines.size - suffix),
                newRange = LineRange(prefix, newLines.size - suffix),
            )
        }
    }
}

private data class LineRange(
    val start: Int,
    val end: Int,
) {
    init {
        require(start <= end)
    }

    fun lineGapTo(other: LineRange): Int =
        when {
            end < other.start -> other.start - end
            other.end < start -> start - other.end
            else -> 0
        }
}

object UnifiedDiffBuilder {
    private const val CONTEXT_LINES = 2
    private const val MAX_SMART_DIFF_CELLS = 200_000

    fun build(
        filePath: String,
        originalText: String,
        newText: String,
    ): String? {
        if (originalText == newText) return null

        val oldLines = originalText.split('\n')
        val newLines = newText.split('\n')
        val changedLines = ChangedLineRanges.between(originalText, newText)
        val oldChangeStart = changedLines.oldRange.start
        val oldChangeEnd = changedLines.oldRange.end
        val newChangeStart = changedLines.newRange.start
        val newChangeEnd = changedLines.newRange.end

        val hunkOldStart = max(0, oldChangeStart - CONTEXT_LINES)
        val hunkNewStart = max(0, newChangeStart - CONTEXT_LINES)
        val hunkOldEnd = min(oldLines.size, oldChangeEnd + CONTEXT_LINES)
        val hunkNewEnd = min(newLines.size, newChangeEnd + CONTEXT_LINES)

        val oldLen = hunkOldEnd - hunkOldStart
        val newLen = hunkNewEnd - hunkNewStart
        val oldStartDisplay = hunkOldStart + 1
        val newStartDisplay = hunkNewStart + 1

        return buildString {
            appendLine("--- $filePath")
            appendLine("+++ $filePath")
            appendLine("@@ -$oldStartDisplay,$oldLen +$newStartDisplay,$newLen @@")

            oldLines.subList(hunkOldStart, oldChangeStart).forEach { appendLine(" $it") }
            appendChangedLines(
                oldLines = oldLines.subList(oldChangeStart, oldChangeEnd),
                newLines = newLines.subList(newChangeStart, newChangeEnd),
            )
            oldLines.subList(oldChangeEnd, hunkOldEnd).forEach { appendLine(" $it") }
        }.trimEnd()
    }

    private fun StringBuilder.appendChangedLines(
        oldLines: List<String>,
        newLines: List<String>,
    ) {
        val cells = oldLines.size.toLong() * newLines.size.toLong()
        if (cells > MAX_SMART_DIFF_CELLS) {
            oldLines.forEach { appendLine("-$it") }
            newLines.forEach { appendLine("+$it") }
            return
        }

        for (line in buildLineDiff(oldLines, newLines)) {
            appendLine("${line.kind.marker}${line.text}")
        }
    }

    private fun buildLineDiff(
        oldLines: List<String>,
        newLines: List<String>,
    ): List<DiffLine> {
        val suffixLengths = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
        for (oldIndex in oldLines.indices.reversed()) {
            for (newIndex in newLines.indices.reversed()) {
                suffixLengths[oldIndex][newIndex] =
                    if (oldLines[oldIndex] == newLines[newIndex]) {
                        suffixLengths[oldIndex + 1][newIndex + 1] + 1
                    } else {
                        max(suffixLengths[oldIndex + 1][newIndex], suffixLengths[oldIndex][newIndex + 1])
                    }
            }
        }

        val diff = mutableListOf<DiffLine>()
        var oldIndex = 0
        var newIndex = 0
        while (oldIndex < oldLines.size && newIndex < newLines.size) {
            if (oldLines[oldIndex] == newLines[newIndex]) {
                diff.add(DiffLine(DiffLineKind.CONTEXT, oldLines[oldIndex]))
                oldIndex++
                newIndex++
            } else if (suffixLengths[oldIndex + 1][newIndex] >= suffixLengths[oldIndex][newIndex + 1]) {
                diff.add(DiffLine(DiffLineKind.DELETE, oldLines[oldIndex]))
                oldIndex++
            } else {
                diff.add(DiffLine(DiffLineKind.INSERT, newLines[newIndex]))
                newIndex++
            }
        }
        while (oldIndex < oldLines.size) {
            diff.add(DiffLine(DiffLineKind.DELETE, oldLines[oldIndex]))
            oldIndex++
        }
        while (newIndex < newLines.size) {
            diff.add(DiffLine(DiffLineKind.INSERT, newLines[newIndex]))
            newIndex++
        }
        return diff
    }

    private data class DiffLine(
        val kind: DiffLineKind,
        val text: String,
    )

    private enum class DiffLineKind(
        val marker: String,
    ) {
        CONTEXT(" "),
        DELETE("-"),
        INSERT("+"),
    }
}
