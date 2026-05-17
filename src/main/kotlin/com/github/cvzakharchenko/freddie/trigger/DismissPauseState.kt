package com.github.cvzakharchenko.freddie.trigger

internal class DismissPauseState {
    private var pausedAfterDismiss = false

    fun isPaused(pauseOnDismissEnabled: Boolean): Boolean = pauseOnDismissEnabled && pausedAfterDismiss

    fun pauseIfEnabled(pauseOnDismissEnabled: Boolean): Boolean {
        if (!pauseOnDismissEnabled) {
            pausedAfterDismiss = false
            return false
        }
        pausedAfterDismiss = true
        return true
    }

    fun resume(): Boolean {
        val wasPaused = pausedAfterDismiss
        pausedAfterDismiss = false
        return wasPaused
    }
}
