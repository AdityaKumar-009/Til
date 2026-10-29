package com.flivoro.tile8auncher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.*
import com.flivoro.tile8auncher.ui.phone.PhoneLauncherSurface
import com.flivoro.tile8auncher.ui.phone.PhoneLayoutStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val fixtureContext get() = instrumentation.context
    private val manager get() = AppWidgetManager.getInstance(context)
    private val ids = mutableListOf<Int>()

    @Before fun prepare() {
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        instrumentation.uiAutomation.executeShellCommand(
            "appwidget grantbind --package ${context.packageName} --user 0").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync {
            ids.forEach { LauncherWidgetHost.get(context).deleteAppWidgetId(it) }
        }
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun provider(tall: Boolean = false): AppWidgetProviderInfo =
        manager.installedProviders.first { it.provider.packageName == fixtureContext.packageName &&
            it.provider.className.endsWith(if (tall) "FixtureTallWidgetProvider" else "FixtureSmallWidgetProvider") }

    private fun allocate(tall: Boolean = false): Int {
        var result = -1
        instrumentation.runOnMainSync {
            val info = provider(tall)
            result = LauncherWidgetHost.get(context).allocateAppWidgetId()
            ids += result
            assertTrue("Test host must be allowed to bind", manager.bindAppWidgetIdIfAllowed(result, info.provider))
            manager.updateAppWidget(result, FixtureWidgetProvider.content(fixtureContext, result, context))
        }
        return result
    }

    @Test fun previewUsesDpDimensionsAndTapsShowFeedbackWithoutNativeInterception() {
        val info = provider()
        var selected by mutableStateOf(false)
        var taps = 0
        compose.setContent { MaterialTheme {
            Box(Modifier.size(300.dp, 240.dp)) {
                WidgetChoiceCard(WidgetProviderChoice(info, "Small clock", "Fixture"),
                    selected = selected, enabled = !selected) { selected = true; taps++ }
            }
        } }
        val preview = compose.onNodeWithTag("widget-preview-${info.provider.flattenToString()}", true)
        preview.assertWidthIsEqualTo(60.dp).assertHeightIsEqualTo(60.dp)
        // Real pointer input reaches Compose through an AndroidView, not performClick semantics.
        preview.performTouchInput { click() }
        compose.onNodeWithText("Adding…", true).assertIsDisplayed()
        preview.performTouchInput { click() }
        compose.runOnIdle { assertEquals("Pending selection cannot bind twice", 1, taps) }
        compose.captureFidelity("widget-picker-feedback")
    }

    @Test fun tallWidgetFitsCompletelyReportsItsSizeAndStillHandlesItsOwnButtons() {
        val id = allocate(tall = true)
        var height by mutableStateOf(160.dp)
        var taps = 0
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { taps++ }
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver,
            IntentFilter(FixtureWidgetProvider.ACTION_TAP), Context.RECEIVER_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver, IntentFilter(FixtureWidgetProvider.ACTION_TAP))
        try {
            compose.setContent { MaterialTheme { HostedWidgetTile(listOf(id), Modifier.size(200.dp, height)) } }
            compose.onNodeWithTag("hosted-widget-$id").assertIsDisplayed()
            compose.waitForIdle()
            compose.runOnIdle {
                val viewport = WindowInspector.getGlobalWindowViews().firstNotNullOf { root ->
                    root.findViewWithTag<ViewGroup>("widget-host-$id") }
                val hosted = viewport.getChildAt(0)
                assertTrue("Native content must retain the provider's minimum height",
                    hosted.height >= provider(true).minHeight)
                assertTrue(hosted.height * hosted.scaleY <= viewport.height + 1f)
                assertTrue(hosted.width * hosted.scaleX <= viewport.width + 1f)
                val options = manager.getAppWidgetOptions(id)
                val expected = (hosted.height - hosted.paddingTop - hosted.paddingBottom) /
                    context.resources.displayMetrics.density
                assertTrue(kotlin.math.abs(expected.toInt() -
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)) <= 2)
            }
            var buttonCenter = Offset.Zero
            compose.runOnIdle {
                val viewport = WindowInspector.getGlobalWindowViews().firstNotNullOf { root ->
                    root.findViewWithTag<ViewGroup>("widget-host-$id") }
                val button = viewport.findViewById<View>(fixtureContext.resources.getIdentifier(
                    "widget_test_button", "id", fixtureContext.packageName))
                val visibleButton = android.graphics.Rect()
                assertTrue(button.getGlobalVisibleRect(visibleButton))
                val origin = IntArray(2)
                viewport.getLocationOnScreen(origin)
                buttonCenter = Offset(visibleButton.exactCenterX() - origin[0],
                    visibleButton.exactCenterY() - origin[1])
                assertTrue(buttonCenter.x in 0f..viewport.width.toFloat())
                assertTrue(buttonCenter.y in 0f..viewport.height.toFloat())
            }
            // Espresso's 90% visibility matcher compares unscaled local dimensions
            // with the scaled global rectangle. Send a real tap at the rendered
            // button instead, and still require its actual PendingIntent to fire.
            compose.onNodeWithTag("hosted-widget-$id").performTouchInput { click(buttonCenter) }
            compose.waitUntil(5_000) { taps == 1 }
            compose.runOnIdle { height = 280.dp }
            compose.waitForIdle()
            compose.runOnIdle {
                assertTrue(manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) >= 240)
            }
            compose.captureFidelity("widget-tall-complete")
        } finally { context.unregisterReceiver(receiver) }
    }

    @Test fun widgetStackChangesHostIdentityAndContentTogether() {
        val first = allocate()
        val second = allocate(tall = true)
        compose.setContent { MaterialTheme { HostedWidgetTile(listOf(first, second), Modifier.size(280.dp, 180.dp)) } }
        compose.onNodeWithTag("hosted-widget-$first").assertIsDisplayed()
        compose.onNodeWithText("1/2").performClick()
        compose.onNodeWithTag("hosted-widget-$second").assertIsDisplayed()
        compose.onNodeWithTag("hosted-widget-$first").assertDoesNotExist()
        compose.onNodeWithText("2/2").performClick()
        compose.onNodeWithTag("hosted-widget-$first").assertIsDisplayed()
    }

    @Test fun bothPhoneModesMountRealWidgetsAndKeepTheLargeWidgetFootprint() {
        val id = allocate(tall = true)
        val tile = TileModel("widget-test", "Calendar widget", size = TileSize.LARGE)
        LauncherFeatureStore.setWidgetStackIds(context, tile.id, listOf(id))
        var mode by mutableStateOf(LauncherUiMode.PHONE_8)
        compose.setContent { MaterialTheme {
            PhoneLauncherSurface(mode, listOf(tile), emptyList(), AppsRepository(context),
                homeRequest = 0, entranceRequest = 0, launchingTileId = null,
                isLaunching = false, interactionEnabled = true, wallpaperStyle = 0,
                onLaunch = { _, _, _ -> fail("A widget button must not launch its tile") },
                onExitFinished = {}, onOpenSettings = {}, onOpenAppInfo = {})
        } }
        for (style in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
            compose.runOnIdle { mode = style }
            compose.onNodeWithTag("hosted-widget-$id", true).assertIsDisplayed()
            compose.runOnIdle { assertEquals(TileSize.LARGE, PhoneLayoutStore.size(context, style, tile)) }
            compose.onNodeWithContentDescription("Edit widget", true).assertIsDisplayed()
            compose.captureFidelity("widget-${style.name}")
        }
    }
}
