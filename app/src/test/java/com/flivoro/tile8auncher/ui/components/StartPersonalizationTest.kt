package com.flivoro.tile8auncher.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPersonalizationTest {
    @Test
    fun windowsDefaultsRemainPresentInThePersonalizePalette() {
        assertTrue(Color(StartPersonalization.DEFAULT_BACKGROUND_ARGB) in StartPersonalization.backgroundChoices)
        assertTrue(Color(StartPersonalization.DEFAULT_ACCENT_ARGB) in StartPersonalization.accentChoices)
        assertEquals(20, StartPersonalization.backgroundChoices.size)
        assertEquals(20, StartPersonalization.accentChoices.size)
    }

    @Test
    fun defaultAccentKeepsTheExistingLauncherHighlightExactly() {
        assertEquals(
            Color(StartPersonalization.DEFAULT_HIGHLIGHT_ARGB),
            StartPersonalization.highlightFor(Color(StartPersonalization.DEFAULT_ACCENT_ARGB)),
        )
    }

    @Test
    fun customAccentProducesOpaqueBoundedHighlight() {
        val highlight = StartPersonalization.highlightFor(Color(0xFF0078D7L))

        assertEquals(1f, highlight.alpha, 0f)
        assertTrue(highlight.red in 0f..1f)
        assertTrue(highlight.green in 0f..1f)
        assertTrue(highlight.blue in 0f..1f)
    }
}
