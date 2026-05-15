package com.github.cvzakharchenko.freddie.actions

import com.github.cvzakharchenko.freddie.controller.MercuryNextEditController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager

@Service(Service.Level.APP)
class MercuryEditorActionRouterService : Disposable {
    private var installed = false
    private var originalTabHandler: EditorActionHandler? = null
    private var originalEscapeHandler: EditorActionHandler? = null

    fun ensureInstalled() {
        if (installed) return

        val actionManager = EditorActionManager.getInstance()
        originalTabHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_TAB)
        originalEscapeHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE)

        originalTabHandler?.let { original ->
            actionManager.setActionHandler(
                IdeActions.ACTION_EDITOR_TAB,
                RoutedEditorActionHandler(original) { it.acceptCurrentSuggestion() },
            )
        }
        originalEscapeHandler?.let { original ->
            actionManager.setActionHandler(
                IdeActions.ACTION_EDITOR_ESCAPE,
                RoutedEditorActionHandler(original) { it.dismissCurrentSuggestion() },
            )
        }

        installed = true
    }

    override fun dispose() {
        if (!installed) return
        val actionManager = EditorActionManager.getInstance()
        originalTabHandler?.let { actionManager.setActionHandler(IdeActions.ACTION_EDITOR_TAB, it) }
        originalEscapeHandler?.let { actionManager.setActionHandler(IdeActions.ACTION_EDITOR_ESCAPE, it) }
        installed = false
    }
}

private class RoutedEditorActionHandler(
    private val original: EditorActionHandler,
    private val route: (MercuryNextEditController) -> Boolean,
) : EditorActionHandler() {
    override fun doExecute(
        editor: Editor,
        caret: Caret?,
        dataContext: DataContext?,
    ) {
        val controller = editor.project?.service<MercuryNextEditController>()
        if (controller != null && controller.hasVisibleSuggestion() && route(controller)) {
            return
        }
        original.execute(editor, caret, dataContext)
    }
}
