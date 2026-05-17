package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.controller.SuggestionCaretMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionCaretMapperTest {
    @Test
    fun `moves caret to the end of a replaced current line`() {
        val original = "return fu"
        val replacement = "return function();"

        assertEquals(replacement.length, SuggestionCaretMapper.caretAfterLastAppliedBlock(original, replacement))
    }

    @Test
    fun `moves caret before an unchanged suffix on a replaced current line`() {
        val original = "auto service = getServiceManager();"
        val replacement = "auto serviceManager = getServiceManager();"

        assertEquals("auto serviceManager".length, SuggestionCaretMapper.caretAfterLastAppliedBlock(original, replacement))
    }

    @Test
    fun `moves caret to the end of an inserted line before unchanged suffix`() {
        val original = "one\nfour\n"
        val replacement = "one\ntwo\nfour\n"

        assertEquals(replacement.indexOf("\nfour"), SuggestionCaretMapper.caretAfterLastAppliedBlock(original, replacement))
    }

    @Test
    fun `moves caret to the end of the last changed part in the last changed block`() {
        val original = "one\nold two\nthree\nold four\nfive\n"
        val replacement = "one\nnew two\nthree\nnew four\nfive\n"

        assertEquals(
            replacement.indexOf("new four") + "new".length,
            SuggestionCaretMapper.caretAfterLastAppliedBlock(original, replacement),
        )
    }

    @Test
    fun `moves caret to the deletion anchor for deletion-only changes`() {
        val original = "one\ndelete me\nthree\n"
        val replacement = "one\nthree\n"

        assertEquals(replacement.indexOf("three"), SuggestionCaretMapper.caretAfterLastAppliedBlock(original, replacement))
    }

    @Test
    fun `moves caret before an unchanged suffix on a quoted string replacement`() {
        val original = "\t\t\treturn \"\";\n\t\tcase AuthStatus::ErrorPrefabHash:\n"
        val replacement = "\t\t\treturn \"predefined replicables hashes mismatch\";\n\t\tcase AuthStatus::ErrorPrefabHash:\n"

        assertEquals(
            replacement.indexOf("predefined replicables hashes mismatch") +
                "predefined replicables hashes mismatch".length,
            SuggestionCaretMapper.caretAfterLastAppliedBlock(original, replacement),
        )
    }

    @Test
    fun `handles CRLF line endings`() {
        val original = "one\r\nfour\r\n"
        val replacement = "one\r\ntwo\r\nfour\r\n"

        assertEquals(replacement.indexOf("\r\nfour"), SuggestionCaretMapper.caretAfterLastAppliedBlock(original, replacement))
    }
}
