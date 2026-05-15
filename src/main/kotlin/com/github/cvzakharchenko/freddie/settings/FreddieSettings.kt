package com.github.cvzakharchenko.freddie.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.PersistentStateComponent

@Service
@State(name = "FreddieSettings", storages = [Storage("freddie.xml")])
class FreddieSettings : PersistentStateComponent<FreddieSettings.State> {
    data class State(
        var nextEditEnabled: Boolean = true,
        var debounceMs: Int = 200,
    )

    private var settingsState = State()

    override fun getState(): State = settingsState

    override fun loadState(state: State) {
        settingsState = state.copy(debounceMs = state.debounceMs.coerceIn(MIN_DEBOUNCE_MS, MAX_DEBOUNCE_MS))
    }

    var nextEditEnabled: Boolean
        get() = settingsState.nextEditEnabled
        set(value) {
            settingsState.nextEditEnabled = value
        }

    var debounceMs: Int
        get() = settingsState.debounceMs
        set(value) {
            settingsState.debounceMs = value.coerceIn(MIN_DEBOUNCE_MS, MAX_DEBOUNCE_MS)
        }

    companion object {
        const val MIN_DEBOUNCE_MS = 10
        const val MAX_DEBOUNCE_MS = 5000

        fun getInstance(): FreddieSettings = service()
    }
}
