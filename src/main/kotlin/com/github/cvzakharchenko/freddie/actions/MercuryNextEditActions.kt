package com.github.cvzakharchenko.freddie.actions

import com.github.cvzakharchenko.freddie.controller.MercuryNextEditController
import com.github.cvzakharchenko.freddie.settings.FreddieSettings
import com.github.cvzakharchenko.freddie.trigger.TriggerKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor

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
        updateAcceptPresentation(event)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

class AcceptMercuryNextEditWordAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.service<MercuryNextEditController>()?.acceptNextSuggestionWord()
    }

    override fun update(event: AnActionEvent) {
        updateAcceptPresentation(event)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

class AcceptMercuryNextEditLineAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.service<MercuryNextEditController>()?.acceptNextSuggestionLine()
    }

    override fun update(event: AnActionEvent) {
        updateAcceptPresentation(event)
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

private fun updateAcceptPresentation(event: AnActionEvent) {
    val controller = event.project?.service<MercuryNextEditController>()
    event.presentation.isEnabledAndVisible = controller?.hasVisibleSuggestion() == true
}

private fun editor(event: AnActionEvent): Editor? = event.getData(CommonDataKeys.EDITOR)
