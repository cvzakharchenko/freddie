package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.presentation.ChangedBlock
import com.github.cvzakharchenko.freddie.presentation.LineHintPlan
import com.github.cvzakharchenko.freddie.presentation.SuggestionTextDiff
import com.github.cvzakharchenko.freddie.presentation.SuggestionTextSegmentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LineHintPlanTest {
    @Test
    fun `renders a line replacement below the original changed line without a synthetic leading newline`() {
        val original =
            "\t\tcase AuthStatus::ErrorPredefinedReplicablesHash:\n" +
                "\t\t\treturn \"\";\n" +
                "\t\tcase AuthStatus::ErrorPrefabHash:\n"
        val replacement =
            "\t\tcase AuthStatus::ErrorPredefinedReplicablesHash:\n" +
                "\t\t\treturn \"predefined replicables hashes mismatch\";\n" +
                "\t\tcase AuthStatus::ErrorPrefabHash:\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))
        val plan = requireNotNull(LineHintPlan.create(original, 0, block, segments(original, replacement, block)))

        assertEquals(original.indexOf("\n\t\tcase AuthStatus::ErrorPrefabHash:"), plan.renderOffset)
        assertEquals(false, plan.showAbove)
        assertEquals(
            listOf(
                listOf(
                    SuggestionTextSegmentKind.EQUAL to "\t\t\treturn \"",
                    SuggestionTextSegmentKind.INSERTED to "predefined replicables hashes mismatch",
                    SuggestionTextSegmentKind.EQUAL to "\";",
                ),
            ),
            plan.lines.map { line -> line.map { it.kind to it.text } },
        )
    }

    @Test
    fun `renders inserted lines below the previous unchanged line`() {
        val original = "keep\nnext\n"
        val replacement = "keep\ninserted\nnext\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))
        val plan = requireNotNull(LineHintPlan.create(original, 0, block, segments(original, replacement, block)))

        assertEquals("keep".length, plan.renderOffset)
        assertEquals(false, plan.showAbove)
        assertEquals(
            listOf(listOf(SuggestionTextSegmentKind.INSERTED to "inserted")),
            plan.lines.map { line -> line.map { it.kind to it.text } },
        )
    }

    @Test
    fun `renders file-start insertions above the first line`() {
        val original = "next\n"
        val replacement = "inserted\nnext\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))
        val plan = requireNotNull(LineHintPlan.create(original, 0, block, segments(original, replacement, block)))

        assertEquals(0, plan.renderOffset)
        assertEquals(true, plan.showAbove)
        assertEquals(
            listOf(listOf(SuggestionTextSegmentKind.INSERTED to "inserted")),
            plan.lines.map { line -> line.map { it.kind to it.text } },
        )
    }

    private fun segments(
        original: String,
        replacement: String,
        block: ChangedBlock,
    ) = SuggestionTextDiff.between(original, replacement, block).replacementSegments
}
