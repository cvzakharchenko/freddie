package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.context.RecentEditHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentEditHistoryTest {
    @Test
    fun `stores small edit diffs oldest to newest and skips no-op edits`() {
        val history = RecentEditHistory()

        history.recordEdit("a.kt", "fun a() = 1\n", "fun a() = 2\n")
        history.recordEdit("b.kt", "fun b() = 1\n", "fun b() = 1\n")
        history.recordEdit("c.kt", "fun c() = 1\n", "fun c() = 3\n")

        val diffs = history.formattedDiffs()
        assertEquals(2, diffs.size)
        assertTrue(diffs[0].contains("--- a.kt"))
        assertTrue(diffs[0].contains("+fun a() = 2"))
        assertTrue(diffs[1].contains("--- c.kt"))
        assertTrue(diffs[1].contains("+fun c() = 3"))
    }
}
