package com.github.cvzakharchenko.freddie.presentation

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionTextElement
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer

class LineGhostTextPresenter : SuggestionPresenter {
    private var current: LineGhostTextPreview? = null

    override fun show(suggestion: MercurySuggestion): PresentedSuggestion? {
        current?.dispose()
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
    private val presentable: InlineCompletionElement.Presentable,
) : PresentedSuggestion {
    override fun dispose() {
        Disposer.dispose(presentable)
    }

    companion object {
        fun create(suggestion: MercurySuggestion): LineGhostTextPreview? {
            val changedBlock = ChangedBlock.between(suggestion.originalText, suggestion.replacementText) ?: return null
            val displayText =
                if (changedBlock.isDeletionOnly) {
                    "(delete ${deletedLineCount(changedBlock)} line(s))"
                } else {
                    changedBlock.replacementBlock
                }
            if (displayText.isBlank()) return null

            val plan =
                LineGhostTextPlan.create(
                    documentText = suggestion.document.charsSequence,
                    editableStartOffset = suggestion.startOffset,
                    changedBlock = changedBlock,
                    displayText = displayText,
                ) ?: return null
            val presentable =
                InlineCompletionTextElement(
                    plan.text,
                    inlineSuggestionAttributes(suggestion),
                ).toPresentable()

            presentable.render(suggestion.editor, plan.renderOffset)
            if (!presentable.isVisible()) {
                Disposer.dispose(presentable)
                return null
            }
            return LineGhostTextPreview(suggestion, presentable)
        }

        private fun inlineSuggestionAttributes(suggestion: MercurySuggestion): TextAttributes =
            suggestion.editor.colorsScheme
                .getAttributes(DefaultLanguageHighlighterColors.INLINE_SUGGESTION)
                .clone()

        private fun deletedLineCount(changedBlock: ChangedBlock): Int =
            (changedBlock.originalEndLineExclusive - changedBlock.originalStartLine).coerceAtLeast(1)
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
