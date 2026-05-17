package com.github.cvzakharchenko.freddie.presentation

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes

internal object SuggestionPreviewStyles {
    fun attributes(
        editor: Editor,
        kind: SuggestionTextSegmentKind,
    ): TextAttributes =
        when (kind) {
            SuggestionTextSegmentKind.EQUAL -> inlineSuggestionAttributes(editor)
            SuggestionTextSegmentKind.INSERTED -> insertedSuggestionAttributes(editor)
        }

    fun createDeletedHighlighters(
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
