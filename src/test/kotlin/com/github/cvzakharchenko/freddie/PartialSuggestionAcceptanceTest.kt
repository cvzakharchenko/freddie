package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.controller.PartialAcceptKind
import com.github.cvzakharchenko.freddie.controller.PartialSuggestionAcceptance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialSuggestionAcceptanceTest {
    @Test
    fun `block accept inserts the first contiguous changed block`() {
        val result =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = "one\nfour\nseven\n",
                    replacementText = "one\ntwo\nthree\nfour\nfive\nsix\nseven\n",
                    kind = PartialAcceptKind.BLOCK,
                ),
            )

        assertEquals("one\ntwo\nthree\nfour\nseven\n", result.text)
        assertFalse(result.completed)
    }

    @Test
    fun `block accept moves to the next changed block after the first block was accepted`() {
        val replacement = "one\ntwo\nthree\nfour\nfive\nsix\nseven\n"
        val firstResult =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = "one\nfour\nseven\n",
                    replacementText = replacement,
                    kind = PartialAcceptKind.BLOCK,
                ),
            )
        val secondResult =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = firstResult.text,
                    replacementText = replacement,
                    kind = PartialAcceptKind.BLOCK,
                ),
            )

        assertEquals(replacement, secondResult.text)
        assertTrue(secondResult.completed)
    }

    @Test
    fun `block accept replaces the first contiguous changed block`() {
        val result =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = "one\nold two\nold three\nfour\nold six\nseven\n",
                    replacementText = "one\nnew two\nnew three\nfour\nnew six\nseven\n",
                    kind = PartialAcceptKind.BLOCK,
                ),
            )

        assertEquals("one\nnew two\nnew three\nfour\nold six\nseven\n", result.text)
        assertFalse(result.completed)
    }

    @Test
    fun `block accept deletes the first contiguous changed block`() {
        val result =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = "one\ndelete two\ndelete three\nfour\ndelete six\nseven\n",
                    replacementText = "one\nfour\nseven\n",
                    kind = PartialAcceptKind.BLOCK,
                ),
            )

        assertEquals("one\nfour\ndelete six\nseven\n", result.text)
        assertFalse(result.completed)
    }

    @Test
    fun `line accept inserts one suggested line`() {
        val result =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = "one\nfour\n",
                    replacementText = "one\ntwo\nthree\nfour\n",
                    kind = PartialAcceptKind.LINE,
                ),
            )

        assertEquals("one\ntwo\nfour\n", result.text)
        assertFalse(result.completed)
    }

    @Test
    fun `line accept completes after the last remaining changed line`() {
        val result =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = "one\nold\nthree\n",
                    replacementText = "one\nnew\nthree\n",
                    kind = PartialAcceptKind.LINE,
                ),
            )

        assertEquals("one\nnew\nthree\n", result.text)
        assertTrue(result.completed)
    }

    @Test
    fun `line accept deletes one suggested deletion line`() {
        val result =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = "one\ndelete me\nthree\n",
                    replacementText = "one\nthree\n",
                    kind = PartialAcceptKind.LINE,
                ),
            )

        assertEquals("one\nthree\n", result.text)
        assertTrue(result.completed)
    }

    @Test
    fun `word accept inserts the next word from an inserted line`() {
        val result =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = "one\nnext\n",
                    replacementText = "one\n\t\tcase AuthStatus::ErrorReassigned:\nnext\n",
                    kind = PartialAcceptKind.WORD,
                ),
            )

        assertEquals("one\n\t\tcase\nnext\n", result.text)
        assertFalse(result.completed)
    }

    @Test
    fun `word accept inserts a contiguous symbol group`() {
        val result =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = "one\nreturn function\n",
                    replacementText = "one\nreturn function))))\n",
                    kind = PartialAcceptKind.WORD,
                ),
            )

        assertEquals("one\nreturn function))))\n", result.text)
        assertTrue(result.completed)
    }

    @Test
    fun `partial accept preserves CRLF line endings`() {
        val result =
            requireNotNull(
                PartialSuggestionAcceptance.accept(
                    currentText = "one\r\nfour\r\n",
                    replacementText = "one\r\ntwo\r\nthree\r\nfour\r\n",
                    kind = PartialAcceptKind.LINE,
                ),
            )

        assertEquals("one\r\ntwo\r\nfour\r\n", result.text)
        assertFalse(result.completed)
    }
}
