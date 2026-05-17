package com.github.cvzakharchenko.freddie.presentation

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.LineSeparatorRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font
import java.awt.Graphics

class LineHintPresenter : SuggestionPresenter {
    private var current: LineHintPreview? = null

    override fun show(suggestion: MercurySuggestion): PresentedSuggestion? {
        current?.dispose()
        current = null
        val preview = LineHintPreview.create(suggestion) ?: return null
        current = preview
        return preview
    }

    override fun dispose() {
        current?.dispose()
        current = null
    }
}

private class LineHintPreview(
    override val suggestion: MercurySuggestion,
    private val lineHighlighters: List<RangeHighlighter>,
    private val deletedHighlighters: List<RangeHighlighter>,
) : PresentedSuggestion {
    override val presentationDescription: String =
        "Line hint (${lineHighlighters.size} line separator(s), ${deletedHighlighters.size} deletion highlighter(s))"

    override fun dispose() {
        if (!suggestion.editor.isDisposed) {
            lineHighlighters.forEach { suggestion.editor.markupModel.removeHighlighter(it) }
            deletedHighlighters.forEach { suggestion.editor.markupModel.removeHighlighter(it) }
        }
    }

    companion object {
        fun create(suggestion: MercurySuggestion): LineHintPreview? {
            val lineHighlighters = mutableListOf<RangeHighlighter>()
            val deletedHighlighters = mutableListOf<RangeHighlighter>()
            for (changedBlock in ChangedBlock.allBetween(suggestion.originalText, suggestion.replacementText)) {
                val blockDiff =
                    SuggestionTextDiff.between(
                        original = suggestion.originalText,
                        replacement = suggestion.replacementText,
                        block = changedBlock,
                    )
                deletedHighlighters.addAll(
                    SuggestionPreviewStyles.createDeletedHighlighters(
                        suggestion = suggestion,
                        deletedRanges = blockDiff.deletedRanges,
                    ),
                )

                val displaySegments = SuggestionTextDiff.limitVisibleLines(blockDiff.replacementSegments)
                val displayText = displaySegments.joinToString(separator = "") { it.text }
                if (displayText.isBlank()) continue

                val plan =
                    LineHintPlan.create(
                        documentText = suggestion.document.charsSequence,
                        editableStartOffset = suggestion.startOffset,
                        changedBlock = changedBlock,
                        displaySegments = displaySegments,
                    ) ?: continue

                val lineHighlighter =
                    suggestion.editor.markupModel.addLineHighlighter(
                        lineNumberAt(suggestion.document.charsSequence, plan.renderOffset),
                        HighlighterLayer.ADDITIONAL_SYNTAX,
                        null,
                    )
                lineHighlighter.setLineSeparatorPlacement(
                    if (plan.showAbove) SeparatorPlacement.TOP else SeparatorPlacement.BOTTOM,
                )
                lineHighlighter.setLineSeparatorRenderer(
                    LineHintSeparatorRenderer(
                        editor = suggestion.editor,
                        anchorLine = lineNumberAt(suggestion.document.charsSequence, plan.renderOffset),
                        lines = plan.lines,
                    ),
                )
                lineHighlighter.setLineSeparatorColor(
                    SuggestionPreviewStyles.lineHintInsertedColor(suggestion.editor)
                        ?: suggestion.editor.colorsScheme.defaultForeground,
                )
                lineHighlighters.add(lineHighlighter)
            }

            return if (lineHighlighters.isNotEmpty() || deletedHighlighters.isNotEmpty()) {
                LineHintPreview(suggestion, lineHighlighters, deletedHighlighters)
            } else {
                null
            }
        }

        private fun lineNumberAt(
            text: CharSequence,
            offset: Int,
        ): Int {
            val safeOffset = offset.coerceIn(0, text.length)
            if (safeOffset == text.length && text.isNotEmpty() && text.last() == '\n') {
                return lineNumberAt(text, safeOffset - 1)
            }

            var line = 0
            for (index in 0 until safeOffset) {
                if (text[index] == '\n') {
                    line++
                }
            }
            return line
        }
    }
}

