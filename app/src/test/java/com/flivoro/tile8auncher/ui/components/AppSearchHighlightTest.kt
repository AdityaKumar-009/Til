package com.flivoro.tile8auncher.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchHighlightTest {
    private val accent = Color(0xFF0078D7)

    @Test fun colorsTheMatchCaseInsensitivelyWithoutChangingText() {
        val result = highlightedAppLabel("Microsoft Photos", "PHO", accent)
        assertEquals("Microsoft Photos", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(10, result.spanStyles.single().start)
        assertEquals(13, result.spanStyles.single().end)
        assertEquals(accent, result.spanStyles.single().item.color)
    }

    @Test fun blankOrMissingQueryDoesNotChangeLabel() {
        assertEquals(0, highlightedAppLabel("Messages", "", accent).spanStyles.size)
        assertEquals(0, highlightedAppLabel("Messages", "camera", accent).spanStyles.size)
    }

    @Test fun ignoresSearchFieldSurroundingWhitespace() {
        val result = highlightedAppLabel("Camera", "  AME ", accent)
        assertEquals("Camera", result.text)
        assertEquals(1, result.spanStyles.single().start)
        assertEquals(4, result.spanStyles.single().end)
    }

    @Test fun keepsOriginalAccentsAndUnicodeText() {
        val result = highlightedAppLabel("Café Music", "CAF", accent)
        assertEquals("Café Music", result.text)
        assertTrue(result.spanStyles.single().item.color == accent)
    }
}
