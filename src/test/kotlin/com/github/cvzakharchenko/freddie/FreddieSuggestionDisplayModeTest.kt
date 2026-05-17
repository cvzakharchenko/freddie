package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.settings.FreddieSettings
import com.github.cvzakharchenko.freddie.settings.FreddieSuggestionDisplayMode
import java.awt.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class FreddieSuggestionDisplayModeTest {
    @Test
    fun `parses persisted storage names`() {
        assertEquals(
            FreddieSuggestionDisplayMode.LINE_HINT,
            FreddieSuggestionDisplayMode.fromStorageName("LINE_HINT"),
        )
    }

    @Test
    fun `parses old persisted display labels`() {
        assertEquals(
            FreddieSuggestionDisplayMode.LINE_HINT,
            FreddieSuggestionDisplayMode.fromStorageName("Line hint"),
        )
    }

    @Test
    fun `falls back to ghost text for unknown persisted names`() {
        assertEquals(
            FreddieSuggestionDisplayMode.GHOST_TEXT,
            FreddieSuggestionDisplayMode.fromStorageName("unknown"),
        )
    }

    @Test
    fun `stores custom line hint inserted color as hex`() {
        val settings = FreddieSettings()

        settings.customLineHintInsertedColor = Color(0x12, 0xAB, 0xEF)

        assertEquals("12ABEF", settings.state.customLineHintInsertedColor)
        assertEquals(Color(0x12, 0xAB, 0xEF), settings.customLineHintInsertedColor)
    }

    @Test
    fun `clears invalid custom line hint inserted color from loaded state`() {
        val settings = FreddieSettings()

        settings.loadState(FreddieSettings.State(customLineHintInsertedColor = "not a color"))

        assertEquals("", settings.state.customLineHintInsertedColor)
        assertEquals(null, settings.customLineHintInsertedColor)
    }
}
