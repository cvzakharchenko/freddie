package com.github.cvzakharchenko.freddie.actions

import com.github.cvzakharchenko.freddie.controller.MercuryNextEditController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.keymap.KeymapManager

@Service(Service.Level.APP)
class MercuryInlineCompletionActionBridgeService : Disposable {
    private val originalHandlers = linkedMapOf<EditorAction, EditorActionHandler>()
    private var installed = false

    fun ensureInstalled() {
        if (installed) return
        installed = true

        wrapInlineCompletionHandler(
            sourceActionId = INSERT_INLINE_COMPLETION_ACTION_ID,
            freddieActionId = ACCEPT_ACTION_ID,
            route = { it.acceptCurrentSuggestion() },
        )
        wrapInlineCompletionHandler(
            sourceActionId = INSERT_INLINE_COMPLETION_WORD_ACTION_ID,
            freddieActionId = ACCEPT_WORD_ACTION_ID,
            route = { it.acceptNextSuggestionWord() },
        )
        wrapInlineCompletionHandler(
            sourceActionId = INSERT_INLINE_COMPLETION_LINE_ACTION_ID,
            freddieActionId = ACCEPT_LINE_ACTION_ID,
            route = { it.acceptNextSuggestionLine() },
        )
    }

    private fun wrapInlineCompletionHandler(
        sourceActionId: String,
        freddieActionId: String,
        route: (MercuryNextEditController) -> Boolean,
    ) {
        val action = ActionManager.getInstance().getAction(sourceActionId) as? EditorAction ?: return
        if (action in originalHandlers) return

        val original = action.setupHandler(
            MercuryInlineCompletionHandler(
                original = action.getHandler(),
                freddieActionId = freddieActionId,
                route = route,
            ),
        )
        originalHandlers[action] = original
    }

    override fun dispose() {
        if (!installed) return

        originalHandlers.forEach { (action, originalHandler) ->
            action.setupHandler(originalHandler)
        }
        originalHandlers.clear()
        installed = false
    }
}

private class MercuryInlineCompletionHandler(
    private val original: EditorActionHandler,
    private val freddieActionId: String,
    private val route: (MercuryNextEditController) -> Boolean,
) : EditorActionHandler() {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun isEnabled(
        editor: Editor,
        dataContext: DataContext,
    ): Boolean =
        controller(editor)?.let { shouldRouteToFreddie(it) } == true ||
            original.isEnabled(editor, dataContext)

    override fun executeInCommand(
        editor: Editor,
        dataContext: DataContext,
    ): Boolean =
        if (controller(editor)?.let { shouldRouteToFreddie(it) } == true) {
            false
        } else {
            original.executeInCommand(editor, dataContext)
        }

    override fun runForAllCarets(): Boolean = original.runForAllCarets()

    override fun reverseCaretOrder(): Boolean = original.reverseCaretOrder()

    override fun getCommandGroupId(editor: Editor) = original.getCommandGroupId(editor)

    override fun doExecute(
        editor: Editor,
        caret: Caret?,
        dataContext: DataContext?,
    ) {
        val controller = controller(editor)
        if (controller != null && shouldRouteToFreddie(controller) && route(controller)) {
            return
        }
        original.execute(editor, caret, dataContext)
    }

    private fun shouldRouteToFreddie(controller: MercuryNextEditController): Boolean =
        controller.hasVisibleSuggestion() && !hasFreddieSpecificShortcut()

    private fun hasFreddieSpecificShortcut(): Boolean =
        KeymapManager
            .getInstance()
            .activeKeymap
            .getShortcuts(freddieActionId)
            .isNotEmpty()

    private fun controller(editor: Editor): MercuryNextEditController? =
        editor.project?.service<MercuryNextEditController>()
}

internal const val ACCEPT_ACTION_ID = "com.github.cvzakharchenko.freddie.AcceptMercuryNextEdit"
internal const val ACCEPT_WORD_ACTION_ID = "com.github.cvzakharchenko.freddie.AcceptMercuryNextEditWord"
internal const val ACCEPT_LINE_ACTION_ID = "com.github.cvzakharchenko.freddie.AcceptMercuryNextEditLine"
internal const val INSERT_INLINE_COMPLETION_ACTION_ID = "InsertInlineCompletionAction"
internal const val INSERT_INLINE_COMPLETION_WORD_ACTION_ID = "InsertInlineCompletionWordAction"
internal const val INSERT_INLINE_COMPLETION_LINE_ACTION_ID = "InsertInlineCompletionLineAction"
