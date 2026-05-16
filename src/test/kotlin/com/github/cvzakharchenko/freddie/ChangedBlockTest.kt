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

    @Test
    fun `separates independent replacements into multiple blocks`() {
        val original =
            "one\n" +
                "old two\n" +
                "three\n" +
                "old five\n" +
                "six\n"
        val replacement =
            "one\n" +
                "new two\n" +
                "three\n" +
                "new five\n" +
                "six\n"

        val blocks = ChangedBlock.allBetween(original, replacement)

        assertEquals(2, blocks.size)
        assertEquals(original.indexOf("old two"), blocks[0].anchorOffsetInOriginal)
        assertEquals(1, blocks[0].originalStartLine)
        assertEquals(2, blocks[0].originalEndLineExclusive)
        assertEquals("new two", blocks[0].replacementBlock)
        assertEquals(original.indexOf("old five"), blocks[1].anchorOffsetInOriginal)
        assertEquals(3, blocks[1].originalStartLine)
        assertEquals(4, blocks[1].originalEndLineExclusive)
        assertEquals("new five", blocks[1].replacementBlock)
    }

    @Test
    fun `separates independent insertions into multiple blocks`() {
        val original = "one\nthree\nfive\n"
        val replacement = "one\ntwo\nthree\nfour\nfive\n"

        val blocks = ChangedBlock.allBetween(original, replacement)

        assertEquals(2, blocks.size)
        assertEquals(original.indexOf("three"), blocks[0].anchorOffsetInOriginal)
        assertEquals(1, blocks[0].originalStartLine)
        assertEquals(1, blocks[0].originalEndLineExclusive)
        assertEquals("two", blocks[0].replacementBlock)
        assertEquals(original.indexOf("five"), blocks[1].anchorOffsetInOriginal)
        assertEquals(2, blocks[1].originalStartLine)
        assertEquals(2, blocks[1].originalEndLineExclusive)
        assertEquals("four", blocks[1].replacementBlock)
    }

    @Test
    fun `drops only blocks touching the last editable line`() {
        val original =
            "one\n" +
                "old two\n" +
                "three\n" +
                "old last\n"
        val replacement =
            "one\n" +
                "new two\n" +
                "three\n" +
                "new last\n"

        val filtered = ChangedBlock.dropLastLineTouchingBlocks(original, replacement)

        assertEquals(1, filtered.keptBlockCount)
        assertEquals(1, filtered.droppedBlockCount)
        assertEquals(
            "one\n" +
                "new two\n" +
                "three\n" +
                "old last\n",
            filtered.text,
        )
    }

    @Test
    fun `drops insertions anchored on the last editable line`() {
        val original = "one\nlast\n"
        val replacement = "one\ninserted\nlast\n"

        val filtered = ChangedBlock.dropLastLineTouchingBlocks(original, replacement)

        assertEquals(0, filtered.keptBlockCount)
        assertEquals(1, filtered.droppedBlockCount)
        assertEquals(original, filtered.text)
    }
}
