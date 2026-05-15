package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.controller.SuggestionCaretMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionCaretMapperTest {
    @Test
    fun `keeps caret after unchanged prefix when suggestion edits after caret`() {
        val original = "before\ncaret"
        val replacement = "before\ncaret\ninserted"
        val caret = "before".length

        assertEquals(caret, SuggestionCaretMapper.mapCaretOffset(original, replacement, caret))
    }

    @Test
    fun `shifts caret with unchanged suffix when suggestion inserts before caret`() {
        val original = "one\ncaret line\nnext\n"
        val replacement = "one\ninserted\ncaret line\nnext\n"
        val caret = original.indexOf("\nnext")

        assertEquals(replacement.indexOf("\nnext"), SuggestionCaretMapper.mapCaretOffset(original, replacement, caret))
    }

    @Test
    fun `keeps caret at the end of a replaced current line`() {
        val original = "\t\t\treturn \"\";\n\t\tcase AuthStatus::ErrorPrefabHash:\n"
        val replacement = "\t\t\treturn \"predefined replicables hashes mismatch\";\n\t\tcase AuthStatus::ErrorPrefabHash:\n"
        val caret = original.indexOf('\n')

        assertEquals(replacement.indexOf('\n'), SuggestionCaretMapper.mapCaretOffset(original, replacement, caret))
    }
}
