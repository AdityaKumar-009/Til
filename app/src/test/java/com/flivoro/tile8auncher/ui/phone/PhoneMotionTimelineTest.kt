package com.flivoro.tile8auncher.ui.phone

import com.flivoro.tile8auncher.features.LauncherUiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneMotionTimelineTest {
    private val phone = LauncherUiMode.PHONE_8

    @Test fun windowsPhoneToolkitDefinesExactDirectionalEndPoints() {
        val expected = listOf(
            Triple(PhoneMotionPhase.FORWARD_IN, -80f, 0f),
            Triple(PhoneMotionPhase.FORWARD_OUT, 0f, 50f),
            Triple(PhoneMotionPhase.BACKWARD_IN, 50f, 0f),
            Triple(PhoneMotionPhase.BACKWARD_OUT, 0f, -80f),
        )
        expected.forEach { (phase, from, to) ->
            val start = PhoneMotionTimeline.sample(phone, phase, 0, 0)
            val end = PhoneMotionTimeline.sample(phone, phase,
                PhoneMotionTimeline.durationMillis(phone, phase), 0)
            assertEquals(from, start.rotationY, .001f)
            assertEquals(to, end.rotationY, .001f)
            assertEquals(-.2f, end.pivotX, .001f)
        }
    }

    @Test fun originalFeatherDelaysDifferByDirection() {
        assertEquals(120, PhoneMotionTimeline.delayMillis(phone, PhoneMotionPhase.FORWARD_IN, 3))
        assertEquals(150, PhoneMotionTimeline.delayMillis(phone, PhoneMotionPhase.FORWARD_OUT, 3))
        assertEquals(150, PhoneMotionTimeline.delayMillis(phone, PhoneMotionPhase.BACKWARD_IN, 3))
        assertEquals(120, PhoneMotionTimeline.delayMillis(phone, PhoneMotionPhase.BACKWARD_OUT, 3))
    }

    @Test fun everyTenMillisecondSampleRemainsStableAndBounded() {
        for (phase in PhoneMotionPhase.entries) {
            var previous = PhoneMotionTimeline.sample(phone, phase, 0, 2).rotationY
            val end = PhoneMotionTimeline.totalMillis(phone, phase, 2)
            for (ms in 10..(end + 10) step 10) {
                val frame = PhoneMotionTimeline.sample(phone, phase, ms, 2)
                assertTrue(frame.rotationY.isFinite())
                assertTrue(frame.alpha in 0f..1f)
                assertTrue(frame.rotationY in -80.001f..50.001f)
                if (phase == PhoneMotionPhase.FORWARD_IN || phase == PhoneMotionPhase.BACKWARD_OUT) {
                    assertTrue(frame.rotationY >= previous - .001f || phase == PhoneMotionPhase.BACKWARD_OUT)
                }
                previous = frame.rotationY
            }
        }
        assertEquals(0f, PhoneMotionTimeline.exponentialEaseOut6(0f), .0001f)
        assertEquals(1f, PhoneMotionTimeline.exponentialEaseOut6(1f), .0001f)
        assertEquals(7f / 63f, PhoneMotionTimeline.exponentialEaseIn6(.5f), .0001f)
    }

    @Test fun opacitySnapsOnElementStartInsteadOfCrossFading() {
        val delay = PhoneMotionTimeline.delayMillis(phone, PhoneMotionPhase.FORWARD_IN, 2)
        assertEquals(0f, PhoneMotionTimeline.sample(phone, PhoneMotionPhase.FORWARD_IN, delay - 1, 2).alpha, 0f)
        assertEquals(1f, PhoneMotionTimeline.sample(phone, PhoneMotionPhase.FORWARD_IN, delay, 2).alpha, 0f)
        val exitEnd = PhoneMotionTimeline.totalMillis(phone, PhoneMotionPhase.FORWARD_OUT, 2)
        assertEquals(1f, PhoneMotionTimeline.sample(phone, PhoneMotionPhase.FORWARD_OUT, exitEnd - 1, 2).alpha, 0f)
        assertEquals(0f, PhoneMotionTimeline.sample(phone, PhoneMotionPhase.FORWARD_OUT, exitEnd, 2).alpha, 0f)
    }

    @Test fun mobileProfileNeverUsesClassicTurnstileRotation() {
        for (ms in 0..420 step 10) {
            val f = PhoneMotionTimeline.sample(LauncherUiMode.MOBILE_10,
                PhoneMotionPhase.FORWARD_OUT, ms, 3, selectedTile = true)
            assertEquals(0f, f.rotationY, .00001f)
            assertTrue(f.alpha in 0f..1f)
            assertTrue(f.scale >= 1f)
        }
    }
}
