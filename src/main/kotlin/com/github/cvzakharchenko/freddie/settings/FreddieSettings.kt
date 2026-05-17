package com.github.cvzakharchenko.freddie.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.awt.Color

enum class FreddieSuggestionDisplayMode(
    private val label: String,
) {
    GHOST_TEXT("Ghost text"),
    LINE_HINT("Line hint"),
    ;

    override fun toString(): String = label

    companion object {
        fun fromStorageName(value: String): FreddieSuggestionDisplayMode =
            entries.firstOrNull { it.name == value || it.label == value } ?: GHOST_TEXT
    }
}

@Service
@State(name = "FreddieSettings", storages = [Storage("freddie.xml")])
class FreddieSettings : PersistentStateComponent<FreddieSettings.State> {
    data class State(
        var nextEditEnabled: Boolean = true,
        var triggerOnEdit: Boolean = true,
        var chainedSuggestionsEnabled: Boolean = true,
        var pauseOnDismiss: Boolean = false,
        var suggestionDisplayMode: String = FreddieSuggestionDisplayMode.GHOST_TEXT.name,
        var customLineHintInsertedColor: String = "",
        var customLineHintMatchedColor: String = "",
        var customGhostTextInsertedBackgroundColor: String = "",
        var customGhostTextMatchedBackgroundColor: String = "",
        var debounceMs: Int = 200,
    )

    private var settingsState = State()

    override fun getState(): State = settingsState

    override fun loadState(state: State) {
        settingsState = normalizeState(state)
    }

    var nextEditEnabled: Boolean
        get() = settingsState.nextEditEnabled
        set(value) {
            replaceState(settingsState.copy(nextEditEnabled = value))
        }

    var triggerOnEdit: Boolean
        get() = settingsState.triggerOnEdit
        set(value) {
            replaceState(settingsState.copy(triggerOnEdit = value))
        }

    var chainedSuggestionsEnabled: Boolean
        get() = settingsState.chainedSuggestionsEnabled
        set(value) {
            replaceState(settingsState.copy(chainedSuggestionsEnabled = value))
        }

    var pauseOnDismiss: Boolean
        get() = settingsState.pauseOnDismiss
        set(value) {
            replaceState(settingsState.copy(pauseOnDismiss = value))
        }

    var debounceMs: Int
        get() = settingsState.debounceMs
        set(value) {
            replaceState(settingsState.copy(debounceMs = value.coerceIn(MIN_DEBOUNCE_MS, MAX_DEBOUNCE_MS)))
        }

    var suggestionDisplayMode: FreddieSuggestionDisplayMode
        get() = FreddieSuggestionDisplayMode.fromStorageName(settingsState.suggestionDisplayMode)
        set(value) {
            replaceState(settingsState.copy(suggestionDisplayMode = value.name))
        }

    var customLineHintInsertedColor: Color?
        get() = settingsState.customLineHintInsertedColor.takeIf { it.isNotBlank() }?.let(::parseColor)
        set(value) {
            replaceState(settingsState.copy(customLineHintInsertedColor = value?.toStorageString().orEmpty()))
        }

    var customLineHintMatchedColor: Color?
        get() = settingsState.customLineHintMatchedColor.takeIf { it.isNotBlank() }?.let(::parseColor)
        set(value) {
            replaceState(settingsState.copy(customLineHintMatchedColor = value?.toStorageString().orEmpty()))
        }

    var customGhostTextInsertedBackgroundColor: Color?
        get() = settingsState.customGhostTextInsertedBackgroundColor.takeIf { it.isNotBlank() }?.let(::parseColor)
        set(value) {
            replaceState(settingsState.copy(customGhostTextInsertedBackgroundColor = value?.toStorageString().orEmpty()))
        }

    var customGhostTextMatchedBackgroundColor: Color?
        get() = settingsState.customGhostTextMatchedBackgroundColor.takeIf { it.isNotBlank() }?.let(::parseColor)
        set(value) {
            replaceState(settingsState.copy(customGhostTextMatchedBackgroundColor = value?.toStorageString().orEmpty()))
        }

    private fun replaceState(nextState: State) {
        val normalizedState = normalizeState(nextState)
        if (settingsState == normalizedState) return

        settingsState = normalizedState
    }

    private fun normalizeState(state: State): State =
        state.copy(
            debounceMs = state.debounceMs.coerceIn(MIN_DEBOUNCE_MS, MAX_DEBOUNCE_MS),
            suggestionDisplayMode = FreddieSuggestionDisplayMode.fromStorageName(state.suggestionDisplayMode).name,
            customLineHintInsertedColor = normalizeColor(state.customLineHintInsertedColor),
            customLineHintMatchedColor = normalizeColor(state.customLineHintMatchedColor),
            customGhostTextInsertedBackgroundColor = normalizeColor(state.customGhostTextInsertedBackgroundColor),
            customGhostTextMatchedBackgroundColor = normalizeColor(state.customGhostTextMatchedBackgroundColor),
        )

    companion object {
        const val MIN_DEBOUNCE_MS = 10
        const val MAX_DEBOUNCE_MS = 5000

        fun getInstance(): FreddieSettings = service()

        private fun normalizeColor(value: String): String =
            value
                .takeIf { it.isNotBlank() }
                ?.let(::parseColor)
                ?.toStorageString()
                .orEmpty()

        private fun parseColor(value: String): Color? =
            runCatching {
                val normalized = value.removePrefix("#")
                if (normalized.length != 6) return null
                Color(normalized.toInt(16))
            }.getOrNull()

        private fun Color.toStorageString(): String =
            "%02X%02X%02X".format(red, green, blue)
    }
}
