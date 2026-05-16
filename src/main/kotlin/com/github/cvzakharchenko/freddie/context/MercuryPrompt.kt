package com.github.cvzakharchenko.freddie.context

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

data class MercuryPromptContext(
    val viewedSnippets: List<CodeSnippet>,
    val copiedSnippets: List<CodeSnippet>,
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
    val viewedSnippets: List<MercurySnippetDebugInfo>,
    val copiedSnippets: List<MercurySnippetDebugInfo>,
    val editDiffCount: Int,
    val editDiffsOldestToNewest: List<String>,
    val currentFileBudget: ContextBudgetDebugInfo,
    val recentEditsBudget: ContextBudgetDebugInfo,
    val viewedSnippetsBudget: ContextBudgetDebugInfo,
    val copiedSnippetsBudget: ContextBudgetDebugInfo,
    val promptCharCount: Int,
    val codeToEditBlock: String,
)

data class MercurySnippetDebugInfo(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val charCount: Int,
    val text: String,
)

class MercuryContextCollector(
    private val project: Project,
    private val recentEditHistory: RecentEditHistory,
    private val recentlyViewedSnippetTracker: RecentlyViewedSnippetTracker,
    private val copiedSnippetTracker: CopiedSnippetTracker,
) {
    fun capture(editor: Editor): MercuryRequestSnapshot? {
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        val document = editor.document
        val filePath = projectRelativePath(project, file)
        val caretOffset = editor.caretModel.offset.coerceIn(0, document.textLength)
        val editableRegion = EditableRegionSelector.select(document, caretOffset)
        val promptWindow = CurrentFilePromptWindow.from(document.text, editableRegion)
        val snippetSelection =
            recentlyViewedSnippetTracker.snippetsFor(
                currentEditor = editor,
                currentFilePath = filePath,
                editableStartLine = editableRegion.startLine,
                editableEndLine = editableRegion.endLine,
            )
        val viewedSnippets = snippetSelection.snippets
        val copiedSnippetSelection = copiedSnippetTracker.snippetsWithinBudget()
        val copiedSnippets = copiedSnippetSelection.snippets
        val editSelection = recentEditHistory.formattedDiffsWithinBudget()
        val editDiffs = editSelection.diffsOldestToNewest
        val context =
            MercuryPromptContext(
                viewedSnippets = viewedSnippets,
                copiedSnippets = copiedSnippets,
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
                    viewedSnippets =
                        viewedSnippets.map {
                            MercurySnippetDebugInfo(
                                filePath = it.filePath,
                                startLine = it.startLine,
                                endLine = it.endLine,
                                charCount = it.text.length,
                                text = it.text,
                            )
                        },
                    copiedSnippets =
                        copiedSnippets.map {
                            MercurySnippetDebugInfo(
                                filePath = it.filePath,
                                startLine = it.startLine,
                                endLine = it.endLine,
                                charCount = it.text.length,
                                text = it.text,
                            )
                        },
                    editDiffCount = editDiffs.size,
                    editDiffsOldestToNewest = editDiffs,
                    currentFileBudget = promptWindow.budget,
                    recentEditsBudget = editSelection.budget,
                    viewedSnippetsBudget = snippetSelection.budget,
                    copiedSnippetsBudget = copiedSnippetSelection.budget,
                    promptCharCount = prompt.length,
                    codeToEditBlock = editableRegion.beforeCursor + "<|cursor|>" + editableRegion.afterCursor,
                ),
        )
    }
}

private data class CurrentFilePromptWindow(
    val codeAbove: String,
    val codeBelow: String,
    val budget: ContextBudgetDebugInfo,
) {
    companion object {
        private const val ABOVE_CONTEXT_PERCENT = 60

        fun from(
            documentText: String,
            editableRegion: EditableRegion,
            budgetChars: Int = ContextBudget.CURRENT_FILE_CHARS,
            budgetTokens: Int = ContextBudget.CURRENT_FILE_TOKENS,
        ): CurrentFilePromptWindow {
            val before = documentText.substring(0, editableRegion.startOffset)
            val after = documentText.substring(editableRegion.endOffset)
            val surrounding = surroundingText(before, after, budgetChars)
            return CurrentFilePromptWindow(
                codeAbove = surrounding.above,
                codeBelow = surrounding.below,
                budget =
                    ContextBudgetDebugInfo(
                        budgetTokens = budgetTokens,
                        budgetChars = budgetChars,
                        usedChars = surrounding.above.length + surrounding.below.length,
                        droppedChars = before.length + after.length - surrounding.above.length - surrounding.below.length,
                        keptItems = listOf(surrounding.above, surrounding.below).count { it.isNotEmpty() },
                        droppedItems = if (before.length + after.length > surrounding.above.length + surrounding.below.length) 1 else 0,
                    ),
            )
        }

        private fun surroundingText(
            before: String,
            after: String,
            budgetChars: Int,
        ): SurroundingText {
            if (budgetChars <= 0) return SurroundingText(above = "", below = "")
            if (before.length + after.length <= budgetChars) return SurroundingText(above = before, below = after)

            var aboveBudget = minOf(before.length, budgetChars * ABOVE_CONTEXT_PERCENT / 100)
            var belowBudget = minOf(after.length, budgetChars - aboveBudget)
            val remainingBudget = budgetChars - aboveBudget - belowBudget
            if (remainingBudget > 0) {
                val extraAbove = minOf(before.length - aboveBudget, remainingBudget)
                aboveBudget += extraAbove
                belowBudget += minOf(after.length - belowBudget, remainingBudget - extraAbove)
            }

            return SurroundingText(
                above = before.takeLastAtLineBoundary(aboveBudget),
                below = after.takeAtLineBoundary(belowBudget),
            )
        }

        private fun String.takeLastAtLineBoundary(maxChars: Int): String {
            if (length <= maxChars) return this
            if (maxChars <= 0) return ""
            val rawStart = length - maxChars
            val newline = indexOf('\n', rawStart)
            return if (newline >= 0 && newline + 1 < length) substring(newline + 1) else takeLast(maxChars)
        }

        private fun String.takeAtLineBoundary(maxChars: Int): String {
            if (length <= maxChars) return this
            if (maxChars <= 0) return ""
            val rawEnd = maxChars.coerceAtMost(length)
            val newline = lastIndexOf('\n', rawEnd - 1)
            return if (newline >= 0) substring(0, newline + 1) else take(maxChars)
        }
    }
}

private data class SurroundingText(
    val above: String,
    val below: String,
)

object MercuryPromptBuilder {
    fun build(context: MercuryPromptContext): String =
        buildString {
            appendLine("<|recently_viewed_code_snippets|>")
            for (snippet in context.viewedSnippets + context.copiedSnippets) {
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
