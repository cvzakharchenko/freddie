package com.github.cvzakharchenko.freddie.context

import kotlin.math.max
import kotlin.math.min

data class RecentEdit(
    val filePath: String,
    val originalText: String,
    val newText: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val formattedDiff: String? = UnifiedDiffBuilder.build(filePath, originalText, newText)
}

class RecentEditHistory {
    private val edits = ArrayDeque<RecentEdit>()

    fun recordEdit(
        filePath: String,
        originalText: String,
        newText: String,
    ) {
        if (originalText == newText) return

        val candidate = RecentEdit(filePath, originalText, newText)
        val diff = candidate.formattedDiff ?: return
        if (diff.length > MAX_DIFF_CHARS || changedLineCount(diff) > MAX_CHANGED_LINES) return

        val previous = edits.lastOrNull()
        if (previous != null && previous.filePath == filePath && candidate.timestamp - previous.timestamp <= COALESCE_WINDOW_MS) {
            val combined = RecentEdit(filePath, previous.originalText, newText)
            val combinedDiff = combined.formattedDiff
            if (combinedDiff != null && combinedDiff.length <= MAX_DIFF_CHARS && changedLineCount(combinedDiff) <= MAX_CHANGED_LINES) {
                edits.removeLast()
                edits.addLast(combined)
                return
            }
        }

        edits.addLast(candidate)
        while (edits.size > MAX_EDITS) {
            edits.removeFirst()
        }
    }

    fun formattedDiffs(): List<String> = edits.mapNotNull { it.formattedDiff }

    private fun changedLineCount(diff: String): Int =
        diff
            .lineSequence()
            .count { (it.startsWith("+") && !it.startsWith("+++")) || (it.startsWith("-") && !it.startsWith("---")) }

    companion object {
        private const val MAX_EDITS = 8
        private const val MAX_DIFF_CHARS = 20_000
        private const val MAX_CHANGED_LINES = 24
        private const val COALESCE_WINDOW_MS = 1500L
    }
}

object UnifiedDiffBuilder {
    private const val CONTEXT_LINES = 2

    fun build(
        filePath: String,
        originalText: String,
        newText: String,
    ): String? {
        if (originalText == newText) return null

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

        val oldChangeStart = prefix
        val oldChangeEnd = oldLines.size - suffix
        val newChangeStart = prefix
        val newChangeEnd = newLines.size - suffix

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
            oldLines.subList(oldChangeStart, oldChangeEnd).forEach { appendLine("-$it") }
            newLines.subList(newChangeStart, newChangeEnd).forEach { appendLine("+$it") }
            oldLines.subList(oldChangeEnd, hunkOldEnd).forEach { appendLine(" $it") }
        }.trimEnd()
    }
}
