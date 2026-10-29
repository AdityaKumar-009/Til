package com.flivoro.tile8auncher

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherWidgetHost
import com.flivoro.tile8auncher.features.WidgetPickerActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPlacementTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var originalIds = emptySet<Int>()

    @Before fun prepare() {
        AppsRepository(context).savePinnedTiles(emptyList())
        originalIds = LauncherWidgetHost.get(context).appWidgetIds.toSet()
        instrumentation.uiAutomation.executeShellCommand(
            "appwidget grantbind --package ${context.packageName} --user 0").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
    }

    @After fun cleanup() {
        (LauncherWidgetHost.get(context).appWidgetIds.toSet() - originalIds).forEach {
            LauncherWidgetHost.get(context).deleteAppWidgetId(it)
        }
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun addFixture(tall: Boolean, size: TileSize) {
        val fixture = instrumentation.context.packageName
        val provider = AppWidgetManager.getInstance(context).installedProviders.first {
            it.provider.packageName == fixture && it.provider.className.endsWith(
                if (tall) "FixtureTallWidgetProvider" else "FixtureSmallWidgetProvider") }
        val intent = WidgetPickerActivity.forApp(context, fixture).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<WidgetPickerActivity>(intent).use {
            compose.onNodeWithTag("app-widget-bottom-sheet").assertIsDisplayed()
            compose.captureFidelity("widget-app-sheet", tag = "app-widget-bottom-sheet")
            compose.onNodeWithTag("widget-choice-${provider.provider.flattenToString()}")
                .performScrollTo().performClick()
            compose.waitUntil(10_000) { AppsRepository(context).loadPinnedTiles().size == 1 }
            val tile = AppsRepository(context).loadPinnedTiles().single()
            assertEquals(size, tile.size)
            val id = LauncherFeatureStore.widgetStackIds(context, tile.id).single()
            assertEquals(provider.provider, AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider)
            assertEquals(1, (LauncherWidgetHost.get(context).appWidgetIds.toSet() - originalIds).size)
        }
    }

    @Test fun smallWidgetBindsAndPersistsWithAnAppropriateFootprint() = addFixture(false, TileSize.MEDIUM)
    @Test fun tallWidgetBindsAndPersistsWithoutBeingForcedIntoAWideTile() = addFixture(true, TileSize.LARGE)
}
