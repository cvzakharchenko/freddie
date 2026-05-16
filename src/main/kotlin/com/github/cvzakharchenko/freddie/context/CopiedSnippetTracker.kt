package com.github.cvzakharchenko.freddie.context

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import kotlin.math.max
import kotlin.math.min

@Service(Service.Level.PROJECT)
class CopiedSnippetTracker(
    private val project: Project,
) {
    private val copiedSnippets = ArrayDeque<CodeSnippet>()

    @Synchronized
    fun recordCopy(
        file: PsiFile,
        startOffsets: IntArray,
        endOffsets: IntArray,
        text: String,
    ) {
        if (text.isBlank()) return
        val virtualFile = file.virtualFile ?: return
        val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return
        if (document.textLength == 0) return
        val lineRange = sourceLineRange(document, startOffsets, endOffsets) ?: return
        val snippet =
            CodeSnippet(
                filePath = projectRelativePath(project, virtualFile),
                startLine = lineRange.first + 1,
                endLine = lineRange.last + 1,
                text = text,
                timestamp = System.currentTimeMillis(),
            )

        copiedSnippets.removeAll {
            it.filePath == snippet.filePath &&
                it.startLine == snippet.startLine &&
                it.endLine == snippet.endLine &&
                it.text == snippet.text
        }
        copiedSnippets.addLast(snippet)
        while (copiedSnippets.size > MAX_COPIED_SNIPPETS) {
            copiedSnippets.removeFirst()
        }
    }

    @Synchronized
    fun snippetsWithinBudget(
        budgetChars: Int = ContextBudget.RECENT_COPIED_SNIPPETS_CHARS,
        budgetTokens: Int = ContextBudget.RECENT_COPIED_SNIPPETS_TOKENS,
    ): SnippetSelection {
        var usedChars = 0
        var droppedChars = 0
        var droppedItems = 0
        val keptNewestToOldest = mutableListOf<CodeSnippet>()

        copiedSnippets
            .asReversed()
            .forEach { snippet ->
                val cost = snippet.promptCharCost()
                if (cost > budgetChars - usedChars) {
                    droppedItems++
                    droppedChars += cost
                } else {
                    keptNewestToOldest.add(snippet)
                    usedChars += cost
                }
            }

        return SnippetSelection(
            snippets = keptNewestToOldest.sortedBy { it.timestamp },
            budget =
                ContextBudgetDebugInfo(
                    budgetTokens = budgetTokens,
                    budgetChars = budgetChars,
                    usedChars = usedChars,
                    droppedChars = droppedChars,
                    keptItems = keptNewestToOldest.size,
                    droppedItems = droppedItems,
                ),
        )
    }

    private fun sourceLineRange(
        document: Document,
        startOffsets: IntArray,
        endOffsets: IntArray,
    ): IntRange? {
        val rangeCount = min(startOffsets.size, endOffsets.size)
        if (rangeCount == 0) return null

        var startLine = Int.MAX_VALUE
        var endLine = 0
        for (index in 0 until rangeCount) {
            val startOffset = startOffsets[index].coerceIn(0, document.textLength)
            val endOffset = endOffsets[index].coerceIn(0, document.textLength)
            val endProbe = if (endOffset > startOffset) endOffset - 1 else startOffset
            startLine = min(startLine, document.getLineNumber(startOffset))
            endLine = max(endLine, document.getLineNumber(endProbe.coerceIn(0, document.textLength)))
        }

        return if (startLine == Int.MAX_VALUE) null else startLine..endLine
    }

    companion object {
        private const val MAX_COPIED_SNIPPETS = 16
    }
}

class FreddieCopyPastePreProcessor : CopyPastePreProcessor {
    override fun preprocessOnCopy(
        file: PsiFile,
        startOffsets: IntArray,
        endOffsets: IntArray,
        text: String,
    ): String {
        if (!file.project.isDisposed) {
            file.project.service<CopiedSnippetTracker>().recordCopy(file, startOffsets, endOffsets, text)
        }
        return text
    }

    override fun preprocessOnPaste(
        project: Project,
        file: PsiFile,
        editor: Editor,
        text: String,
        rawText: RawText,
    ): String = text
}
