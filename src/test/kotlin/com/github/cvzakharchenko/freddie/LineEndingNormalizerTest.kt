package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.context.LineEndingNormalizer
import com.github.cvzakharchenko.freddie.presentation.ChangedBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class LineEndingNormalizerTest {
    @Test
    fun `diff normalizes original and replacement to LF before comparing`() {
        val original =
            "\tErrorUserBan,\r\n" +
                "\tErrorKicked\r\n" +
                "};\r\n" +
                "struct AuthResponse"
        val mercuryReplacement =
            "\tErrorUserBanned,\n" +
                "\tErrorKicked\n" +
                "};\n" +
                "struct AuthResponse"

        val block = requireNotNull(ChangedBlock.between(original, mercuryReplacement))

        assertEquals(0, block.anchorOffsetInOriginal)
        assertEquals("\tErrorUserBanned,", block.replacementBlock.substringBefore('\n'))
    }

    @Test
    fun `leaves text alone when editable region has no line endings`() {
        assertEquals("abc\n", LineEndingNormalizer.normalizeLike("abc\n", "single line"))
    }

    @Test
    fun `prepared replacement preserves editable region trailing line ending`() {
        val original = "one\r\ntwo\r\n"
        val mercuryReplacement = "one\ntwo"

        val prepared =
            LineEndingNormalizer.prepareReplacementForEditableRegion(
                mercuryReplacement = mercuryReplacement,
                originalEditableRegion = original,
                documentText = "file\r\nwith\r\ncrlf\r\n",
            )

        assertEquals("one\r\ntwo\r\n", prepared.applicationText)
    }

    @Test
    fun `prepared replacement preserves editable region trailing empty line`() {
        val original = "one\r\ntwo\r\n\r\n"
        val mercuryReplacement = "one\ntwo changed\n"

        val prepared =
            LineEndingNormalizer.prepareReplacementForEditableRegion(
                mercuryReplacement = mercuryReplacement,
                originalEditableRegion = original,
                documentText = "file\r\nwith\r\ncrlf\r\n",
            )

        assertEquals("one\r\ntwo changed\r\n\r\n", prepared.applicationText)
    }

    @Test
    fun `prepared replacement uses dominant document line ending for application`() {
        val prepared =
            LineEndingNormalizer.prepareReplacementForEditableRegion(
                mercuryReplacement = "one\r\ntwo\n",
                originalEditableRegion = "one\ntwo\n",
                documentText = "file\r\nwith\r\nmostly\r\ncrlf\n",
            )

        assertEquals("one\r\ntwo\r\n", prepared.applicationText)
    }

    @Test
    fun `prepared replacement removes boundary leading blank line when it realigns with original`() {
        val prepared =
            LineEndingNormalizer.prepareReplacementForEditableRegion(
                mercuryReplacement = "\none\ninserted\ntwo\n",
                originalEditableRegion = "one\ntwo\n",
                documentText = "one\ntwo\n",
            )

        assertEquals("one\ninserted\ntwo\n", prepared.applicationText)
    }
}
