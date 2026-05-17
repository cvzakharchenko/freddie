package com.github.cvzakharchenko.freddie.presentation

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionTextElement
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer

class LineGhostTextPresenter : SuggestionPresenter {
    private var current: LineGhostTextPreview? = null

    override fun show(suggestion: MercurySuggestion): PresentedSuggestion? {
        current?.dispose()
        current = null
        val preview = LineGhostTextPreview.create(suggestion) ?: return null
        current = preview
        return preview
    }

    override fun dispose() {
        current?.dispose()
        current = null
    }
}

private class LineGhostTextPreview(
    override val suggestion: MercurySuggestion,
    private val presentables: List<InlineCompletionElement.Presentable>,
    private val deletedHighlighters: List<RangeHighlighter>,
) : PresentedSuggestion {
    override val presentationDescription: String =
        "Ghost text (${presentables.size} inline element(s), ${deletedHighlighters.size} deletion highlighter(s))"

    override fun dispose() {
        presentables.forEach { Disposer.dispose(it) }
        if (!suggestion.editor.isDisposed) {
            deletedHighlighters.forEach { suggestion.editor.markupModel.removeHighlighter(it) }
        }
    }

    companion object {
        fun create(suggestion: MercurySuggestion): LineGhostTextPreview? {
            val presentables = mutableListOf<InlineCompletionElement.Presentable>()
            val deletedHighlighters = mutableListOf<RangeHighlighter>()
            for (changedBlock in ChangedBlock.allBetween(suggestion.originalText, suggestion.replacementText)) {
                val blockDiff =
                    SuggestionTextDiff.between(
                        original = suggestion.originalText,
                        replacement = suggestion.replacementText,
                        block = changedBlock,
                    )
                deletedHighlighters.addAll(SuggestionPreviewStyles.createDeletedHighlighters(suggestion, blockDiff.deletedRanges))

                val displaySegments = SuggestionTextDiff.limitVisibleLines(blockDiff.replacementSegments)
                val displayText = displaySegments.joinToString(separator = "") { it.text }
                if (displayText.isBlank()) continue

                val plan =
                    LineGhostTextPlan.create(
                        documentText = suggestion.document.charsSequence,
                        editableStartOffset = suggestion.startOffset,
                        changedBlock = changedBlock,
                        displayText = displayText,
                    ) ?: continue

                for (segment in segmentsWithPlanAffixes(plan.text, displayText, displaySegments)) {
                    val presentable =
                        InlineCompletionTextElement(
                            segment.text,
                            SuggestionPreviewStyles.ghostTextAttributes(suggestion.editor, segment.kind),
                        ).toPresentable()

                    presentable.render(suggestion.editor, plan.renderOffset)
                    if (presentable.isVisible()) {
                        presentables.add(presentable)
                    } else {
                        Disposer.dispose(presentable)
                    }
                }
            }

            return if (presentables.isNotEmpty() || deletedHighlighters.isNotEmpty()) {
                LineGhostTextPreview(suggestion, presentables, deletedHighlighters)
            } else {
                null
            }
        }

        private fun segmentsWithPlanAffixes(
            plannedText: String,
            displayText: String,
            displaySegments: List<SuggestionTextSegment>,
        ): List<SuggestionTextSegment> {
            if (plannedText == displayText) return displaySegments

            val displayStart = plannedText.indexOf(displayText).takeIf { it >= 0 }
            if (displayStart == null) {
                return listOf(SuggestionTextSegment(plannedText, SuggestionTextSegmentKind.EQUAL))
            }

            val result = mutableListOf<SuggestionTextSegment>()
            addSegment(result, plannedText.substring(0, displayStart), SuggestionTextSegmentKind.EQUAL)
            displaySegments.forEach { addSegment(result, it.text, it.kind) }
            addSegment(
                result,
                plannedText.substring(displayStart + displayText.length),
                SuggestionTextSegmentKind.EQUAL,
            )
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
    }
}

internal data class LineGhostTextPlan(
    val renderOffset: Int,
    val text: String,
) {
    companion object {
        private const val MAX_VISIBLE_LINES = 40

        fun create(
            documentText: CharSequence,
            editableStartOffset: Int,
            changedBlock: ChangedBlock,
            displayText: String,
        ): LineGhostTextPlan? {
            val limitedText = limitVisibleLines(displayText)
            if (limitedText.isBlank()) return null

            val placement =
                LineSuggestionPlacementPlanner.create(
                    documentText = documentText,
                    editableStartOffset = editableStartOffset,
                    changedBlock = changedBlock,
                )
            return LineGhostTextPlan(
                renderOffset = placement.renderOffset,
                text = if (placement.showAbove) limitedText.ensureTrailingLineFeed() else "\n$limitedText",
            )
        }

        private fun limitVisibleLines(text: String): String {
            val lines = text.lineSequence().take(MAX_VISIBLE_LINES + 1).toList()
            return if (lines.size > MAX_VISIBLE_LINES) {
                (lines.take(MAX_VISIBLE_LINES) + "...").joinToString("\n")
            } else {
                lines.joinToString("\n")
            }
        }

        private fun String.ensureTrailingLineFeed(): String =
            if (endsWith('\n')) this else "$this\n"
    }
}
