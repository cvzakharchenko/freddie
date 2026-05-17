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
    fun `stores all custom suggestion colors as hex`() {
        val settings = FreddieSettings()

        settings.customLineHintMatchedColor = Color(0x10, 0x20, 0x30)
        settings.customGhostTextInsertedBackgroundColor = Color(0x40, 0x50, 0x60)
        settings.customGhostTextMatchedBackgroundColor = Color(0x70, 0x80, 0x90)

        assertEquals("102030", settings.state.customLineHintMatchedColor)
        assertEquals("405060", settings.state.customGhostTextInsertedBackgroundColor)
        assertEquals("708090", settings.state.customGhostTextMatchedBackgroundColor)
        assertEquals(Color(0x10, 0x20, 0x30), settings.customLineHintMatchedColor)
        assertEquals(Color(0x40, 0x50, 0x60), settings.customGhostTextInsertedBackgroundColor)
        assertEquals(Color(0x70, 0x80, 0x90), settings.customGhostTextMatchedBackgroundColor)
    }

    @Test
    fun `clears invalid custom line hint inserted color from loaded state`() {
        val settings = FreddieSettings()

        settings.loadState(FreddieSettings.State(customLineHintInsertedColor = "not a color"))

        assertEquals("", settings.state.customLineHintInsertedColor)
        assertEquals(null, settings.customLineHintInsertedColor)
    }

    @Test
    fun `clears invalid custom suggestion colors from loaded state`() {
        val settings = FreddieSettings()

        settings.loadState(
            FreddieSettings.State(
                customLineHintMatchedColor = "not a color",
                customGhostTextInsertedBackgroundColor = "also bad",
                customGhostTextMatchedBackgroundColor = "#12345",
            ),
        )

        assertEquals("", settings.state.customLineHintMatchedColor)
        assertEquals("", settings.state.customGhostTextInsertedBackgroundColor)
        assertEquals("", settings.state.customGhostTextMatchedBackgroundColor)
        assertEquals(null, settings.customLineHintMatchedColor)
        assertEquals(null, settings.customGhostTextInsertedBackgroundColor)
        assertEquals(null, settings.customGhostTextMatchedBackgroundColor)
    }
}
