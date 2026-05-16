package com.github.cvzakharchenko.freddie.startup

import com.github.cvzakharchenko.freddie.actions.MercuryInlineCompletionActionBridgeService
import com.github.cvzakharchenko.freddie.controller.MercuryNextEditController
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class FreddieProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().service<MercuryInlineCompletionActionBridgeService>().ensureInstalled()
        project.service<MercuryNextEditController>().start()
    }
}
