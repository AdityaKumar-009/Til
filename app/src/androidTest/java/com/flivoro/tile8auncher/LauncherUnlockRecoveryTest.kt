package com.flivoro.tile8auncher

import android.content.BroadcastReceiver
import android.content.Intent
import android.graphics.Bitmap
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.lockscreen.WindowsLockScreenRuntime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the actual Activity/root alpha gate, not just a standalone animation fixture. */
@RunWith(AndroidJUnit4::class)
class LauncherUnlockRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun ready(): Boolean {
        var result = false
        instrumentation.runOnMainSync {
            result = MainActivity::class.java.getDeclaredMethod("getEntranceReady")
                .apply { isAccessible = true }.invoke(compose.activity) as Boolean
        }
        return result
    }

    private fun entranceCount(): Int {
        var result = 0
        instrumentation.runOnMainSync {
            result = MainActivity::class.java.getDeclaredMethod("getEntranceRequest")
                .apply { isAccessible = true }.invoke(compose.activity) as Int
        }
        return result
    }

    private fun signal(action: String) {
        compose.runOnUiThread {
            // Android protects these broadcasts. Invoke our registered receiver directly
            // to reproduce OEM delivery order without pretending to send a system intent.
            val receiver = MainActivity::class.java.getDeclaredField("userPresentReceiver")
                .apply { isAccessible = true }.get(compose.activity) as BroadcastReceiver
            receiver.onReceive(compose.activity, Intent(action))
        }
    }

    private fun allModes(block: (LauncherUiMode) -> Unit) {
        val context = instrumentation.targetContext
        val previous = LauncherFeatureStore.launcherUiMode(context)
        try {
            for (mode in LauncherUiMode.entries) {
                compose.mainClock.autoAdvance = true
                compose.runOnUiThread { LauncherFeatureStore.setLauncherUiMode(context, mode) }
                compose.waitUntil(20_000) {
                    val repository = (compose.activity.application as Tile8Application).appsRepository
                    repository.peekCategorizedApps() != null && repository.cachedPinnedTiles != null &&
                        ready() && if (mode == LauncherUiMode.DESKTOP) {
                            compose.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
                        } else {
                            compose.onAllNodesWithTag("phone-navigation-state")
                                .fetchSemanticsNodes().isNotEmpty()
                        }
                }
                compose.mainClock.autoAdvance = false
                compose.mainClock.advanceTimeBy(2_500)
                compose.waitForIdle()
                block(mode)
            }
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            instrumentation.runOnMainSync {
                WindowsLockScreenRuntime.dismiss()
                LauncherFeatureStore.setLauncherUiMode(context, previous)
                LauncherFeatureStore.confirmPhoneRender(context)
            }
        }
    }

    private fun assertVisible(mode: LauncherUiMode, name: String) {
        compose.waitUntil(5_000) { ready() }
        compose.runOnUiThread { WindowsLockScreenRuntime.dismiss() }
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        assertTrue("$mode remained behind the entrance gate ($name)", ready())
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val colors = mutableSetOf<Int>()
        for (y in bitmap.height / 8 until bitmap.height * 7 / 8 step 8) {
            for (x in bitmap.width / 8 until bitmap.width * 7 / 8 step 8) {
                colors += bitmap.getPixel(x, y)
            }
        }
        assertTrue("$mode rendered a flat/black surface ($name)", colors.size > 24)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null),
            "motion-frames/unlock-recovery").apply { mkdirs() }
        File(directory, "cross-unlock-$mode-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun lateScreenOffWithoutUserPresentCannotLeaveAnyModeBlank() = allModes { mode ->
        // Last resume/USER_PRESENT has already happened; no further lifecycle event follows.
        signal(Intent.ACTION_SCREEN_OFF)
        assertVisible(mode, "late-screen-off")
    }

    @Test fun windowFocusRecoversAMissedWakeWithoutReplayingSettledHome() = allModes { mode ->
        compose.runOnUiThread {
            // Reproduce a previously hidden surface with all wake broadcasts dropped.
            MainActivity::class.java.getDeclaredField("startupEntrancePending")
                .apply { isAccessible = true }.setBoolean(compose.activity, true)
            MainActivity::class.java.getDeclaredMethod("setEntranceReady", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(compose.activity, false)
            compose.activity.onWindowFocusChanged(true)
        }
        assertVisible(mode, "focus-recovery")
        val count = entranceCount()
        repeat(3) {
            signal(Intent.ACTION_SCREEN_ON)
            signal(Intent.ACTION_USER_PRESENT)
            compose.runOnUiThread { compose.activity.onWindowFocusChanged(true) }
        }
        assertEquals("Duplicate wake signals replayed $mode", count, entranceCount())
    }

    @Test fun pausedAndRecreatedHomeRecoversTheSamePreparedWorkspace() = allModes { mode ->
        val repository = (compose.activity.application as Tile8Application).appsRepository
        val pins = repository.cachedPinnedTiles
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        signal(Intent.ACTION_SCREEN_OFF)
        assertFalse("Paused HOME was revealed", ready())
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertVisible(mode, "recreated")
        assertSame(repository, (compose.activity.application as Tile8Application).appsRepository)
        assertEquals(pins, repository.cachedPinnedTiles)
    }

    @Test fun interruptedClassicResumeCannotKeepItsBlackDelaySurface() {
        allModes { mode ->
            if (mode == LauncherUiMode.PHONE_8) {
                compose.runOnUiThread {
                    MainActivity::class.java.getDeclaredMethod("scheduleClassicPhoneResume")
                        .apply { isAccessible = true }.invoke(compose.activity)
                }
                assertFalse(ready())
                signal(Intent.ACTION_SCREEN_OFF)
                SystemClock.sleep(300) // Let the obsolete 200ms return callback run.
                assertVisible(mode, "interrupted-return")
            }
        }
    }

    @Test fun actualDisplaySleepAndWakeRestoresAllThreeModesRepeatedly() = allModes { mode ->
        repeat(2) { cycle ->
            shell("input keyevent 223") // KEYCODE_SLEEP, idempotent (unlike POWER).
            // Input injection completes before asynchronous power/broadcast delivery.
            // Waiting a fixed 400ms races SCREEN_OFF on software-rendered emulators.
            val power = instrumentation.targetContext.getSystemService(PowerManager::class.java)
            compose.waitUntil(10_000) { !power.isInteractive && !ready() }
            assertFalse("$mode did not hide while asleep", ready())
            shell("input keyevent 224") // KEYCODE_WAKEUP.
            shell("wm dismiss-keyguard")
            assertVisible(mode, "wake-$cycle")
        }
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
}
