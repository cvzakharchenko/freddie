package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.presentation.ChangedBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChangedBlockTest {
    @Test
    fun `extracts inserted response lines before the first differing original line`() {
        val original =
            "\t\t\treturn \"prefab hashes mismatch\";\n" +
                "\t\tcase AuthStatus::ErrorTimeout:\n" +
                "\t\t\treturn \"timeout\";\n" +
                "\t\tcase AuthStatus::ErrorWrongResponse:\n" +
                "\t\t\treturn \"wrong challenge response\";\n"
        val replacement =
            "\t\t\treturn \"prefab hashes mismatch\";\n" +
                "\t\tcase AuthStatus::ErrorReassigned:\n" +
                "\t\t\treturn \"player reassigned\";\n" +
                "\t\tcase AuthStatus::ErrorUserBanned:\n" +
                "\t\t\treturn \"user blocked by server\";\n" +
                "\t\tcase AuthStatus::ErrorTimeout:\n" +
                "\t\t\treturn \"timeout\";\n" +
                "\t\tcase AuthStatus::ErrorWrongResponse:\n" +
                "\t\t\treturn \"wrong challenge response\";\n"

        val block = requireNotNull(ChangedBlock.between(original, replacement))
        val timeoutCaseStart = original.indexOf("\t\tcase AuthStatus::ErrorTimeout:")

        assertEquals(timeoutCaseStart, block.anchorOffsetInOriginal)
        assertEquals(1, block.originalStartLine)
        assertEquals(1, block.originalEndLineExclusive)
        assertEquals(
            "\t\tcase AuthStatus::ErrorReassigned:\n" +
                "\t\t\treturn \"player reassigned\";\n" +
                "\t\tcase AuthStatus::ErrorUserBanned:\n" +
                "\t\t\treturn \"user blocked by server\";",
            block.replacementBlock,
        )
    }

    @Test
    fun `extracts a whole changed response line instead of a token-level insertion`() {
        val original =
            "\t\tcase AuthStatus::ErrorUserBan:\n" +
                "\t\t\treturn \"user ban\";\n" +
                "\t\tcase AuthStatus::ErrorKicked:\n"
        val replacement =
            "\t\tcase AuthStatus::ErrorUserBanned:\n" +
                "\t\t\treturn \"user is banned\";\n" +
                "\t\tcase AuthStatus::ErrorKicked:\n"

        val block = requireNotNull(ChangedBlock.between(original, replacement))

        assertEquals(0, block.anchorOffsetInOriginal)
        assertEquals(0, block.originalStartLine)
        assertEquals(2, block.originalEndLineExclusive)
        assertEquals(
            "\t\tcase AuthStatus::ErrorUserBanned:\n" +
                "\t\t\treturn \"user is banned\";",
            block.replacementBlock,
        )
    }

    @Test
    fun `ignores line ending differences`() {
        val block = ChangedBlock.between("one\r\ntwo\r\n", "one\ntwo\n")

        assertEquals(null, block)
    }
}
