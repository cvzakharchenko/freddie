package com.github.cvzakharchenko.freddie.presentation

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle

class InlineDiffPreviewPresenter : SuggestionPresenter {
    private var current: InlineDiffPreview? = null

    override fun show(suggestion: MercurySuggestion): PresentedSuggestion? {
        current?.dispose()
        val preview = InlineDiffPreview.create(suggestion) ?: return null
        current = preview
        return preview
    }

    override fun dispose() {
        current?.dispose()
        current = null
    }
}

private class InlineDiffPreview(
    override val suggestion: MercurySuggestion,
    private val highlighters: List<RangeHighlighter>,
    private val inlays: List<Inlay<*>>,
) : PresentedSuggestion {
    override fun dispose() {
        highlighters.forEach { it.dispose() }
        inlays.forEach { Disposer.dispose(it) }
    }

    companion object {
        fun create(suggestion: MercurySuggestion): InlineDiffPreview? {
            val editor = suggestion.editor
            val slice = ChangedSlice.between(suggestion.originalText, suggestion.replacementText)
            if (slice.originalChange.isEmpty() && slice.replacementChange.isEmpty()) return null

            val highlighters = mutableListOf<RangeHighlighter>()
            val inlays = mutableListOf<Inlay<*>>()
            val changeStartOffset = suggestion.startOffset + slice.prefixLength
            val changeEndOffset = suggestion.endOffset - slice.suffixLength

            if (changeStartOffset < changeEndOffset) {
                highlighters.add(
                    editor.markupModel.addRangeHighlighter(
                        changeStartOffset,
                        changeEndOffset,
                        HighlighterLayer.SELECTION - 1,
                        deletionAttributes(),
                        HighlighterTargetArea.EXACT_RANGE,
                    ),
                )
            }

            if (slice.replacementChange.isNotEmpty()) {
                addReplacementInlays(editor, changeStartOffset, slice.replacementChange, inlays)
            }

            if (highlighters.isEmpty() && inlays.isEmpty()) return null
            return InlineDiffPreview(suggestion, highlighters, inlays)
        }

        private fun addReplacementInlays(
            editor: Editor,
            offset: Int,
            text: String,
            inlays: MutableList<Inlay<*>>,
        ) {
            val lines = text.split('\n')
            val firstLine = lines.firstOrNull().orEmpty()
            if (firstLine.isNotEmpty()) {
                editor.inlayModel.addInlineElement(offset, true, AddedTextRenderer(listOf(firstLine)))?.let { inlays.add(it) }
            }

            val blockLines = lines.drop(1)
            if (blockLines.isNotEmpty()) {
                editor.inlayModel.addBlockElement(offset, true, false, 0, AddedTextRenderer(blockLines))?.let { inlays.add(it) }
            }
        }

        private fun deletionAttributes(): TextAttributes =
            TextAttributes().apply {
                backgroundColor = JBColor(Color(0xF9D8D6), Color(0x5A2528))
                effectColor = JBColor(Color(0xB3261E), Color(0xFFB4AB))
                effectType = EffectType.STRIKEOUT
            }
    }
}

private data class ChangedSlice(
    val prefixLength: Int,
    val suffixLength: Int,
    val originalChange: String,
    val replacementChange: String,
) {
    companion object {
        fun between(
            original: String,
            replacement: String,
        ): ChangedSlice {
            var prefix = 0
            val maxPrefix = minOf(original.length, replacement.length)
            while (prefix < maxPrefix && original[prefix] == replacement[prefix]) {
                prefix++
            }

            var suffix = 0
            while (
                suffix < original.length - prefix &&
                suffix < replacement.length - prefix &&
                original[original.lastIndex - suffix] == replacement[replacement.lastIndex - suffix]
            ) {
                suffix++
            }

            return ChangedSlice(
                prefixLength = prefix,
                suffixLength = suffix,
                originalChange = original.substring(prefix, original.length - suffix),
                replacementChange = replacement.substring(prefix, replacement.length - suffix),
            )
        }
    }
}

private class AddedTextRenderer(
    private val lines: List<String>,
) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val metrics = inlay.editor.contentComponent.getFontMetrics(font(inlay.editor))
        return lines.maxOfOrNull { metrics.stringWidth(displayLine(it)) }?.coerceAtLeast(metrics.stringWidth(" ")) ?: 0
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = inlay.editor.lineHeight * lines.size.coerceAtLeast(1)

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics,
        targetRegion: Rectangle,
        textAttributes: TextAttributes,
    ) {
        val editor = inlay.editor
        val font = font(editor)
        val metrics = editor.contentComponent.getFontMetrics(font)
        g.font = font
        g.color = JBColor(Color(0xD9F4DF), Color(0x173F28))
        g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width.coerceAtLeast(1), targetRegion.height)
        g.color = JBColor(Color(0x0F6D34), Color(0x8FE3A7))
        lines.forEachIndexed { index, line ->
            g.drawString(displayLine(line), targetRegion.x, targetRegion.y + metrics.ascent + index * editor.lineHeight)
        }
    }

    private fun font(editor: Editor): Font = editor.colorsScheme.getFont(EditorFontType.PLAIN)

    private fun displayLine(line: String): String = line.ifEmpty { " " }
}
