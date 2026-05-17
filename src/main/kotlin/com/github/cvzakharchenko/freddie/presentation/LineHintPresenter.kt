package com.github.cvzakharchenko.freddie.presentation

import com.github.cvzakharchenko.freddie.settings.FreddieSettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

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
    private val inlays: List<Inlay<*>>,
    private val deletedHighlighters: List<RangeHighlighter>,
) : PresentedSuggestion {
    override val presentationDescription: String =
        "Line hint (${inlays.size} block inlay(s), ${deletedHighlighters.size} deletion highlighter(s), geometric marker)"

    override fun dispose() {
        inlays.forEach { inlay ->
            if (inlay.isValid) {
                Disposer.dispose(inlay)
            }
        }
        if (!suggestion.editor.isDisposed) {
            deletedHighlighters.forEach { suggestion.editor.markupModel.removeHighlighter(it) }
        }
    }

    companion object {
        fun create(suggestion: MercurySuggestion): LineHintPreview? {
            val inlays = mutableListOf<Inlay<*>>()
            val deletedHighlighters = mutableListOf<RangeHighlighter>()
            suggestion.editor.inlayModel.execute(true) {
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

                    val inlay =
                        suggestion.editor.inlayModel.addBlockElement(
                            plan.renderOffset,
                            InlayProperties()
                                .relatesToPrecedingText(!plan.showAbove)
                                .showAbove(plan.showAbove)
                                .priority(0),
                            LineHintRenderer(plan.lines),
                        )
                    if (inlay != null) {
                        inlays.add(inlay)
                    }
                }
            }

            return if (inlays.isNotEmpty() || deletedHighlighters.isNotEmpty()) {
                LineHintPreview(suggestion, inlays, deletedHighlighters)
            } else {
                null
            }
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

private class LineHintRenderer(
    private val lines: List<List<SuggestionTextSegment>>,
) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val editor = inlay.editor
        val width =
            lines.maxOfOrNull { line ->
                measureLine(editor, line) + RIGHT_PADDING
            } ?: RIGHT_PADDING
        return width.coerceAtLeast(1)
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int =
        VERTICAL_PADDING * 2 +
            lines.size * BAR_HEIGHT +
            (lines.size - 1).coerceAtLeast(0) * BAR_GAP

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics2D,
        targetRegion: Rectangle2D,
        textAttributes: TextAttributes,
    ) {
        val editor = inlay.editor
        val graphics = g.create() as Graphics2D
        try {
            val baseX = targetRegion.x.toInt()
            for ((lineIndex, line) in lines.withIndex()) {
                paintLine(
                    editor = editor,
                    graphics = graphics,
                    line = line,
                    x = baseX,
                    y = targetRegion.y.toInt() + VERTICAL_PADDING + lineIndex * (BAR_HEIGHT + BAR_GAP),
                )
            }
        } finally {
            graphics.dispose()
        }
    }

    private fun paintLine(
        editor: Editor,
        graphics: Graphics2D,
        line: List<SuggestionTextSegment>,
        x: Int,
        y: Int,
    ) {
        var currentX = x
        for (segment in line) {
            val attributes = SuggestionPreviewStyles.attributes(editor, segment.kind)
            val fontType = attributes.fontType.takeIf { it >= 0 } ?: Font.PLAIN
            val segmentWidth = measureText(editor, segment.text, currentX - x, fontType)
            if (segmentWidth > 0) {
                graphics.color = markerColor(editor, segment.kind, attributes)
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
        attributes: TextAttributes,
    ): Color =
        when (kind) {
            SuggestionTextSegmentKind.EQUAL ->
                attributes.foregroundColor
                    ?: attributes.backgroundColor
                    ?: editor.colorsScheme.defaultForeground
            SuggestionTextSegmentKind.INSERTED ->
                FreddieSettings.getInstance().customLineHintInsertedColor
                    ?: attributes.backgroundColor
                    ?: attributes.foregroundColor
                    ?: editor.colorsScheme.defaultForeground
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
        private val BAR_GAP = JBUI.scale(2)
        private val VERTICAL_PADDING = JBUI.scale(2)
        private val RIGHT_PADDING = JBUI.scale(16)
    }
}
