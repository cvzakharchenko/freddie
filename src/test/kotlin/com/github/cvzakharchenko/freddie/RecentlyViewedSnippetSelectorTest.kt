package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.context.CodeSnippet
import com.github.cvzakharchenko.freddie.context.RecentlyViewedSnippetSelector
import com.github.cvzakharchenko.freddie.context.RecentlyViewedSnippetWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentlyViewedSnippetSelectorTest {
    @Test
    fun `drops snippets from the current file`() {
        val selection =
            RecentlyViewedSnippetSelector.select(
                candidatesNewestToOldest =
                    listOf(
                        snippet("src/App.kt", startLine = 30, timestamp = 3),
                        snippet("src/Helper.kt", startLine = 10, timestamp = 2),
                        snippet("src/App.kt", startLine = 1, timestamp = 1),
                    ),
                currentFilePath = "src/App.kt",
            )

        assertEquals(listOf("src/Helper.kt"), selection.snippets.map { it.filePath })
        assertEquals(1, selection.budget.keptItems)
        assertEquals(2, selection.budget.droppedItems)
    }

    @Test
    fun `keeps at most two newest snippets per file`() {
        val selection =
            RecentlyViewedSnippetSelector.select(
                candidatesNewestToOldest =
                    listOf(
                        snippet("src/Helper.kt", startLine = 40, timestamp = 4),
                        snippet("src/Helper.kt", startLine = 30, timestamp = 3),
                        snippet("src/Other.kt", startLine = 20, timestamp = 2),
                        snippet("src/Helper.kt", startLine = 10, timestamp = 1),
                    ),
                currentFilePath = "src/App.kt",
            )

        assertEquals(
            listOf(
                "src/Other.kt:20",
                "src/Helper.kt:30",
                "src/Helper.kt:40",
            ),
            selection.snippets.map { "${it.filePath}:${it.startLine}" },
        )
        assertEquals(3, selection.budget.keptItems)
        assertEquals(1, selection.budget.droppedItems)
    }

    @Test
    fun `snippet window clamps stale center lines after a file shrinks`() {
        val range = RecentlyViewedSnippetWindow.around(lineCount = 132, centerLine = 162, radiusLines = 20)

        assertEquals(111..131, range)
    }

    @Test
    fun `snippet window clamps negative center lines`() {
        val range = RecentlyViewedSnippetWindow.around(lineCount = 10, centerLine = -50, radiusLines = 20)

        assertEquals(0..9, range)
    }

    private fun snippet(
        filePath: String,
        startLine: Int,
        timestamp: Long,
    ): CodeSnippet =
        CodeSnippet(
            filePath = filePath,
            startLine = startLine,
            endLine = startLine + 2,
            text = "line $startLine\nline ${startLine + 1}\nline ${startLine + 2}",
            timestamp = timestamp,
        )
}
