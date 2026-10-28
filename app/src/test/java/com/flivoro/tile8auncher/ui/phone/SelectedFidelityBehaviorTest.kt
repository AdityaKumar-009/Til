package com.flivoro.tile8auncher.ui.phone

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.flivoro.tile8auncher.data.AppInfo
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SelectedFidelityBehaviorTest {
    @Test fun mobileMenuUsesWindowInsetsAndMeasuredSize() {
        val viewport = IntRect(24, 80, 1056, 2000)
        assertEquals(IntOffset(60, 300), placeMobileAppMenu(IntRect(60, 180, 900, 300), viewport, IntSize(648, 795)))
        assertEquals(IntOffset(408, 1045), placeMobileAppMenu(IntRect(900, 1840, 1040, 1960), viewport, IntSize(648, 795)))
        assertEquals(IntOffset(24, 80), placeMobileAppMenu(IntRect(60, 180, 900, 300), viewport, IntSize(2000, 2400)))
    }

    @Test fun expandedFolderCannotCutThroughTallNeighbor() {
        val slots = listOf(PhoneTileSlot(0, 0, 2, 2), PhoneTileSlot(2, 0, 4, 4), PhoneTileSlot(0, 2, 2, 4))
        assertEquals(6, phoneFolderInsertionRow(slots, 0))
        assertTrue(slots.none { it.row < 6 && it.row + it.rows > 6 })
        assertEquals(0, phoneFolderInsertionRow(slots, -1))
    }

    @Test fun localizedDefaultAppsUseRolesWithoutEnglishLabels() {
        val roles = PhoneAppRoles("dialer.pkg", "sms.pkg")
        assertEquals("wp81_phone", roles.glyph("dialer.pkg", "फ़ोन"))
        assertEquals("wp81_messages", roles.glyph("sms.pkg", "رسائل"))
        assertNull(roles.glyph("unrelated.pkg", "Phone"))
        assertEquals("wp81_phone", PhoneAppRoles().glyph("old.pkg", "Phone"))
    }

    @Test fun everyAlphabetGroupRemainsReachableAndSortingIsStable() {
        assertEquals("E", phoneAlphabetGroup("  Éclair"))
        assertEquals("#", phoneAlphabetGroup("٣ Notes"))
        assertEquals("&", phoneAlphabetGroup("東京"))
        assertEquals("&", phoneAlphabetGroup("😀工具"))
        val apps = listOf(AppInfo("Éclair", "b", "B"), AppInfo("eclair", "a", "A"),
            AppInfo("東京", "c", "C"), AppInfo("3 Notes", "d", "D"))
        assertEquals(listOf("d", "a", "b", "c"), phoneAppsInDisplayOrder(apps, Locale.US).map { it.packageName })
    }
}
