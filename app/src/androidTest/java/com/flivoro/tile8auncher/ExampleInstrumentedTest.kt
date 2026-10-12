package com.flivoro.tile8auncher

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun launcherStartsWithPersistedWp81Mode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        var activity: MainActivity? = null
        try {
            LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.PHONE_8)
            val launchIntent = Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
            activity = instrumentation.startActivitySync(launchIntent) as MainActivity
            instrumentation.waitForIdleSync()
            assertEquals(LauncherUiMode.PHONE_8, LauncherFeatureStore.launcherUiMode(activity))
            assertFalse("Launcher closed during WP8.1 startup", activity.isFinishing)
        } finally {
            activity?.finish()
            LauncherFeatureStore.setLauncherUiMode(context, previousMode)
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.flivoro.tile8auncher", appContext.packageName)
    }
}