package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.presentation.ChangedBlock
import com.github.cvzakharchenko.freddie.presentation.LineGhostTextPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class LineGhostTextPlanTest {
    @Test
    fun `renders a line replacement below the original changed line`() {
        val original =
            "\t\tcase AuthStatus::ErrorPredefinedReplicablesHash:\n" +
                "\t\t\treturn \"\";\n" +
                "\t\tcase AuthStatus::ErrorPrefabHash:\n"
        val replacement =
            "\t\tcase AuthStatus::ErrorPredefinedReplicablesHash:\n" +
                "\t\t\treturn \"predefined replicables hashes mismatch\";\n" +
                "\t\tcase AuthStatus::ErrorPrefabHash:\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))
        val plan = requireNotNull(LineGhostTextPlan.create(original, 0, block, block.replacementBlock))

        assertEquals(original.indexOf("\n\t\tcase AuthStatus::ErrorPrefabHash:"), plan.renderOffset)
        assertEquals("\n\t\t\treturn \"predefined replicables hashes mismatch\";", plan.text)
    }

    @Test
    fun `renders inserted lines below the previous unchanged line`() {
        val original = "keep\nnext\n"
        val replacement = "keep\ninserted\nnext\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))
        val plan = requireNotNull(LineGhostTextPlan.create(original, 0, block, block.replacementBlock))

        assertEquals("keep".length, plan.renderOffset)
        assertEquals("\ninserted", plan.text)
    }

    @Test
    fun `renders separated insertion blocks at their own anchors`() {
        val original = "one\nthree\nfive\n"
        val replacement = "one\ntwo\nthree\nfour\nfive\n"
        val blocks = ChangedBlock.allBetween(original, replacement)
        val plans =
            blocks.map {
                requireNotNull(LineGhostTextPlan.create(original, 0, it, it.replacementBlock))
            }

        assertEquals(2, plans.size)
        assertEquals("one".length, plans[0].renderOffset)
        assertEquals("\ntwo", plans[0].text)
        assertEquals("one\nthree".length, plans[1].renderOffset)
        assertEquals("\nfour", plans[1].text)
    }

    @Test
    fun `renders inserted auth status case below the previous unchanged return line`() {
        val original =
            "\t\tcase AuthStatus::ErrorAbsentPlayer:\n" +
                "\t\t\treturn \"player is absent on server\";\n" +
                "\t\tcase AuthStatus::ErrorSuspiciousMessage:\n" +
                "\t\t\treturn \"server received suspicious message\";\n" +
                "\t\tcase AuthStatus::ErrorKicked:\n" +
                "\t\t\treturn \"kicked by server\";\n" +
                "\t\tdefault:\n" +
                "\t\t\treturn \"unknown\";\n" +
                "\t}\n" +
                "}\n\n" +
                "//----------------------------------------------------------------------------------------------------------------------\n" +
                "ServerAuthSystem::ServerAuthSystem(engine::ecs::World& w)\n" +
                "\t: System(w)\n" +
                "{}\n\n"
        val replacement =
            "\t\tcase AuthStatus::ErrorAbsentPlayer:\n" +
                "\t\t\treturn \"player is absent on server\";\n" +
                "\t\tcase AuthStatus::ErrorSuspiciousMessage:\n" +
                "\t\t\treturn \"server received suspicious message\";\n" +
                "\t\tcase AuthStatus::ErrorReassigned:\n" +
                "\t\t\treturn \"connection reassigned\";\n" +
                "\t\tcase AuthStatus::ErrorKicked:\n" +
                "\t\t\treturn \"kicked by server\";\n" +
                "\t\tdefault:\n" +
                "\t\t\treturn \"unknown\";\n" +
                "\t}\n" +
                "}\n\n" +
                "//----------------------------------------------------------------------------------------------------------------------\n" +
                "ServerAuthSystem::ServerAuthSystem(engine::ecs::World& w)\n" +
                "\t: System(w)\n" +
                "{}\n\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))
        val plan = requireNotNull(LineGhostTextPlan.create(original, 0, block, block.replacementBlock))

        val previousLineEnd = original.indexOf("\n\t\tcase AuthStatus::ErrorKicked:")
        assertEquals(previousLineEnd, plan.renderOffset)
        assertEquals(
            "\n\t\tcase AuthStatus::ErrorReassigned:\n" +
                "\t\t\treturn \"connection reassigned\";",
            plan.text,
        )
    }

    @Test
    fun `ignores a missing trailing blank line when rendering inserted auth status case`() {
        val original =
            "\t\tcase AuthStatus::ErrorAbsentPlayer:\n" +
                "\t\t\treturn \"player is absent on server\";\n" +
                "\t\tcase AuthStatus::ErrorSuspiciousMessage:\n" +
                "\t\t\treturn \"server received suspicious message\";\n" +
                "\t\tcase AuthStatus::ErrorKicked:\n" +
                "\t\t\treturn \"kicked by server\";\n" +
                "\t\tdefault:\n" +
                "\t\t\treturn \"unknown\";\n" +
                "\t}\n" +
                "}\n\n" +
                "//----------------------------------------------------------------------------------------------------------------------\n" +
                "ServerAuthSystem::ServerAuthSystem(engine::ecs::World& w)\n" +
                "\t: System(w)\n" +
                "{}\n\n"
        val replacement =
            "\t\tcase AuthStatus::ErrorAbsentPlayer:\n" +
                "\t\t\treturn \"player is absent on server\";\n" +
                "\t\tcase AuthStatus::ErrorSuspiciousMessage:\n" +
                "\t\t\treturn \"server received suspicious message\";\n" +
                "\t\tcase AuthStatus::ErrorUserBanned:\n" +
                "\t\t\treturn \"user banned\";\n" +
                "\t\tcase AuthStatus::ErrorKicked:\n" +
                "\t\t\treturn \"kicked by server\";\n" +
                "\t\tdefault:\n" +
                "\t\t\treturn \"unknown\";\n" +
                "\t}\n" +
                "}\n\n" +
                "//----------------------------------------------------------------------------------------------------------------------\n" +
                "ServerAuthSystem::ServerAuthSystem(engine::ecs::World& w)\n" +
                "\t: System(w)\n" +
                "{}\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))
        val plan = requireNotNull(LineGhostTextPlan.create(original, 0, block, block.replacementBlock))

        val previousLineEnd = original.indexOf("\n\t\tcase AuthStatus::ErrorKicked:")
        assertEquals(previousLineEnd, plan.renderOffset)
        assertEquals(
            "\n\t\tcase AuthStatus::ErrorUserBanned:\n" +
                "\t\t\treturn \"user banned\";",
            plan.text,
        )
    }

    @Test
    fun `renders file-start insertions before the first line`() {
        val original = "next\n"
        val replacement = "inserted\nnext\n"
        val block = requireNotNull(ChangedBlock.between(original, replacement))
        val plan = requireNotNull(LineGhostTextPlan.create(original, 0, block, block.replacementBlock))

        assertEquals(0, plan.renderOffset)
        assertEquals("inserted\n", plan.text)
    }
}
