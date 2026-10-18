package com.flivoro.tile8auncher.ui.components

import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLaunchMotionTest {
    @Test fun nativePhoneAdaptersEndHiddenAndHandOffTheApplication() {
        listOf(MasterLaunchAnimation.PHONE_8, MasterLaunchAnimation.MOBILE_10).forEach { style ->
            val start = SharedLaunchMotion.frame(style, 0f)
            val end = SharedLaunchMotion.frame(style, 1f)
            assertEquals(style.name, 1f, start.tileAlpha, .001f)
            assertEquals(style.name, 0f, start.appAlpha, .001f)
            assertEquals(style.name, 0f, end.tileAlpha, .001f)
            assertEquals(style.name, 1f, end.appAlpha, .001f)
            assertTrue(style.name, SharedLaunchMotion.duration(style) in 200..650)
        }
    }

    @Test fun sampledPhoneGeometryIsFiniteAtTenMillisecondIntervals() {
        listOf(MasterLaunchAnimation.PHONE_8, MasterLaunchAnimation.MOBILE_10).forEach { style ->
            val duration = SharedLaunchMotion.duration(style)
            for (ms in 0..duration step 10) {
                val f = SharedLaunchMotion.frame(style, ms.toFloat() / duration)
                assertTrue(style.name, f.tileAlpha in 0f..1f)
                assertTrue(style.name, f.appAlpha in 0f..1f)
                assertTrue(style.name, f.tileRotationY.isFinite())
                assertTrue(style.name, f.tileTranslationX.isFinite())
                assertTrue(style.name, f.tileTranslationY.isFinite())
                assertTrue(style.name, f.tileScale.isFinite() && f.tileScale > 0f)
                assertTrue(style.name, f.tilePivotX.isFinite())
            }
        }
    }
}
