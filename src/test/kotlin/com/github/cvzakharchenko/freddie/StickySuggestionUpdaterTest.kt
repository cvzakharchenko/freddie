package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.controller.StickySuggestionUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickySuggestionUpdaterTest {
    @Test
    fun `advances when typed text consumes suggestion prefix`() {
        val oldText = "return fu"
        val newText = "return fun"

        val update =
            StickySuggestionUpdater.advance(
                oldDocumentText = oldText,
                newDocumentText = newText,
                regionStartOffset = 0,
                regionEndOffset = oldText.length,
                currentRegionText = oldText,
                replacementText = "return function(abc, def);",
                editOffset = oldText.length,
                oldLength = 0,
                newLength = 1,
            )

        requireNotNull(update)
        assertEquals(newText, update.currentText)
        assertEquals(newText.length, update.endOffset)
        assertFalse(update.completed)
    }

    @Test
    fun `advances when editable region has unchanged suffix after caret`() {
        val oldText = "return fu\nnext();"
        val newText = "return fun\nnext();"

        val update =
            StickySuggestionUpdater.advance(
                oldDocumentText = oldText,
                newDocumentText = newText,
                regionStartOffset = 0,
                regionEndOffset = oldText.length,
                currentRegionText = oldText,
                replacementText = "return function(abc, def);\nnext();",
                editOffset = "return fu".length,
                oldLength = 0,
                newLength = 1,
            )

        requireNotNull(update)
        assertEquals(newText, update.currentText)
        assertEquals(newText.length, update.endOffset)
        assertFalse(update.completed)
    }

    @Test
    fun `rejects typed text that diverges from suggestion`() {
        val oldText = "return fu\nnext();"
        val newText = "return fux\nnext();"

        val update =
            StickySuggestionUpdater.advance(
                oldDocumentText = oldText,
                newDocumentText = newText,
                regionStartOffset = 0,
                regionEndOffset = oldText.length,
                currentRegionText = oldText,
                replacementText = "return function(abc, def);\nnext();",
                editOffset = "return fu".length,
                oldLength = 0,
                newLength = 1,
            )

        assertNull(update)
    }

    @Test
    fun `keeps start offset when typing at start of region`() {
        val oldText = "bc();"
        val newText = "abc();"

        val update =
            StickySuggestionUpdater.advance(
                oldDocumentText = oldText,
                newDocumentText = newText,
                regionStartOffset = 0,
                regionEndOffset = oldText.length,
                currentRegionText = oldText,
                replacementText = "abc();",
                editOffset = 0,
                oldLength = 0,
                newLength = 1,
            )

        requireNotNull(update)
        assertEquals(0, update.startOffset)
        assertEquals(newText, update.currentText)
    }

    @Test
    fun `marks suggestion completed when user types the remaining text`() {
        val oldText = "return fu"
        val replacementText = "return function(abc, def);"

        val update =
            StickySuggestionUpdater.advance(
                oldDocumentText = oldText,
                newDocumentText = replacementText,
                regionStartOffset = 0,
                regionEndOffset = oldText.length,
                currentRegionText = oldText,
                replacementText = replacementText,
                editOffset = oldText.length,
                oldLength = 0,
                newLength = replacementText.length - oldText.length,
            )

        requireNotNull(update)
        assertEquals(replacementText, update.currentText)
        assertTrue(update.completed)
    }
}
