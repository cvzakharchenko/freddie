package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.context.EditableRegionMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableRegionMatcherTest {
    @Test
    fun `uses exact saved offsets when valid`() {
        val resolution = EditableRegionMatcher.resolve("abc def ghi", 4, 7, "def")

        assertEquals(4, resolution.match?.startOffset)
        assertEquals(7, resolution.match?.endOffset)
        assertNull(resolution.match?.note)
        assertNull(resolution.failureReason)
    }

    @Test
    fun `relocates original region when saved offsets are invalid`() {
        val text = "prefix\noriginal editable region\nsuffix"
        val resolution = EditableRegionMatcher.resolve(text, 100, 140, "original editable region")

        assertEquals(7, resolution.match?.startOffset)
        assertEquals(31, resolution.match?.endOffset)
        assertNotNull(resolution.match?.note)
        assertNull(resolution.failureReason)
    }

    @Test
    fun `reports useful details when original text cannot be found`() {
        val resolution = EditableRegionMatcher.resolve("short text", 20, 30, "missing")

        assertNull(resolution.match)
        assertTrue(resolution.failureReason.orEmpty().contains("start=20"))
        assertTrue(resolution.failureReason.orEmpty().contains("document length 10"))
    }
}
