package com.github.cvzakharchenko.freddie.presentation

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionTextElement
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
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
                deletedHighlighters.addAll(createDeletedHighlighters(suggestion, blockDiff.deletedRanges))

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
                            inlineSuggestionAttributes(suggestion.editor, segment.kind),
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

        private fun createDeletedHighlighters(
            suggestion: MercurySuggestion,
            deletedRanges: List<SuggestionDeletedRange>,
        ): List<RangeHighlighter> =
            deletedRanges.flatMap { range ->
                val startOffset = suggestion.startOffset + range.startOffsetInOriginal
                val endOffset = suggestion.startOffset + range.endOffsetInOriginal
                if (startOffset < endOffset && endOffset <= suggestion.document.textLength) {
                    DiffDrawUtil.createInlineHighlighter(
                        suggestion.editor,
                        startOffset,
                        endOffset,
                        TextDiffType.DELETED,
                    )
                } else {
                    emptyList()
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

        private fun inlineSuggestionAttributes(
            editor: Editor,
            kind: SuggestionTextSegmentKind,
        ): TextAttributes =
            when (kind) {
                SuggestionTextSegmentKind.EQUAL -> inlineSuggestionAttributes(editor)
                SuggestionTextSegmentKind.INSERTED -> insertedSuggestionAttributes(editor)
            }

        private fun inlineSuggestionAttributes(editor: Editor): TextAttributes =
            editor.colorsScheme
                .getAttributes(DefaultLanguageHighlighterColors.INLINE_SUGGESTION)
                .clone()

        private fun insertedSuggestionAttributes(editor: Editor): TextAttributes {
            val attributes = inlineSuggestionAttributes(editor)
            val diffAttributes = editor.colorsScheme.getAttributes(DiffColors.DIFF_INSERTED)
            attributes.backgroundColor = diffAttributes.backgroundColor ?: TextDiffType.INSERTED.getColor(editor)
            attributes.effectColor = diffAttributes.effectColor
            attributes.effectType = diffAttributes.effectType
            attributes.setAdditionalEffects(diffAttributes.additionalEffects)
            return attributes
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

            val anchorOffset =
                (editableStartOffset + changedBlock.anchorOffsetInOriginal)
                    .coerceIn(0, documentText.length)
            val changedOriginalLineCount = changedBlock.originalEndLineExclusive - changedBlock.originalStartLine
            if (changedOriginalLineCount > 0) {
                val firstChangedLine = lineNumberAt(documentText, anchorOffset)
                val lastChangedLine =
                    (firstChangedLine + changedOriginalLineCount - 1)
                        .coerceAtMost(lastLineNumber(documentText))
                return LineGhostTextPlan(
                    renderOffset = lineEndOffset(documentText, lastChangedLine),
                    text = "\n$limitedText",
                )
            }

            val anchorLine = lineNumberAt(documentText, anchorOffset)
            return if (anchorLine > 0) {
                LineGhostTextPlan(
                    renderOffset = lineEndOffset(documentText, anchorLine - 1),
                    text = "\n$limitedText",
                )
            } else {
                LineGhostTextPlan(
                    renderOffset = anchorOffset,
                    text = limitedText.ensureTrailingLineFeed(),
                )
            }
        }

        private fun lineNumberAt(
            text: CharSequence,
            offset: Int,
        ): Int {
            val safeOffset = offset.coerceIn(0, text.length)
            var line = 0
            for (index in 0 until safeOffset) {
                if (text[index] == '\n') line++
            }
            return line
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

        private fun lineEndOffset(
            text: CharSequence,
            lineNumber: Int,
        ): Int {
            val startOffset = lineStartOffset(text, lineNumber)
            var index = startOffset
            while (index < text.length && text[index] != '\n') {
                index++
            }
            return index
        }

        private fun lineStartOffset(
            text: CharSequence,
            lineNumber: Int,
        ): Int {
            if (lineNumber <= 0) return 0

            var currentLine = 0
            for (index in text.indices) {
                if (text[index] == '\n') {
                    currentLine++
                    if (currentLine == lineNumber) return index + 1
                }
            }
            return text.length
        }

        private fun lastLineNumber(text: CharSequence): Int = lineNumberAt(text, text.length)
    }
}
