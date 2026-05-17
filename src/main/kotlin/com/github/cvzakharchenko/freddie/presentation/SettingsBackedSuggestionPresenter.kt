package com.github.cvzakharchenko.freddie.presentation

import com.github.cvzakharchenko.freddie.settings.FreddieSettings
import com.github.cvzakharchenko.freddie.settings.FreddieSuggestionDisplayMode

class SettingsBackedSuggestionPresenter : SuggestionPresenter {
    private val ghostTextPresenter = LineGhostTextPresenter()
    private val lineHintPresenter = LineHintPresenter()
    private var ghostTextOverride = false

    override fun show(suggestion: MercurySuggestion): PresentedSuggestion? =
        when (effectiveDisplayMode()) {
            FreddieSuggestionDisplayMode.GHOST_TEXT -> {
                lineHintPresenter.dispose()
                ghostTextPresenter.show(suggestion)
            }
            FreddieSuggestionDisplayMode.LINE_HINT -> {
                ghostTextPresenter.dispose()
                lineHintPresenter.show(suggestion)
            }
        }

    fun setGhostTextOverride(value: Boolean) {
        ghostTextOverride = value
    }

    private fun effectiveDisplayMode(): FreddieSuggestionDisplayMode {
        val configuredMode = FreddieSettings.getInstance().suggestionDisplayMode
        return if (configuredMode == FreddieSuggestionDisplayMode.LINE_HINT && ghostTextOverride) {
            FreddieSuggestionDisplayMode.GHOST_TEXT
        } else {
            configuredMode
        }
    }

    override fun dispose() {
        ghostTextPresenter.dispose()
        lineHintPresenter.dispose()
    }
}
