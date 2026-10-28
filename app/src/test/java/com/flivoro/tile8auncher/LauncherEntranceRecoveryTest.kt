package com.flivoro.tile8auncher

import org.junit.Assert.*
import org.junit.Test

class LauncherEntranceRecoveryTest {
    private class Device {
        var pending = true
        var resumed = false
        var interactive = true
        var locked = false
        var entrances = 0
        val callbacks = mutableListOf<Runnable>()
        val recovery = LauncherEntranceRecovery(
            isPending = { pending }, isResumed = { resumed },
            isInteractive = { interactive }, isKeyguardLocked = { locked },
            postDelayed = { task, delay ->
                assertEquals(100L, delay)
                callbacks += task
            },
            removeCallbacks = { task -> callbacks.removeAll { it === task } },
            onReady = { pending = false; entrances++ },
        )
        fun resume() { resumed = true; recovery.recheck() }
        fun pause() { resumed = false; recovery.cancel() }
        fun tick() { callbacks.removeAt(0).run() }
    }

    @Test fun coldUnlockedHomeIsReadyImmediately() {
        val device = Device()
        device.resume()
        assertEquals(1, device.entrances)
        assertFalse(device.pending)
        assertTrue(device.callbacks.isEmpty())
    }

    @Test fun resumedBehindKeyguardRecoversWithoutUserPresentOrAnotherResume() {
        val device = Device().apply { locked = true }
        device.resume()
        repeat(10) { device.tick(); assertEquals(0, device.entrances) }
        device.locked = false
        device.tick()
        assertEquals(1, device.entrances)
        assertTrue(device.callbacks.isEmpty())
    }

    @Test fun userPresentBeforeKeyguardClearsDoesNotRevealEarlyOrGetLost() {
        val device = Device().apply { locked = true }
        device.resume()
        device.recovery.recheck() // USER_PRESENT with stale KeyguardManager state.
        device.tick()
        assertEquals(0, device.entrances)
        device.locked = false
        device.tick()
        assertEquals(1, device.entrances)
    }

    @Test fun userPresentBeforeResumeIsRecoveredFromCurrentState() {
        val device = Device()
        device.recovery.recheck()
        assertTrue(device.callbacks.isEmpty())
        assertEquals(0, device.entrances)
        device.resume()
        assertEquals(1, device.entrances)
    }

    @Test fun screenOnWithoutSecureLockOrUserPresentReleasesTheGate() {
        val device = Device().apply { interactive = false }
        device.resume()
        assertTrue(device.callbacks.isEmpty())
        device.interactive = true
        device.recovery.recheck() // SCREEN_ON, no USER_PRESENT and no new resume.
        assertEquals(1, device.entrances)
    }

    @Test fun focusRecoversIfBothWakeBroadcastsWereMissed() {
        val device = Device().apply { interactive = false; locked = true }
        device.resume()
        device.interactive = true
        device.locked = false
        device.recovery.recheck() // Window focus only.
        assertEquals(1, device.entrances)
    }

    @Test fun delayedScreenOffAfterUnlockCannotStrandAnInteractiveHome() {
        val device = Device()
        device.resume()
        device.pending = true // Late SCREEN_OFF, after the last resume/USER_PRESENT.
        device.recovery.recheck()
        assertFalse(device.pending)
        assertEquals(2, device.entrances)
    }

    @Test fun duplicateWakeAndFocusEventsNeverReplayASettledEntrance() {
        val device = Device()
        device.resume()
        repeat(50) { device.recovery.recheck() }
        assertEquals(1, device.entrances)
        assertTrue(device.callbacks.isEmpty())
    }

    @Test fun repeatedLockedSignalsKeepOnlyOneRecoveryTimer() {
        val device = Device().apply { locked = true }
        device.resume()
        repeat(50) { device.recovery.recheck(); assertEquals(1, device.callbacks.size) }
        device.locked = false
        device.tick()
        assertEquals(1, device.entrances)
    }

    @Test fun leavingHomeCancelsRecoveryUntilTheNextResume() {
        val device = Device().apply { locked = true }
        device.resume()
        val staleCallback = device.callbacks.single()
        device.pause()
        device.locked = false
        staleCallback.run()
        assertTrue(device.callbacks.isEmpty())
        assertEquals(0, device.entrances)
        device.resume()
        assertEquals(1, device.entrances)
    }

    @Test fun lockingAgainDoesNotReleaseOrPollWhileAsleep() {
        val device = Device().apply { locked = true }
        device.resume()
        device.interactive = false
        device.recovery.recheck()
        assertEquals(0, device.entrances)
        assertTrue(device.callbacks.isEmpty())
        device.interactive = true
        device.recovery.recheck()
        assertEquals(0, device.entrances)
        device.locked = false
        device.tick()
        assertEquals(1, device.entrances)
    }

    @Test fun recreationCannotRevealOrRescheduleTheDestroyedWindow() {
        val old = Device().apply { locked = true }
        old.resume()
        val staleCallback = old.callbacks.single()
        old.recovery.dispose()
        old.locked = false
        staleCallback.run()
        old.recovery.recheck()
        assertEquals(0, old.entrances)
        assertTrue(old.callbacks.isEmpty())
        val replacement = Device()
        replacement.resume()
        assertEquals(1, replacement.entrances)
    }
}
