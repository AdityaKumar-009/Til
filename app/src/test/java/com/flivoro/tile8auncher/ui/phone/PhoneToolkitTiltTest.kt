package com.flivoro.tile8auncher.ui.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneToolkitTiltTest {
    @Test fun centerPressPushesTileIntoScreenWithoutRotation() {
        val frame = PhoneToolkitTilt.at(50f, 100f, 100f, 200f)
        assertEquals(0f, frame.rotationX, .0001f)
        assertEquals(0f, frame.rotationY, .0001f)
        assertEquals(25f, frame.depth, .0001f)
    }

    @Test fun cornersFollowOriginalTwoAxisRotationAndZeroDepression() {
        val a = PhoneToolkitTilt.at(0f, 0f, 100f, 200f)
        val b = PhoneToolkitTilt.at(100f, 200f, 100f, 200f)
        assertTrue(a.rotationX < 0f && a.rotationY > 0f)
        assertTrue(b.rotationX > 0f && b.rotationY < 0f)
        assertEquals(0f, a.depth, .0001f)
        assertEquals(-a.rotationX, b.rotationX, .0001f)
        assertEquals(-a.rotationY, b.rotationY, .0001f)
    }

    @Test fun releaseTimingsMatchToolkit() {
        assertEquals(200, PhoneToolkitTilt.RELEASE_DELAY_MS)
        assertEquals(100, PhoneToolkitTilt.RELEASE_DURATION_MS)
    }

    @Test fun invalidDimensionsDoNotProduceNaN() {
        assertEquals(PhoneTiltFrame(0f, 0f, 0f), PhoneToolkitTilt.at(30f, 20f, 0f, 0f))
    }
}
