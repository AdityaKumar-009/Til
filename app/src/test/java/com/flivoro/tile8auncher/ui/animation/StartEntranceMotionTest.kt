package com.flivoro.tile8auncher.ui.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartEntranceMotionTest {
    @Test
    fun bothProfilesSettleExactlyWithoutOvershoot() {
        StartEntranceProfile.entries.forEach { profile ->
            val duration = StartEntranceMotion.durationForProfile(profile)
            for (column in 0..3) {
                var previous = StartEntranceMotion.frameForProfile(profile, 0f, column)
                for (ms in 10..duration step 10) {
                    val frame = StartEntranceMotion.frameForProfile(
                        profile = profile,
                        progress = ms.toFloat() / duration,
                        column = column,
                    )
                    assertTrue(frame.offsetFraction <= previous.offsetFraction + 0.00001f)
                    assertTrue(frame.scale >= previous.scale - 0.00001f && frame.scale <= 1f)
                    assertTrue(frame.alpha >= previous.alpha - 0.00001f && frame.alpha <= 1f)
                    previous = frame
                }
                val finalFrame = StartEntranceMotion.frameForProfile(profile, 1f, column)
                assertEquals(EntranceFrame(0f, 1f, 1f), finalFrame)
            }
        }
    }

    @Test
    fun signInIsLongerAndMorePronouncedThanNormalReturn() {
        assertTrue(
            StartEntranceMotion.durationForProfile(StartEntranceProfile.SIGN_IN) >
                StartEntranceMotion.durationForProfile(StartEntranceProfile.RETURN),
        )

        val normal = StartEntranceMotion.frameForProfile(StartEntranceProfile.RETURN, 0f, 0)
        val signIn = StartEntranceMotion.frameForProfile(StartEntranceProfile.SIGN_IN, 0f, 0)

        assertEquals(1f, normal.scale, 0f)
        assertTrue(signIn.scale < 1f)
        assertTrue(signIn.offsetFraction > normal.offsetFraction)
    }
}
