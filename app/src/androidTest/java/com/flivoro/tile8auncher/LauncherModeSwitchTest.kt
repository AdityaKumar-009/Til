package com.flivoro.tile8auncher

import android.os.Process
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherModeSwitchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun switchingActiveLauncherToWp81DoesNotCrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        compose.mainClock.autoAdvance = false

        try {
            Thread.sleep(350)
            compose.runOnUiThread {
                LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.DESKTOP)
            }
            compose.mainClock.advanceTimeBy(1500)

            // This is the same store callback invoked by the Windows interface choice row. Keeping
            // the Activity in place covers the live root-surface replacement from the settings path.
            compose.runOnUiThread {
                LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.PHONE_8)
            }
            compose.mainClock.advanceTimeBy(1500)

            Thread.sleep(700)
            assertEquals(LauncherUiMode.PHONE_8, LauncherFeatureStore.launcherUiMode(context))
            compose.runOnUiThread {
                assertFalse("Launcher Activity finished after choosing WP8.1", compose.activity.isFinishing)
                assertTrue(
                    "Launcher window detached after choosing WP8.1",
                    compose.activity.window.decorView.isAttachedToWindow,
                )
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
            }
        }
    }

    @Test
    fun choosingWp81AndW10mFromSettingsReturnsToLivePhoneStart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        try {
            compose.runOnUiThread {
                LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.DESKTOP)
                LauncherFeatureStore.confirmPhoneRender(context)
                LauncherFeatureStore.confirmPhoneSettings(context)
            }
            compose.waitForIdle()

            // Follow the visible PC Settings path used by the mode picker, rather than
            // changing SharedPreferences behind the UI as the lower-level tests do.
            compose.waitUntil(timeoutMillis = 20_000) {
                compose.onAllNodesWithText("PC settings").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("PC settings").performClick()
            compose.waitUntil(timeoutMillis = 20_000) {
                compose.onAllNodesWithText("Open Android System Settings")
                    .fetchSemanticsNodes().isNotEmpty()
            }

            fun selectMode(mode: LauncherUiMode) {
                compose.onNodeWithText("Windows interface").performScrollTo().performClick()
                compose.onNodeWithTag("launcher-mode-${mode.name}").performClick()
                assertEquals(mode, LauncherFeatureStore.launcherUiMode(context))
                assertFalse("Activity finished while selecting $mode", compose.activity.isFinishing)
                compose.onNodeWithText("✕").performClick()
                compose.waitUntil(timeoutMillis = 20_000) {
                    compose.onAllNodesWithTag("phone-bottom-home")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                assertFalse("Activity finished while rendering $mode", compose.activity.isFinishing)
            }

            selectMode(LauncherUiMode.PHONE_8)

            compose.onNodeWithTag("phone-bottom-search").performClick()
            compose.onNodeWithTag("phone-app-search-toggle").performClick()
            compose.waitUntil(timeoutMillis = 20_000) {
                compose.onAllNodesWithTag("phone-launcher-settings")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("phone-launcher-settings").performClick()
            compose.waitUntil(timeoutMillis = 20_000) {
                compose.onAllNodesWithText("Open Android System Settings")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            selectMode(LauncherUiMode.MOBILE_10)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
                LauncherFeatureStore.confirmPhoneRender(context)
                LauncherFeatureStore.confirmPhoneSettings(context)
            }
        }
    }
    @Test
    fun openingSettingsInBothPhoneModesKeepsLauncherAlive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        try {
            for (mode in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
                compose.runOnUiThread {
                    LauncherFeatureStore.setLauncherUiMode(context, mode)
                }
                compose.waitUntil(timeoutMillis = 20_000) {
                    compose.onAllNodesWithTag("phone-bottom-search")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("phone-bottom-search").performClick()
                if (mode == LauncherUiMode.PHONE_8) {
                    compose.onNodeWithTag("phone-app-search-toggle").performClick()
                }
                compose.onNodeWithTag("phone-launcher-settings").performClick()
                compose.onNodeWithText("Open Android System Settings").assertExists()
                compose.onNodeWithText("✕").performClick()
                compose.waitForIdle()
                assertEquals(mode, LauncherFeatureStore.launcherUiMode(context))
                assertFalse(compose.activity.isFinishing)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
                LauncherFeatureStore.confirmPhoneRender(context)
                LauncherFeatureStore.confirmPhoneSettings(context)
            }
        }
    }

    @Test
    fun homeAndBackOnSettledPhoneStartAreIdempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        try {
            for (mode in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
                compose.runOnUiThread {
                    LauncherFeatureStore.setLauncherUiMode(context, mode)
                }
                compose.waitUntil(timeoutMillis = 20_000) {
                    compose.onAllNodesWithTag("phone-bottom-home")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitForIdle()
                fun transitionCount(): String = compose.onNodeWithTag("phone-navigation-state")
                    .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                val before = transitionCount()

                repeat(3) {
                    compose.onNodeWithTag("phone-bottom-home").performClick()
                    compose.onNodeWithTag("phone-bottom-back").performClick()
                }
                compose.waitForIdle()
                assertEquals("Repeated HOME/BACK replayed Start entrance in $mode",
                    before, transitionCount())
                assertFalse("Root BACK finished the Activity in $mode", compose.activity.isFinishing)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
                LauncherFeatureStore.confirmPhoneRender(context)
                LauncherFeatureStore.confirmPhoneSettings(context)
            }
        }
    }

    @Test
    fun fatalCrashAfterHealthyPhoneStartupRecoversDesktopAndStoresReport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        val previousColumns = LauncherFeatureStore.phoneSmallColumns(context, LauncherUiMode.PHONE_8)
        try {
            compose.runOnUiThread {
                LauncherFeatureStore.setPhoneSmallColumns(context, LauncherUiMode.PHONE_8, 6)
                LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.PHONE_8)
                LauncherFeatureStore.confirmPhoneRender(context)
            }
            assertTrue(LauncherFeatureStore.recoverFatalPhoneCrash(
                context, IllegalStateException("simulated late phone crash"),
            ))
            assertEquals(LauncherUiMode.DESKTOP, LauncherFeatureStore.launcherUiMode(context))
            assertEquals(6, LauncherFeatureStore.phoneSmallColumns(context, LauncherUiMode.PHONE_8))
            assertTrue(LauncherFeatureStore.lastPhoneCrash(context)
                ?.contains("simulated late phone crash") == true)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setPhoneSmallColumns(context, LauncherUiMode.PHONE_8, previousColumns)
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
                LauncherFeatureStore.confirmPhoneRender(context)
                LauncherFeatureStore.confirmPhoneSettings(context)
            }
        }
    }

    @Test
    fun interruptedPhoneRenderFallsBackWithoutLosingSavedModeSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        val previousColumns = LauncherFeatureStore.phoneSmallColumns(context, LauncherUiMode.PHONE_8)
        try {
            compose.runOnUiThread {
                LauncherFeatureStore.setPhoneSmallColumns(context, LauncherUiMode.PHONE_8, 6)
                LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.PHONE_8)
                // Model process death after an actual render attempt. A PID change separates
                // that crash from a normal Activity recreation in the same process.
                LauncherFeatureStore.beginPhoneRender(context, processId = Int.MAX_VALUE)
            }
            assertTrue(LauncherFeatureStore.recoverInterruptedPhoneRender(context))
            assertEquals(LauncherUiMode.DESKTOP, LauncherFeatureStore.launcherUiMode(context))
            assertEquals(6, LauncherFeatureStore.phoneSmallColumns(context, LauncherUiMode.PHONE_8))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setPhoneSmallColumns(context, LauncherUiMode.PHONE_8, previousColumns)
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
                LauncherFeatureStore.confirmPhoneRender(context)
                LauncherFeatureStore.confirmPhoneSettings(context)
            }
        }
    }

    @Test
    fun livePhoneModeSelectionIsSafeAcrossActivityRecreationAndProcessDeath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        try {
            LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.DESKTOP)
            for (mode in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
                LauncherFeatureStore.setLauncherUiMode(
                    context,
                    mode,
                    phoneRenderAttempted = true,
                )
                assertEquals(mode, LauncherFeatureStore.launcherUiMode(context))
                assertFalse(
                    "A same-process Activity recreation rolled back $mode",
                    LauncherFeatureStore.recoverInterruptedPhoneRender(
                        context,
                        currentProcessId = Process.myPid(),
                    ),
                )
                assertTrue(
                    "A dead $mode render process was not recovered",
                    LauncherFeatureStore.recoverInterruptedPhoneRender(
                        context,
                        currentProcessId = Int.MAX_VALUE,
                    ),
                )
                assertEquals(LauncherUiMode.DESKTOP, LauncherFeatureStore.launcherUiMode(context))
                assertTrue(
                    LauncherFeatureStore.lastPhoneCrash(context)
                        ?.contains(mode.title) == true,
                )
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
                LauncherFeatureStore.confirmPhoneRender(context)
                LauncherFeatureStore.confirmPhoneSettings(context)
            }
        }
    }
}
