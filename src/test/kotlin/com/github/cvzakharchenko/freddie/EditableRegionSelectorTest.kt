package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.context.EditableRegionSelector
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditableRegionSelectorTest : BasePlatformTestCase() {
    fun testClampsAtFileStart() {
        myFixture.configureByText(
            "ClampStart.kt",
            """
            line0
            line1
            line2
            """.trimIndent(),
        )

        val document = myFixture.editor.document
        val region = EditableRegionSelector.select(document, document.getLineStartOffset(0))

        assertEquals(0, region.startLine)
        assertEquals(2, region.endLine)
        assertEquals(document.text, region.originalText)
    }

    fun testSelectsLineAlignedRegionAroundCaret() {
        val text = (0..30).joinToString("\n") { "line$it" }
        myFixture.configureByText("Middle.kt", text)

        val document = myFixture.editor.document
        val caretOffset = document.getLineStartOffset(20) + "line".length
        val region = EditableRegionSelector.select(document, caretOffset)

        assertEquals(15, region.startLine)
        assertEquals(30, region.endLine)
        assertTrue(region.originalText.startsWith("line15"))
        assertTrue(region.beforeCursor.endsWith("line"))
        assertTrue(region.afterCursor.startsWith("20"))
    }
}
