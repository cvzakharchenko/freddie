package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.context.CodeSnippet
import com.github.cvzakharchenko.freddie.context.MercuryPromptBuilder
import com.github.cvzakharchenko.freddie.context.MercuryPromptContext
import org.junit.Assert.assertTrue
import org.junit.Test

class MercuryPromptBuilderTest {
    @Test
    fun `emits Mercury prompt sections with cursor and project relative paths`() {
        val prompt =
            MercuryPromptBuilder.build(
                MercuryPromptContext(
                    viewedSnippets =
                        listOf(
                            CodeSnippet(
                                filePath = "src/main/kotlin/Foo.kt",
                                startLine = 10,
                                endLine = 12,
                                text = "fun helper() = 42",
                                timestamp = 1L,
                            ),
                        ),
                    copiedSnippets =
                        listOf(
                            CodeSnippet(
                                filePath = "src/main/kotlin/Copied.kt",
                                startLine = 3,
                                endLine = 4,
                                text = "fun copied() = true",
                                timestamp = 2L,
                            ),
                        ),
                    currentFilePath = "src/main/kotlin/App.kt",
                    codeAboveEditableRegion = "package app\n\n",
                    editableBeforeCursor = "fun main() {\n    pri",
                    editableAfterCursor = "\n}\n",
                    codeBelowEditableRegion = "\nclass Tail",
                    editHistoryDiffsOldestToNewest = listOf("--- a.kt\n+++ a.kt\n@@ -1,1 +1,1 @@\n-old\n+new"),
                ),
            )

        assertTrue(prompt.contains("<|recently_viewed_code_snippets|>"))
        assertTrue(prompt.contains("<|recently_viewed_code_snippet|>"))
        assertTrue(prompt.contains("code_snippet_file_path: src/main/kotlin/Foo.kt"))
        assertTrue(prompt.contains("code_snippet_file_path: src/main/kotlin/Copied.kt"))
        assertTrue(prompt.indexOf("code_snippet_file_path: src/main/kotlin/Foo.kt") < prompt.indexOf("code_snippet_file_path: src/main/kotlin/Copied.kt"))
        assertTrue(prompt.contains("<|current_file_content|>"))
        assertTrue(prompt.contains("current_file_path: src/main/kotlin/App.kt"))
        assertTrue(prompt.contains("pri<|cursor|>"))
        assertTrue(prompt.contains("<|edit_diff_history|>"))
        assertTrue(prompt.contains("--- a.kt"))
    }

    @Test
    fun `emits empty snippet and history sections`() {
        val prompt =
            MercuryPromptBuilder.build(
                MercuryPromptContext(
                    viewedSnippets = emptyList(),
                    copiedSnippets = emptyList(),
                    currentFilePath = "Only.kt",
                    codeAboveEditableRegion = "",
                    editableBeforeCursor = "",
                    editableAfterCursor = "fun main() {}\n",
                    codeBelowEditableRegion = "",
                    editHistoryDiffsOldestToNewest = emptyList(),
                ),
            )

        assertTrue(prompt.contains("<|recently_viewed_code_snippets|>\n<|/recently_viewed_code_snippets|>"))
        assertTrue(prompt.contains("<|edit_diff_history|>\n<|/edit_diff_history|>"))
        assertTrue(prompt.contains("<|code_to_edit|>\n<|cursor|>fun main() {}"))
    }
}
