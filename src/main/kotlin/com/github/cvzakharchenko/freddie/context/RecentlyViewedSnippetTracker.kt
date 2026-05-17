package com.github.cvzakharchenko.freddie.context

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Point
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RecentlyViewedSnippetTracker(
    private val project: Project,
) {
    private data class ViewedLocation(
        val file: VirtualFile,
        val line: Int,
        val timestamp: Long,
    )

    private val viewedLocations = ArrayDeque<ViewedLocation>()

    fun record(editor: Editor) {
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val line = editor.caretModel.logicalPosition.line
        val previous = viewedLocations.lastOrNull()
        if (previous != null && previous.file == file && abs(previous.line - line) < LOCAL_MOVEMENT_LINES) {
            viewedLocations.removeLast()
        }
        viewedLocations.addLast(ViewedLocation(file, line, System.currentTimeMillis()))
        pruneViewedLocationsFor(file)
        while (viewedLocations.size > MAX_VIEWED_LOCATIONS) {
            viewedLocations.removeFirst()
        }
    }

    fun snippetsFor(
        currentEditor: Editor,
        currentFilePath: String,
        budgetChars: Int = ContextBudget.RECENT_SNIPPETS_CHARS,
        budgetTokens: Int = ContextBudget.RECENT_SNIPPETS_TOKENS,
    ): SnippetSelection {
        val snippets = mutableListOf<CodeSnippet>()

        viewedLocations
            .asReversed()
            .mapNotNull { snippetAround(it.file, it.line, it.timestamp) }
            .forEach { snippets.add(it) }

        selectedSplitSnippets(currentEditor).forEach { snippets.add(it) }

        return RecentlyViewedSnippetSelector.select(
            candidatesNewestToOldest = snippets,
            currentFilePath = currentFilePath,
            budgetChars = budgetChars,
            budgetTokens = budgetTokens,
        )
    }

    private fun selectedSplitSnippets(currentEditor: Editor): List<CodeSnippet> {
        val currentFile = FileDocumentManager.getInstance().getFile(currentEditor.document)
        return FileEditorManager
            .getInstance(project)
            .selectedFiles
            .mapNotNull { file ->
                if (file == currentFile) return@mapNotNull null
                val selectedEditor = FileEditorManager.getInstance(project).getSelectedEditor(file) as? TextEditor
                val editor = selectedEditor?.editor
                val line =
                    if (editor != null) {
                        val visibleArea = editor.scrollingModel.visibleArea
                        val start = editor.xyToLogicalPosition(Point(0, visibleArea.y)).line
                        val end = editor.xyToLogicalPosition(Point(0, visibleArea.y + visibleArea.height)).line
                        ((start + end) / 2).coerceAtLeast(0)
                    } else {
                        0
                    }
                snippetAround(file, line, System.currentTimeMillis())
            }
    }

    private fun snippetAround(
        file: VirtualFile,
        centerLine: Int,
        timestamp: Long,
    ): CodeSnippet? {
        if (file.isDirectory || !file.isValid) return null
        val text = textFor(file) ?: return null
        if (text.isBlank()) return null
        val lines = text.split('\n')
        if (lines.isEmpty()) return null

        val start = max(0, centerLine - SNIPPET_RADIUS_LINES)
        val end = min(lines.lastIndex, centerLine + SNIPPET_RADIUS_LINES)
        return CodeSnippet(
            filePath = projectRelativePath(project, file),
            startLine = start + 1,
            endLine = end + 1,
            text = lines.subList(start, end + 1).joinToString("\n"),
            timestamp = timestamp,
        )
    }

    private fun textFor(file: VirtualFile): String? {
        FileDocumentManager.getInstance().getDocument(file)?.let { return it.text }
        return runCatching { VfsUtilCore.loadText(file) }.getOrNull()
    }

    private fun pruneViewedLocationsFor(file: VirtualFile) {
        var locationsForFile = 0
        val newestToOldest = viewedLocations.listIterator(viewedLocations.size)
        while (newestToOldest.hasPrevious()) {
            val location = newestToOldest.previous()
            if (location.file != file) continue

            locationsForFile++
            if (locationsForFile > MAX_SNIPPETS_PER_FILE) {
                newestToOldest.remove()
            }
        }
    }

    companion object {
        private const val LOCAL_MOVEMENT_LINES = 50
        private const val MAX_VIEWED_LOCATIONS = 16
        private const val SNIPPET_RADIUS_LINES = 20
    }
}

internal object RecentlyViewedSnippetSelector {
    fun select(
        candidatesNewestToOldest: List<CodeSnippet>,
        currentFilePath: String,
        budgetChars: Int = ContextBudget.RECENT_SNIPPETS_CHARS,
        budgetTokens: Int = ContextBudget.RECENT_SNIPPETS_TOKENS,
    ): SnippetSelection {
        val keptPerFile = mutableMapOf<String, Int>()
        val seen = mutableSetOf<Pair<String, Int>>()
        var usedChars = 0
        var droppedChars = 0
        var droppedItems = 0
        val keptNewestToOldest = mutableListOf<CodeSnippet>()

        candidatesNewestToOldest
            .asSequence()
            .filter { seen.add(it.filePath to it.startLine) }
            .forEach { snippet ->
                val cost = snippet.promptCharCost()
                val fileSnippetCount = keptPerFile.getOrDefault(snippet.filePath, 0)
                if (
                    snippet.filePath == currentFilePath ||
                    keptNewestToOldest.size >= MAX_SNIPPETS ||
                    fileSnippetCount >= MAX_SNIPPETS_PER_FILE ||
                    cost > budgetChars - usedChars
                ) {
                    droppedItems++
                    droppedChars += cost
                } else {
                    keptNewestToOldest.add(snippet)
                    keptPerFile[snippet.filePath] = fileSnippetCount + 1
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

    private const val MAX_SNIPPETS = 8
}

private const val MAX_SNIPPETS_PER_FILE = 2
