package com.github.cvzakharchenko.freddie.startup

import com.github.cvzakharchenko.freddie.actions.MercuryEditorActionRouterService
import com.github.cvzakharchenko.freddie.controller.MercuryNextEditController
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class FreddieProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().service<MercuryEditorActionRouterService>().ensureInstalled()
        project.service<MercuryNextEditController>().start()
    }
}
