package com.github.cvzakharchenko.freddie.debug

import com.github.cvzakharchenko.freddie.context.ContextBudgetDebugInfo
import com.github.cvzakharchenko.freddie.context.MercuryContextDebugInfo
import com.github.cvzakharchenko.freddie.controller.MercuryNextEditController
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class FreddieDebugToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val panel = FreddieDebugPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

private class FreddieDebugPanel(
    project: Project,
) : FreddieDebugListener,
    Disposable {
    private val service = project.service<FreddieDebugStateService>()
    private val controller = project.service<MercuryNextEditController>()
    private val previewAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val statusLabel = JBLabel()
    private val settingsLabel = JBLabel()
    private val triggerLabel = JBLabel()
    private val suggestionLabel = JBLabel()
    private val eventLabel = JBLabel()
    private val contextArea = debugTextArea()
    private val codeToEditArea = debugTextArea()
    private val recentEditsArea = debugTextArea()
    private val copiedSnippetsArea = debugTextArea()
    private val promptArea = debugTextArea()
    private val responseSummaryArea = debugTextArea()
    private val responseChoiceArea = debugTextArea(editable = true)
    private val responseRawArea = debugTextArea()
    private val eventsArea = debugTextArea()
    private var renderedResponseRevision = Long.MIN_VALUE
    private var updatingChoiceText = false

    val component: JComponent =
        JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(buildSummaryPanel(), BorderLayout.NORTH)
            add(buildTabs(), BorderLayout.CENTER)
        }

    init {
        responseChoiceArea.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = scheduleEditedChoicePreview()

                override fun removeUpdate(event: DocumentEvent) = scheduleEditedChoicePreview()

                override fun changedUpdate(event: DocumentEvent) = scheduleEditedChoicePreview()
            },
        )
        service.addListener(this, this)
    }

    override fun snapshotChanged(snapshot: FreddieDebugSnapshot) {
        if (SwingUtilities.isEventDispatchThread()) {
            render(snapshot)
        } else {
            SwingUtilities.invokeLater { render(snapshot) }
        }
    }

    private fun render(snapshot: FreddieDebugSnapshot) {
        statusLabel.text = "Status: ${snapshot.connectionStatus}"
        settingsLabel.text =
            "Enabled: ${snapshot.enabled}    Trigger on edit: ${snapshot.triggerOnEdit}    " +
                "Display: ${snapshot.suggestionDisplayMode}    " +
                "Chain: ${snapshot.chainedSuggestions}    Pause on dismiss: ${formatPauseOnDismiss(snapshot)}    " +
                "Debounce: ${snapshot.debounceMs}ms    API key: ${snapshot.apiKeySource}"
        triggerLabel.text = "Trigger: ${snapshot.lastTrigger}    Decision: ${snapshot.lastDecision}"
        suggestionLabel.text = "Suggestion visible: ${snapshot.visibleSuggestion}"
        eventLabel.text = "Last event: ${snapshot.lastEvent}    Updated: ${formatTime(snapshot.updatedAtMillis)}"
        contextArea.text = formatContext(snapshot.context)
        codeToEditArea.text = snapshot.lastCodeToEdit.ifBlank { "No code_to_edit block captured yet." }
        recentEditsArea.text = formatRecentEdits(snapshot.recentEditDiffsOldestToNewest)
        copiedSnippetsArea.text = formatCopiedSnippets(snapshot.context)
        promptArea.text = snapshot.lastPrompt.ifBlank { "No prompt captured yet." }
        responseSummaryArea.text = formatResponseSummary(snapshot)
        responseRawArea.text = snapshot.lastRawResponseBody.ifBlank { "No raw response body captured yet." }
        if (snapshot.responseRevision != renderedResponseRevision) {
            renderedResponseRevision = snapshot.responseRevision
            setChoiceText(snapshot.lastResponseText, preview = false)
        }
        eventsArea.text = snapshot.events.joinToString("\n").ifBlank { "No activity yet." }
        listOf(contextArea, codeToEditArea, recentEditsArea, copiedSnippetsArea, promptArea, responseSummaryArea, responseRawArea, eventsArea)
            .forEach { it.caretPosition = 0 }
    }

    private fun buildSummaryPanel(): JComponent =
        JPanel(GridBagLayout()).apply {
            addRow(0, statusLabel)
            addRow(1, settingsLabel)
            addRow(2, triggerLabel)
            addRow(3, suggestionLabel)
            addRow(4, eventLabel)
            add(
                JButton("Clear").apply { addActionListener { service.clear() } },
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = 0
                    gridheight = 2
                    anchor = GridBagConstraints.NORTHEAST
                    insets = JBUI.insets(0, 8, 8, 0)
                },
            )
        }

    private fun JPanel.addRow(
        row: Int,
        component: JComponent,
    ) {
        add(
            component,
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
                insets = JBUI.insetsBottom(4)
            },
        )
    }

    private fun buildTabs(): JComponent =
        JTabbedPane().apply {
            addTab("Context", JBScrollPane(contextArea))
            addTab("code_to_edit", JBScrollPane(codeToEditArea))
            addTab("Recent Edits", JBScrollPane(recentEditsArea))
            addTab("Copied Snippets", JBScrollPane(copiedSnippetsArea))
            addTab("Prompt", JBScrollPane(promptArea))
            addTab("Response", buildResponseTab())
            addTab("Events", JBScrollPane(eventsArea))
        }

    private fun buildResponseTab(): JComponent =
        JPanel(GridBagLayout()).apply {
            addResponseHeader(row = 0, title = "Summary")
            addResponseBody(row = 1, component = JBScrollPane(responseSummaryArea), weighty = 0.12)
            add(
                JPanel(BorderLayout()).apply {
                    add(JBLabel("Choice text"), BorderLayout.WEST)
                    add(
                        JPanel().apply {
                            add(
                                JButton("Reset to Mercury choice").apply {
                                    addActionListener { setChoiceText(service.currentSnapshot().lastResponseText, preview = true) }
                                },
                            )
                            add(
                                JButton("Dismiss preview").apply {
                                    addActionListener { controller.dismissCurrentSuggestion() }
                                },
                            )
                        },
                        BorderLayout.EAST,
                    )
                },
                responseHeaderConstraints(row = 2),
            )
            addResponseBody(row = 3, component = JBScrollPane(responseChoiceArea), weighty = 0.58)
            addResponseHeader(row = 4, title = "Raw response body")
            addResponseBody(row = 5, component = JBScrollPane(responseRawArea), weighty = 0.30)
        }

    private fun JPanel.addResponseHeader(
        row: Int,
        title: String,
    ) {
        add(JBLabel(title), responseHeaderConstraints(row))
    }

    private fun JPanel.addResponseBody(
        row: Int,
        component: JComponent,
        weighty: Double,
    ) {
        add(
            component,
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                weightx = 1.0
                this.weighty = weighty
                fill = GridBagConstraints.BOTH
                insets = JBUI.insets(0, 0, 8, 0)
            },
        )
    }

    private fun responseHeaderConstraints(row: Int): GridBagConstraints =
        GridBagConstraints().apply {
            gridx = 0
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(0, 0, 4, 0)
        }

    private fun setChoiceText(
        text: String,
        preview: Boolean,
    ) {
        updatingChoiceText = true
        try {
            responseChoiceArea.text = text
            responseChoiceArea.caretPosition = 0
        } finally {
            updatingChoiceText = false
        }
        if (preview) {
            scheduleEditedChoicePreview()
        }
    }

    private fun scheduleEditedChoicePreview() {
        if (updatingChoiceText) return
        previewAlarm.cancelAllRequests()
        previewAlarm.addRequest(
            { controller.previewEditedChoiceText(responseChoiceArea.text) },
            DEBUG_PREVIEW_DEBOUNCE_MS,
        )
    }

    override fun dispose() {
        previewAlarm.cancelAllRequests()
    }

    private fun formatContext(context: MercuryContextDebugInfo?): String {
        if (context == null) return "No context captured yet."
        return buildString {
            appendLine("file: ${context.filePath}")
            appendLine("document: ${context.documentTextLength} chars, ${context.documentLineCount} lines")
            appendLine("caret offset: ${context.caretOffset}")
            appendLine("modification stamp: ${context.modificationStamp}")
            appendLine("editable lines: ${context.editableStartLine + 1}-${context.editableEndLine + 1}")
            appendLine("editable offsets: ${context.editableStartOffset}-${context.editableEndOffset}")
            appendLine("editable chars: ${context.editableCharCount}")
            appendLine("before cursor chars: ${context.beforeCursorCharCount}")
            appendLine("after cursor chars: ${context.afterCursorCharCount}")
            appendLine("code above chars: ${context.codeAboveCharCount}")
            appendLine("code below chars: ${context.codeBelowCharCount}")
            appendLine("recent edit diffs: ${context.editDiffCount}")
            appendLine("prompt chars: ${context.promptCharCount}")
            appendLine()
            appendLine("budgets:")
            appendLine("  current file: ${formatBudget(context.currentFileBudget)}")
            appendLine("  recent edits: ${formatBudget(context.recentEditsBudget)}")
            appendLine("  viewed snippets: ${formatBudget(context.viewedSnippetsBudget)}")
            appendLine("  copied snippets: ${formatBudget(context.copiedSnippetsBudget)}")
            appendLine()
            appendLine("recently viewed snippets: ${context.viewedSnippets.size}")
            context.viewedSnippets.forEachIndexed { index, snippet ->
                appendLine("${index + 1}. ${snippet.filePath}:${snippet.startLine}-${snippet.endLine} (${snippet.charCount} chars)")
            }
            appendLine()
            appendLine("recently copied snippets: ${context.copiedSnippets.size}")
            context.copiedSnippets.forEachIndexed { index, snippet ->
                appendLine("${index + 1}. ${snippet.filePath}:${snippet.startLine}-${snippet.endLine} (${snippet.charCount} chars)")
            }
        }
    }

    private fun formatBudget(budget: ContextBudgetDebugInfo): String =
        "${budget.usedChars}/${budget.budgetChars} chars " +
            "(${budget.budgetTokens} token budget), " +
            "kept ${budget.keptItems}, dropped ${budget.droppedItems}, dropped chars ${budget.droppedChars}"

    private fun formatRecentEdits(diffs: List<String>): String {
        if (diffs.isEmpty()) return "No recent edit diffs captured yet."

        return buildString {
            appendLine("${diffs.size} recent edit diff(s), oldest to newest")
            diffs.forEachIndexed { index, diff ->
                appendLine()
                appendLine("### ${index + 1}")
                append(diff)
                if (!diff.endsWith("\n")) appendLine()
            }
        }
    }

    private fun formatCopiedSnippets(context: MercuryContextDebugInfo?): String {
        if (context == null) return "No context captured yet."
        if (context.copiedSnippets.isEmpty()) return "No copied snippets captured in the last request context."

        return buildString {
            appendLine("${context.copiedSnippets.size} copied snippet(s), oldest to newest in the prompt")
            context.copiedSnippets.forEachIndexed { index, snippet ->
                appendLine()
                appendLine("### ${index + 1}")
                appendLine("${snippet.filePath}:${snippet.startLine}-${snippet.endLine} (${snippet.charCount} chars)")
                appendLine()
                append(snippet.text)
                if (!snippet.text.endsWith("\n")) appendLine()
            }
        }
    }

    private fun formatResponseSummary(snapshot: FreddieDebugSnapshot): String =
        buildString {
            appendLine(snapshot.lastResponseSummary)
            if (snapshot.lastError.isNotBlank()) {
                appendLine()
                appendLine("Error:")
                appendLine(snapshot.lastError)
            }
        }

    private fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(TIME_FORMAT)

    private fun formatPauseOnDismiss(snapshot: FreddieDebugSnapshot): String =
        if (snapshot.pauseOnDismiss) {
            if (snapshot.editTriggersPaused) "on, paused" else "on"
        } else {
            "off"
        }

    companion object {
        private const val DEBUG_PREVIEW_DEBOUNCE_MS = 75
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        private fun debugTextArea(editable: Boolean = false): JBTextArea =
            JBTextArea().apply {
                isEditable = editable
                lineWrap = false
                font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            }
    }
}
