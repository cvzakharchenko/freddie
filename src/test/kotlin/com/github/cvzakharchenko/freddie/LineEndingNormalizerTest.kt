package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.context.LineEndingNormalizer
import com.github.cvzakharchenko.freddie.presentation.ChangedSlice
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

        val slice = ChangedSlice.betweenIgnoringLineEndings(original, mercuryReplacement)

        assertEquals("", slice.originalChange)
        assertEquals("ned", slice.replacementChange)
        assertEquals("\tErrorUserBan".length, slice.originalStartOffset)
        assertEquals("\tErrorUserBan".length, slice.originalEndOffset)
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
    fun `prepared replacement uses dominant document line ending for application`() {
        val prepared =
            LineEndingNormalizer.prepareReplacementForEditableRegion(
                mercuryReplacement = "one\r\ntwo\n",
                originalEditableRegion = "one\ntwo\n",
                documentText = "file\r\nwith\r\nmostly\r\ncrlf\n",
            )

        assertEquals("one\r\ntwo\r\n", prepared.applicationText)
    }
}
