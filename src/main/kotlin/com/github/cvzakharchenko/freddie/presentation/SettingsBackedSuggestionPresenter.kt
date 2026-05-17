package com.github.cvzakharchenko.freddie.presentation

import com.github.cvzakharchenko.freddie.settings.FreddieSettings
import com.github.cvzakharchenko.freddie.settings.FreddieSuggestionDisplayMode

class SettingsBackedSuggestionPresenter : SuggestionPresenter {
    private val ghostTextPresenter = LineGhostTextPresenter()
    private val lineHintPresenter = LineHintPresenter()

    override fun show(suggestion: MercurySuggestion): PresentedSuggestion? =
        when (FreddieSettings.getInstance().suggestionDisplayMode) {
            FreddieSuggestionDisplayMode.GHOST_TEXT -> {
                lineHintPresenter.dispose()
                ghostTextPresenter.show(suggestion)
            }
            FreddieSuggestionDisplayMode.LINE_HINT -> {
                ghostTextPresenter.dispose()
                lineHintPresenter.show(suggestion)
            }
        }

    override fun dispose() {
        ghostTextPresenter.dispose()
        lineHintPresenter.dispose()
    }
}
