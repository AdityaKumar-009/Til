package com.flivoro.tile8auncher.ui.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicPhoneFolderMotionTest {
    @Test fun classicFolderHasItsOwnInterruptibleTimeline() {
        assertEquals(350, ClassicPhoneFolderMotion.remainingDuration(0f, true))
        assertEquals(350, ClassicPhoneFolderMotion.remainingDuration(1f, false))
        assertEquals(88, ClassicPhoneFolderMotion.remainingDuration(.25f, false))
        assertTrue(ClassicPhoneFolderMotion.OPEN_MS != PhoneFolderMotion.OPEN_MS)
        assertTrue(ClassicPhoneFolderMotion.CLOSE_MS != PhoneFolderMotion.CLOSE_MS)
    }

    @Test fun classicPreviewAndRulesFollowTheSameOpenCloseClock() {
        assertEquals(1f, ClassicPhoneFolderMotion.previewOpacity(0f), .001f)
        assertEquals(0f, ClassicPhoneFolderMotion.emptyGridOpacity(0f), .001f)
        assertEquals(0f, ClassicPhoneFolderMotion.previewOpacity(1f), .001f)
        assertEquals(1f, ClassicPhoneFolderMotion.emptyGridOpacity(1f), .001f)
        assertEquals(0f, ClassicPhoneFolderMotion.frame(0f).headerOpacity, .001f)
        assertEquals(1f, ClassicPhoneFolderMotion.frame(1f).headerOpacity, .001f)
        assertTrue(ClassicPhoneFolderMotion.frame(.3f).childrenOpacity <
            ClassicPhoneFolderMotion.frame(.7f).childrenOpacity)
    }
}
