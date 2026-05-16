package com.github.cvzakharchenko.freddie.actions

import com.github.cvzakharchenko.freddie.controller.MercuryNextEditController
import com.github.cvzakharchenko.freddie.settings.FreddieSettings
import com.github.cvzakharchenko.freddie.trigger.TriggerKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapManager
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class RequestMercuryNextEditAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = editor(event) ?: return
        project.service<MercuryNextEditController>().requestSuggestion(editor, TriggerKind.MANUAL)
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val editor = editor(event)
        event.presentation.isEnabled = project != null && editor != null && FreddieSettings.getInstance().nextEditEnabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

class AcceptMercuryNextEditAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.service<MercuryNextEditController>()?.acceptCurrentSuggestion()
    }

    override fun update(event: AnActionEvent) {
        updateAcceptPresentation(event, ACCEPT_ACTION_ID, INSERT_INLINE_COMPLETION_ACTION_ID)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

class AcceptMercuryNextEditWordAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.service<MercuryNextEditController>()?.acceptNextSuggestionWord()
    }

    override fun update(event: AnActionEvent) {
        updateAcceptPresentation(event, ACCEPT_WORD_ACTION_ID, INSERT_INLINE_COMPLETION_WORD_ACTION_ID)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

class AcceptMercuryNextEditLineAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.service<MercuryNextEditController>()?.acceptNextSuggestionLine()
    }

    override fun update(event: AnActionEvent) {
        updateAcceptPresentation(event, ACCEPT_LINE_ACTION_ID, INSERT_INLINE_COMPLETION_LINE_ACTION_ID)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

class DismissMercuryNextEditAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.service<MercuryNextEditController>()?.dismissCurrentSuggestion()
    }

    override fun update(event: AnActionEvent) {
        val controller = event.project?.service<MercuryNextEditController>()
        event.presentation.isEnabledAndVisible = controller?.hasVisibleSuggestion() == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private fun updateAcceptPresentation(
    event: AnActionEvent,
    actionId: String,
    inheritedActionId: String,
) {
    val controller = event.project?.service<MercuryNextEditController>()
    event.presentation.isEnabledAndVisible =
        controller?.hasVisibleSuggestion() == true &&
        isInheritedShortcutAllowed(event, actionId, inheritedActionId)
}

private fun isInheritedShortcutAllowed(
    event: AnActionEvent,
    actionId: String,
    inheritedActionId: String,
): Boolean {
    val keyStroke = (event.inputEvent as? KeyEvent)?.let(KeyStroke::getKeyStrokeForEvent) ?: return true
    val keymap = KeymapManager.getInstance().activeKeymap
    val inheritedShortcuts = keymap.getShortcuts(inheritedActionId).filterIsInstance<KeyboardShortcut>()
    val freddieShortcuts = keymap.getShortcuts(actionId).filterIsInstance<KeyboardShortcut>()
    val separateFreddieShortcuts =
        freddieShortcuts.filterNot { freddieShortcut ->
            inheritedShortcuts.any { inheritedShortcut -> inheritedShortcut.sameAs(freddieShortcut) }
        }
    if (separateFreddieShortcuts.isEmpty()) return true
    return separateFreddieShortcuts.any { it.matchesFirstKeyStroke(keyStroke) }
}

private fun KeyboardShortcut.sameAs(other: KeyboardShortcut): Boolean =
    firstKeyStroke == other.firstKeyStroke && secondKeyStroke == other.secondKeyStroke

private fun KeyboardShortcut.matchesFirstKeyStroke(keyStroke: KeyStroke): Boolean =
    firstKeyStroke == keyStroke

private fun editor(event: AnActionEvent): Editor? = event.getData(CommonDataKeys.EDITOR)

private const val ACCEPT_ACTION_ID = "com.github.cvzakharchenko.freddie.AcceptMercuryNextEdit"
private const val ACCEPT_WORD_ACTION_ID = "com.github.cvzakharchenko.freddie.AcceptMercuryNextEditWord"
private const val ACCEPT_LINE_ACTION_ID = "com.github.cvzakharchenko.freddie.AcceptMercuryNextEditLine"
private const val INSERT_INLINE_COMPLETION_ACTION_ID = "InsertInlineCompletionAction"
private const val INSERT_INLINE_COMPLETION_WORD_ACTION_ID = "InsertInlineCompletionWordAction"
private const val INSERT_INLINE_COMPLETION_LINE_ACTION_ID = "InsertInlineCompletionLineAction"
