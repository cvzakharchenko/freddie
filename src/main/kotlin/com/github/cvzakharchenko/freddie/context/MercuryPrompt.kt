package com.github.cvzakharchenko.freddie.context

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

data class MercuryPromptContext(
    val snippets: List<CodeSnippet>,
    val currentFilePath: String,
    val codeAboveEditableRegion: String,
    val editableBeforeCursor: String,
    val editableAfterCursor: String,
    val codeBelowEditableRegion: String,
    val editHistoryDiffsOldestToNewest: List<String>,
)

data class MercuryRequestSnapshot(
    val editor: Editor,
    val document: Document,
    val filePath: String,
    val modificationStamp: Long,
    val caretOffset: Int,
    val editableRegion: EditableRegion,
    val prompt: String,
    val debugInfo: MercuryContextDebugInfo,
)

data class MercuryContextDebugInfo(
    val filePath: String,
    val documentTextLength: Int,
    val documentLineCount: Int,
    val caretOffset: Int,
    val modificationStamp: Long,
    val editableStartLine: Int,
    val editableEndLine: Int,
    val editableStartOffset: Int,
    val editableEndOffset: Int,
    val editableCharCount: Int,
    val beforeCursorCharCount: Int,
    val afterCursorCharCount: Int,
    val codeAboveCharCount: Int,
    val codeBelowCharCount: Int,
    val snippets: List<MercurySnippetDebugInfo>,
    val editDiffCount: Int,
    val promptCharCount: Int,
    val codeToEditBlock: String,
)

data class MercurySnippetDebugInfo(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val charCount: Int,
)

class MercuryContextCollector(
    private val project: Project,
    private val recentEditHistory: RecentEditHistory,
    private val recentlyViewedSnippetTracker: RecentlyViewedSnippetTracker,
) {
    fun capture(editor: Editor): MercuryRequestSnapshot? {
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        val document = editor.document
        val filePath = projectRelativePath(project, file)
        val caretOffset = editor.caretModel.offset.coerceIn(0, document.textLength)
        val editableRegion = EditableRegionSelector.select(document, caretOffset)
        val promptWindow = CurrentFilePromptWindow.from(document.text, editableRegion)
        val snippets =
            recentlyViewedSnippetTracker.snippetsFor(
                currentEditor = editor,
                currentFilePath = filePath,
                editableStartLine = editableRegion.startLine,
                editableEndLine = editableRegion.endLine,
            )
        val editDiffs = recentEditHistory.formattedDiffs()
        val context =
            MercuryPromptContext(
                snippets = snippets,
                currentFilePath = filePath,
                codeAboveEditableRegion = promptWindow.codeAbove,
                editableBeforeCursor = editableRegion.beforeCursor,
                editableAfterCursor = editableRegion.afterCursor,
                codeBelowEditableRegion = promptWindow.codeBelow,
                editHistoryDiffsOldestToNewest = editDiffs,
            )
        val prompt = MercuryPromptBuilder.build(context)
        return MercuryRequestSnapshot(
            editor = editor,
            document = document,
            filePath = filePath,
            modificationStamp = document.modificationStamp,
            caretOffset = caretOffset,
            editableRegion = editableRegion,
            prompt = prompt,
            debugInfo =
                MercuryContextDebugInfo(
                    filePath = filePath,
                    documentTextLength = document.textLength,
                    documentLineCount = document.lineCount,
                    caretOffset = caretOffset,
                    modificationStamp = document.modificationStamp,
                    editableStartLine = editableRegion.startLine,
                    editableEndLine = editableRegion.endLine,
                    editableStartOffset = editableRegion.startOffset,
                    editableEndOffset = editableRegion.endOffset,
                    editableCharCount = editableRegion.originalText.length,
                    beforeCursorCharCount = editableRegion.beforeCursor.length,
                    afterCursorCharCount = editableRegion.afterCursor.length,
                    codeAboveCharCount = promptWindow.codeAbove.length,
                    codeBelowCharCount = promptWindow.codeBelow.length,
                    snippets =
                        snippets.map {
                            MercurySnippetDebugInfo(
                                filePath = it.filePath,
                                startLine = it.startLine,
                                endLine = it.endLine,
                                charCount = it.text.length,
                            )
                        },
                    editDiffCount = editDiffs.size,
                    promptCharCount = prompt.length,
                    codeToEditBlock = editableRegion.beforeCursor + "<|cursor|>" + editableRegion.afterCursor,
                ),
        )
    }
}

private data class CurrentFilePromptWindow(
    val codeAbove: String,
    val codeBelow: String,
) {
    companion object {
        private const val SMALL_FILE_CHAR_LIMIT = 200_000
        private const val SURROUNDING_LINE_LIMIT = 150

        fun from(
            documentText: String,
            editableRegion: EditableRegion,
        ): CurrentFilePromptWindow {
            if (documentText.length <= SMALL_FILE_CHAR_LIMIT) {
                return CurrentFilePromptWindow(
                    codeAbove = documentText.substring(0, editableRegion.startOffset),
                    codeBelow = documentText.substring(editableRegion.endOffset),
                )
            }

            val before = documentText.substring(0, editableRegion.startOffset)
            val after = documentText.substring(editableRegion.endOffset)
            return CurrentFilePromptWindow(
                codeAbove = before.split('\n').takeLast(SURROUNDING_LINE_LIMIT).joinToString("\n").withOriginalTrailingNewline(before),
                codeBelow = after.split('\n').take(SURROUNDING_LINE_LIMIT).joinToString("\n").withOriginalTrailingNewline(after),
            )
        }

        private fun String.withOriginalTrailingNewline(original: String): String =
            if (isNotEmpty() && original.endsWith("\n") && !endsWith("\n")) "$this\n" else this
    }
}

object MercuryPromptBuilder {
    fun build(context: MercuryPromptContext): String =
        buildString {
            appendLine("<|recently_viewed_code_snippets|>")
            for (snippet in context.snippets) {
                appendLine("<|recently_viewed_code_snippet|>")
                append("code_snippet_file_path: ")
                appendLine(snippet.filePath)
                append(snippet.text)
                if (!snippet.text.endsWith("\n")) appendLine()
                appendLine("<|/recently_viewed_code_snippet|>")
                appendLine()
            }
            appendLine("<|/recently_viewed_code_snippets|>")
            appendLine()

            appendLine("<|current_file_content|>")
            append("current_file_path: ")
            appendLine(context.currentFilePath)
            append(context.codeAboveEditableRegion)
            appendLine("<|code_to_edit|>")
            append(context.editableBeforeCursor)
            append("<|cursor|>")
            append(context.editableAfterCursor)
            if (!context.editableAfterCursor.endsWith("\n")) appendLine()
            appendLine("<|/code_to_edit|>")
            append(context.codeBelowEditableRegion)
            if (context.codeBelowEditableRegion.isNotEmpty() && !context.codeBelowEditableRegion.endsWith("\n")) appendLine()
            appendLine("<|/current_file_content|>")
            appendLine()

            appendLine("<|edit_diff_history|>")
            for (diff in context.editHistoryDiffsOldestToNewest) {
                append(diff)
                if (!diff.endsWith("\n")) appendLine()
                appendLine()
            }
            appendLine("<|/edit_diff_history|>")
        }
}