internal data class LineHintPlan(
    val renderOffset: Int,
    val showAbove: Boolean,
    val lines: List<List<SuggestionTextSegment>>,
) {
    companion object {
        fun create(
            documentText: CharSequence,
            editableStartOffset: Int,
            changedBlock: ChangedBlock,
            displaySegments: List<SuggestionTextSegment>,
        ): LineHintPlan? {
            val lines = LineHintTextLayout.splitIntoLines(displaySegments)
            if (lines.isEmpty()) return null
            val placement =
                LineSuggestionPlacementPlanner.create(
                    documentText = documentText,
                    editableStartOffset = editableStartOffset,
                    changedBlock = changedBlock,
                )
            return LineHintPlan(
                renderOffset = placement.renderOffset,
                showAbove = placement.showAbove,
                lines = lines,
            )
        }
    }
}

internal object LineHintTextLayout {
    fun splitIntoLines(segments: List<SuggestionTextSegment>): List<List<SuggestionTextSegment>> {
        val lines = mutableListOf<MutableList<SuggestionTextSegment>>(mutableListOf())
        for (segment in segments) {
            var start = 0
            while (start <= segment.text.length) {
                val newlineIndex = segment.text.indexOf('\n', start)
                val end = if (newlineIndex >= 0) newlineIndex else segment.text.length
                addSegment(lines.last(), segment.text.substring(start, end), segment.kind)
                if (newlineIndex < 0) break
                lines.add(mutableListOf())
                start = newlineIndex + 1
            }
        }

        while (lines.isNotEmpty() && lines.last().isEmpty()) {
            lines.removeAt(lines.lastIndex)
        }
        return lines
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

private class LineHintSeparatorRenderer(
    private val editor: Editor,
    private val anchorLine: Int,
    private val lines: List<List<SuggestionTextSegment>>,
) : LineSeparatorRenderer {
    override fun drawLine(
        g: Graphics,
        x1: Int,
        x2: Int,
        y: Int,
    ) {
        val startX = editor.logicalPositionToXY(LogicalPosition(anchorLine, 0)).x
        if (lines.size == 1) {
            paintSegmentedLine(g, lines.first(), startX, y)
        } else {
            val width = lines.maxOfOrNull { measureLine(editor, it) } ?: 0
            if (width > 0) {
                g.color = SuggestionPreviewStyles.lineHintInsertedColor(editor) ?: editor.colorsScheme.defaultForeground
                g.fillRect(startX, y, width + RIGHT_PADDING, BAR_HEIGHT)
            }
        }
    }

    private fun paintSegmentedLine(
        graphics: Graphics,
        line: List<SuggestionTextSegment>,
        x: Int,
        y: Int,
    ) {
        var currentX = x
        for (segment in line) {
            val attributes = SuggestionPreviewStyles.attributes(editor, segment.kind)
            val fontType = attributes.fontType.takeIf { it >= 0 } ?: Font.PLAIN
            val segmentWidth = measureText(editor, segment.text, currentX - x, fontType)
            val color = markerColor(editor, segment.kind)
            if (segmentWidth > 0 && color != null) {
                graphics.color = color
                graphics.fillRect(currentX, y, segmentWidth, BAR_HEIGHT)
            }
            currentX += segmentWidth
        }
    }

    private fun measureLine(
        editor: Editor,
        line: List<SuggestionTextSegment>,
    ): Int {
        var width = 0
        for (segment in line) {
            val attributes = SuggestionPreviewStyles.attributes(editor, segment.kind)
            val fontType = attributes.fontType.takeIf { it >= 0 } ?: Font.PLAIN
            width += measureText(editor, segment.text, width, fontType)
        }
        return width
    }

    private fun markerColor(
        editor: Editor,
        kind: SuggestionTextSegmentKind,
    ): Color? =
        when (kind) {
            SuggestionTextSegmentKind.EQUAL ->
                SuggestionPreviewStyles.lineHintMatchedColor()
            SuggestionTextSegmentKind.INSERTED ->
                SuggestionPreviewStyles.lineHintInsertedColor(editor) ?: editor.colorsScheme.defaultForeground
        }

    private fun measureText(
        editor: Editor,
        text: String,
        x: Int,
        fontType: Int,
    ): Int =
        EditorUtil.textWidth(editor, text, 0, text.length, fontType, x)

    companion object {
        private val BAR_HEIGHT = JBUI.scale(1)
        private val RIGHT_PADDING = JBUI.scale(16)
    }
}
