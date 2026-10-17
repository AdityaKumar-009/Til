package com.flivoro.tile8auncher

import android.os.Process
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
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
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherModeSwitchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityRecreationReusesPreparedStateAndRootBackKeepsDesktopAlive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        val repository = (compose.activity.application as Tile8Application).appsRepository
        try {
            compose.runOnUiThread { LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.DESKTOP) }
            compose.waitUntil(timeoutMillis = 20_000) {
                repository.peekCategorizedApps() != null && repository.cachedPinnedTiles != null &&
                    context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, 0)
                        .getBoolean("shared_start_workspace_v1", false)
            }
            val catalog = repository.refreshCatalogIfNeeded(PackageCatalogUpdates.revision)
            val pins = repository.cachedPinnedTiles
            compose.waitForIdle()
            repeat(3) {
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            }
            assertFalse("Root Back destroyed Desktop", compose.activity.isFinishing)
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertSame(repository, (compose.activity.application as Tile8Application).appsRepository)
            assertSame("Recreation rescanned installed apps", catalog, repository.peekCategorizedApps())
            assertEquals(pins?.map { it.id }, repository.cachedPinnedTiles?.map { it.id })
            compose.onNodeWithText("Loading Start").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { LauncherFeatureStore.setLauncherUiMode(context, previousMode) }
        }
    }

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
            fun awaitUi(description: String, condition: () -> Boolean) {
                try {
                    compose.waitUntil(timeoutMillis = 20_000) { condition() }
                } catch (failure: Throwable) {
                    throw AssertionError(
                        "Timed out waiting for $description. UI tree:\n" +
                            compose.onRoot().printToString(),
                        failure,
                    )
                }
            }

            compose.runOnUiThread {
                LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.DESKTOP)
                LauncherFeatureStore.confirmPhoneRender(context)
                LauncherFeatureStore.confirmPhoneSettings(context)
                LauncherFeatureStore.setLauncherUiMode(
                    context,
                    LauncherUiMode.PHONE_8,
                    phoneRenderAttempted = true,
                )
            }
            awaitUi("WP8.1 Start screen") {
                compose.onAllNodesWithTag("phone-navigation-state")
                    .fetchSemanticsNodes().isNotEmpty()
            }

            fun openPhoneSettings(mode: LauncherUiMode) {
                awaitUi("$mode Action Center trigger") {
                    compose.onAllNodesWithTag("phone-action-center-trigger")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("phone-action-center-trigger").performClick()
                compose.onNodeWithText("All settings").performClick()
                awaitUi("$mode Mosaic Settings content") {
                    compose.onAllNodesWithTag("til-advanced-settings-content")
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }

            fun selectMode(mode: LauncherUiMode) {
                compose.onNodeWithText("Windows interface").performScrollTo().performClick()
                compose.onNodeWithTag("launcher-mode-${mode.name}").performClick()
                assertEquals(mode, LauncherFeatureStore.launcherUiMode(context))
                assertFalse("Activity finished while selecting $mode", compose.activity.isFinishing)
                compose.onNodeWithText("✕").performClick()
                awaitUi("$mode Start screen after closing Settings") {
                    compose.onAllNodesWithTag("phone-navigation-state")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitForIdle()
                assertFalse("Activity finished while rendering $mode", compose.activity.isFinishing)
            }

            openPhoneSettings(LauncherUiMode.PHONE_8)
            selectMode(LauncherUiMode.MOBILE_10)
            openPhoneSettings(LauncherUiMode.MOBILE_10)
            selectMode(LauncherUiMode.PHONE_8)
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
                    compose.onAllNodesWithTag("phone-navigation-state")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("phone-action-center-trigger").performClick()
                compose.onNodeWithText("All settings").performClick()
                compose.onNodeWithTag("til-advanced-settings-content").assertExists()
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
                    compose.onAllNodesWithTag("phone-navigation-state")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitForIdle()
                fun transitionCount(): String = compose.onNodeWithTag("phone-navigation-state")
                    .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                val before = transitionCount()

                // Android owns Home/Back. At root, dispatching system Back repeatedly
                // must neither finish the launcher nor restart its entrance.
                repeat(3) {
                    compose.runOnUiThread {
                        compose.activity.onBackPressedDispatcher.onBackPressed()
                    }
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
