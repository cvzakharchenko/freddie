package com.github.cvzakharchenko.freddie.debug

import com.github.cvzakharchenko.freddie.context.MercuryContextDebugInfo
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
    private val statusLabel = JBLabel()
    private val settingsLabel = JBLabel()
    private val triggerLabel = JBLabel()
    private val suggestionLabel = JBLabel()
    private val eventLabel = JBLabel()
    private val contextArea = debugTextArea()
    private val codeToEditArea = debugTextArea()
    private val promptArea = debugTextArea()
    private val responseArea = debugTextArea()
    private val eventsArea = debugTextArea()

    val component: JComponent =
        JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(buildSummaryPanel(), BorderLayout.NORTH)
            add(buildTabs(), BorderLayout.CENTER)
        }

    init {
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
        settingsLabel.text = "Enabled: ${snapshot.enabled}    Debounce: ${snapshot.debounceMs}ms    API key: ${snapshot.apiKeySource}"
        triggerLabel.text = "Trigger: ${snapshot.lastTrigger}    Decision: ${snapshot.lastDecision}"
        suggestionLabel.text = "Suggestion visible: ${snapshot.visibleSuggestion}"
        eventLabel.text = "Last event: ${snapshot.lastEvent}    Updated: ${formatTime(snapshot.updatedAtMillis)}"
        contextArea.text = formatContext(snapshot.context)
        codeToEditArea.text = snapshot.lastCodeToEdit.ifBlank { "No code_to_edit block captured yet." }
        promptArea.text = snapshot.lastPrompt.ifBlank { "No prompt captured yet." }
        responseArea.text = formatResponse(snapshot)
        eventsArea.text = snapshot.events.joinToString("\n").ifBlank { "No activity yet." }
        listOf(contextArea, codeToEditArea, promptArea, responseArea, eventsArea).forEach { it.caretPosition = 0 }
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
            addTab("Prompt", JBScrollPane(promptArea))
            addTab("Response", JBScrollPane(responseArea))
            addTab("Events", JBScrollPane(eventsArea))
        }

    override fun dispose() {
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
            appendLine("recently viewed snippets: ${context.snippets.size}")
            context.snippets.forEachIndexed { index, snippet ->
                appendLine("${index + 1}. ${snippet.filePath}:${snippet.startLine}-${snippet.endLine} (${snippet.charCount} chars)")
            }
        }
    }

    private fun formatResponse(snapshot: FreddieDebugSnapshot): String =
        buildString {
            appendLine(snapshot.lastResponseSummary)
            if (snapshot.lastError.isNotBlank()) {
                appendLine()
                appendLine("Error:")
                appendLine(snapshot.lastError)
            }
            if (snapshot.lastResponseText.isNotBlank()) {
                appendLine()
                appendLine("Choice text:")
                appendLine(snapshot.lastResponseText)
            }
            if (snapshot.lastRawResponseBody.isNotBlank()) {
                appendLine()
                appendLine("Raw response body:")
                appendLine(snapshot.lastRawResponseBody)
            }
        }

    private fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(TIME_FORMAT)

    companion object {
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        private fun debugTextArea(): JBTextArea =
            JBTextArea().apply {
                isEditable = false
                lineWrap = false
                font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            }
    }
}
