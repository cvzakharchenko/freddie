package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.presentation.ChangedBlock
import com.github.cvzakharchenko.freddie.presentation.SuggestionTextDiff
import com.github.cvzakharchenko.freddie.presentation.SuggestionTextSegmentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionTextDiffTest {
    @Test
    fun `leaves common prefix and suffix of changed identifiers unhighlighted`() {
        val original = "\t\tcase AuthStatus::ErrorKicked:\n"
        val replacement = "\t\tcase AuthStatus::ErrorUserBanned:\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))

        val diff = SuggestionTextDiff.between(original, replacement, block)

        assertEquals(
            listOf(
                SuggestionTextSegmentKind.EQUAL to "\t\tcase AuthStatus::Error",
                SuggestionTextSegmentKind.INSERTED to "UserBann",
                SuggestionTextSegmentKind.EQUAL to "ed:",
            ),
            diff.replacementSegments.map { it.kind to it.text },
        )
        val deletedStart = original.indexOf("Kicked")
        assertEquals(
            listOf(deletedStart to deletedStart + "Kick".length),
            diff.deletedRanges.map { it.startOffsetInOriginal to it.endOffsetInOriginal },
        )
    }

    @Test
    fun `leaves a partially typed suggestion prefix unhighlighted`() {
        val original = "return fun\n"
        val replacement = "return function\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))

        val diff = SuggestionTextDiff.between(original, replacement, block)

        assertEquals(
            listOf(
                SuggestionTextSegmentKind.EQUAL to "return fun",
                SuggestionTextSegmentKind.INSERTED to "ction",
            ),
            diff.replacementSegments.map { it.kind to it.text },
        )
        assertEquals(emptyList<Pair<Int, Int>>(), diff.deletedRanges.map { it.startOffsetInOriginal to it.endOffsetInOriginal })
    }

    @Test
    fun `leaves shared prefix and suffix around a partially typed suggestion unhighlighted`() {
        val original = "return fun;\n"
        val replacement = "return function();\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))

        val diff = SuggestionTextDiff.between(original, replacement, block)

        assertEquals(
            listOf(
                SuggestionTextSegmentKind.EQUAL to "return fun",
                SuggestionTextSegmentKind.INSERTED to "ction()",
                SuggestionTextSegmentKind.EQUAL to ";",
            ),
            diff.replacementSegments.map { it.kind to it.text },
        )
        assertEquals(emptyList<Pair<Int, Int>>(), diff.deletedRanges.map { it.startOffsetInOriginal to it.endOffsetInOriginal })
    }

    @Test
    fun `groups contiguous inserted symbols as one changed segment`() {
        val original = "return function\n"
        val replacement = "return function();\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))

        val diff = SuggestionTextDiff.between(original, replacement, block)

        assertEquals(
            listOf(
                SuggestionTextSegmentKind.EQUAL to "return function",
                SuggestionTextSegmentKind.INSERTED to "();",
            ),
            diff.replacementSegments.map { it.kind to it.text },
        )
        assertEquals(emptyList<Pair<Int, Int>>(), diff.deletedRanges.map { it.startOffsetInOriginal to it.endOffsetInOriginal })
    }

    @Test
    fun `highlights inserted whitespace before an otherwise matched word`() {
        val original = "return value;\n"
        val replacement = "return  value;\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))

        val diff = SuggestionTextDiff.between(original, replacement, block)

        assertEquals(
            listOf(
                SuggestionTextSegmentKind.EQUAL to "return ",
                SuggestionTextSegmentKind.INSERTED to " ",
                SuggestionTextSegmentKind.EQUAL to "value;",
            ),
            diff.replacementSegments.map { it.kind to it.text },
        )
        assertEquals(emptyList<Pair<Int, Int>>(), diff.deletedRanges.map { it.startOffsetInOriginal to it.endOffsetInOriginal })
    }

    @Test
    fun `highlights only an inserted space before an otherwise matched word`() {
        val original = "function(foo,bar);\n"
        val replacement = "function(foo, bar);\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))

        val diff = SuggestionTextDiff.between(original, replacement, block)

        assertEquals(
            listOf(
                SuggestionTextSegmentKind.EQUAL to "function(foo,",
                SuggestionTextSegmentKind.INSERTED to " ",
                SuggestionTextSegmentKind.EQUAL to "bar);",
            ),
            diff.replacementSegments.map { it.kind to it.text },
        )
        assertEquals(emptyList<Pair<Int, Int>>(), diff.deletedRanges.map { it.startOffsetInOriginal to it.endOffsetInOriginal })
    }

    @Test
    fun `highlights leading whitespace as part of a pure inserted line`() {
        val original = "next\n"
        val replacement = "\t\tcase AuthStatus::ErrorUserBanned:\nnext\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))

        val diff = SuggestionTextDiff.between(original, replacement, block)

        assertEquals(
            listOf(SuggestionTextSegmentKind.INSERTED to "\t\tcase AuthStatus::ErrorUserBanned:"),
            diff.replacementSegments.map { it.kind to it.text },
        )
        assertEquals(emptyList<Pair<Int, Int>>(), diff.deletedRanges.map { it.startOffsetInOriginal to it.endOffsetInOriginal })
    }
}
