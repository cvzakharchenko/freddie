package com.github.cvzakharchenko.freddie.trigger

import com.github.cvzakharchenko.freddie.settings.FreddieSettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

data class MercuryTriggerDecision(
    val shouldRequest: Boolean,
    val reason: String,
)

class MercuryTriggerPolicy(
    private val project: Project,
) {
    fun shouldRequestAfterTypedEdit(
        editor: Editor,
        event: DocumentEvent,
    ): Boolean = decisionAfterTypedEdit(editor, event).shouldRequest

    fun decisionAfterTypedEdit(
        editor: Editor,
        event: DocumentEvent,
    ): MercuryTriggerDecision {
        if (!FreddieSettings.getInstance().nextEditEnabled) return skipped("next edit prediction is disabled")
        if (project.isDisposed) return skipped("project is disposed")
        if (editor.isDisposed) return skipped("editor is disposed")
        if (editor.project != project) return skipped("editor belongs to a different project")
        if (editor.selectionModel.hasSelection()) return skipped("editor has a selection")
        if (editor.caretModel.caretCount != 1) return skipped("editor has multiple carets")
        if (editor.document.isInBulkUpdate) return skipped("document is in bulk update")
        if (!event.document.isWritable) return skipped("document is not writable")
        if (event.oldFragment == event.newFragment) return skipped("document event did not change text")
        if (event.document.textLength > MAX_DOCUMENT_CHARS) return skipped("document is larger than $MAX_DOCUMENT_CHARS characters")

        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return skipped("document has no virtual file")
        if (!file.isValid) return skipped("virtual file is invalid")
        if (file.isDirectory) return skipped("virtual file is a directory")

        return MercuryTriggerDecision(true, "typed edit accepted")
    }

    companion object {
        private const val MAX_DOCUMENT_CHARS = 1_000_000

        private fun skipped(reason: String): MercuryTriggerDecision = MercuryTriggerDecision(false, reason)
    }
}
