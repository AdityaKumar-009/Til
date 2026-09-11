package com.flivoro.tile8auncher.features

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.ui.theme.WindowsTypography

object LauncherWidgetHost {
    const val HOST_ID = 0x4D4F53 // "MOS"

    @Volatile
    private var host: AppWidgetHost? = null

    fun get(context: Context): AppWidgetHost = synchronized(this) {
        host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }
    }
}

@Composable
fun HostedWidgetTile(
    widgetIds: List<Int>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val host = remember(context) { LauncherWidgetHost.get(context) }
    var activeIndex by remember(widgetIds) { mutableIntStateOf(0) }
    val validIds = remember(widgetIds) {
        widgetIds.filter { id -> runCatching { manager.getAppWidgetInfo(id) }.getOrNull() != null }
    }
    if (activeIndex !in validIds.indices) activeIndex = 0

    DisposableEffect(host) {
        runCatching { host.startListening() }
        onDispose { /* shared host intentionally keeps listening across visible widget tiles */ }
    }

    Box(modifier = modifier.background(Color(0xFF202020))) {
        val id = validIds.getOrNull(activeIndex)
        val info = id?.let(manager::getAppWidgetInfo)
        if (id != null && info != null) {
            AndroidView(
                factory = { host.createView(it, id, info).apply { setAppWidget(id, info) } },
                update = { view -> view.setAppWidget(id, info) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Widget", color = Color.White, style = WindowsTypography.titleMedium)
                Text(
                    "Needs rebind",
                    color = Color.White.copy(alpha = .72f),
                    style = WindowsTypography.labelSmall.copy(fontSize = 10.sp),
                )
            }
        }

        if (validIds.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color(0xAA000000))
                    .clickable { activeIndex = (activeIndex + 1) % validIds.size }
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${activeIndex + 1}/${validIds.size}",
                    color = Color.White,
                    style = WindowsTypography.labelSmall.copy(fontSize = 9.sp),
                )
            }
        }
    }
}

/**
 * Small dedicated picker activity keeps widget binding/configuration away from MainActivity.
 * That isolation is important because MainActivity owns the fitted Windows launch animations.
 */
class WidgetPickerActivity : ComponentActivity() {
    private lateinit var host: AppWidgetHost
    private lateinit var manager: AppWidgetManager
    private var allocatedId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = LauncherWidgetHost.get(this)
        manager = AppWidgetManager.getInstance(this)
        allocatedId = host.allocateAppWidgetId()
        val pick = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(pick, REQUEST_PICK)
    }

    @Deprecated("Deprecated in Android; retained for platform widget picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PICK -> {
                if (resultCode != Activity.RESULT_OK) return cancelAndFinish()
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
                    ?: allocatedId
                allocatedId = id
                val info = manager.getAppWidgetInfo(id) ?: return cancelAndFinish()
                val configure = info.configure
                if (configure != null) {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_CONFIGURE)
                } else {
                    finishAddWidget(info)
                }
            }

            REQUEST_CONFIGURE -> {
                if (resultCode != Activity.RESULT_OK) return cancelAndFinish()
                val info = manager.getAppWidgetInfo(allocatedId) ?: return cancelAndFinish()
                finishAddWidget(info)
            }
        }
    }

    private fun finishAddWidget(info: AppWidgetProviderInfo) {
        val repo = AppsRepository(applicationContext)
        val current = repo.loadPinnedTiles().toMutableList()
        val tileId = "widget_${allocatedId}_${System.currentTimeMillis()}"
        val title = runCatching { info.loadLabel(packageManager) }.getOrNull()
            ?.takeIf(String::isNotBlank) ?: "Widget"
        LauncherFeatureStore.setWidgetStackIds(this, tileId, listOf(allocatedId))
        current += TileModel(
            id = tileId,
            title = title,
            packageName = null,
            size = TileSize.WIDE,
            colorValue = 0xFF202020,
            iconGlyph = "app",
            groupName = "Widgets",
            order = current.size,
        )
        repo.savePinnedTiles(current)
        LauncherFeatureRuntime.notifyPinnedTilesChanged()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun cancelAndFinish() {
        if (allocatedId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runCatching { host.deleteAppWidgetId(allocatedId) }
        }
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val REQUEST_PICK = 7201
        private const val REQUEST_CONFIGURE = 7202
    }
}
