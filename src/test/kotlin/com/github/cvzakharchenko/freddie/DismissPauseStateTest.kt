package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.trigger.DismissPauseState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DismissPauseStateTest {
    @Test
    fun `dismiss pauses edit triggers only when the setting is enabled`() {
        val state = DismissPauseState()

        assertFalse(state.pauseIfEnabled(false))
        assertFalse(state.isPaused(pauseOnDismissEnabled = false))
        assertFalse(state.isPaused(pauseOnDismissEnabled = true))

        assertTrue(state.pauseIfEnabled(true))
        assertTrue(state.isPaused(pauseOnDismissEnabled = true))
        assertFalse(state.isPaused(pauseOnDismissEnabled = false))
    }

    @Test
    fun `manual request resumes edit triggers`() {
        val state = DismissPauseState()
        state.pauseIfEnabled(true)

        assertTrue(state.resume())
        assertFalse(state.isPaused(pauseOnDismissEnabled = true))
        assertFalse(state.resume())
    }
}
