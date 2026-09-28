package com.flivoro.tile8auncher.features

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
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

private data class WidgetProviderChoice(
    val info: AppWidgetProviderInfo,
    val label: String,
    val packageLabel: String,
)

@Composable
private fun WidgetProviderPicker(
    choices: List<WidgetProviderChoice>,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF180424))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Widgets",
                color = Color.White,
                style = WindowsTypography.displayLarge.copy(fontSize = 38.sp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose a widget to pin to Start",
                color = Color.White.copy(alpha = .72f),
                style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
            )
            Spacer(Modifier.height(14.dp))

            if (choices.isEmpty()) {
                Text(
                    "No Android widget providers are installed.",
                    color = Color.White.copy(alpha = .78f),
                    style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                )
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(
                        items = choices,
                        key = { it.info.provider.flattenToString() },
                    ) { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(choice.info) }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .background(Color(0xFF5133AB)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    choice.label.take(1).uppercase(),
                                    color = Color.White,
                                    style = WindowsTypography.titleMedium.copy(fontSize = 18.sp),
                                )
                            }
                            Spacer(Modifier.size(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    choice.label,
                                    color = Color.White,
                                    style = WindowsTypography.bodyMedium.copy(fontSize = 14.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    choice.packageLabel,
                                    color = Color.White.copy(alpha = .58f),
                                    style = WindowsTypography.labelSmall.copy(fontSize = 10.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "Cancel",
                color = Color.White,
                style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
            )
        }
    }
}

/**
 * Tile8 uses its own provider list instead of launching ACTION_APPWIDGET_PICK. Several OEM
 * launchers (including MIUI/HyperOS builds) expose a picker activity that assumes the caller is
 * their own launcher and can crash when a third-party HOME app invokes it. Selecting the provider
 * in-process and asking Android only for the standard bind permission is both portable and safer.
 */
class WidgetPickerActivity : ComponentActivity() {
    private lateinit var host: AppWidgetHost
    private lateinit var manager: AppWidgetManager
    private var allocatedId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingProvider: ComponentName? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = LauncherWidgetHost.get(this)
        manager = AppWidgetManager.getInstance(this)
        allocatedId = savedInstanceState?.getInt(
            STATE_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = savedInstanceState?.getString(STATE_PROVIDER)
            ?.let(ComponentName::unflattenFromString)

        val choices = manager.installedProviders
            .map { info ->
                val label = runCatching { info.loadLabel(packageManager) }
                    .getOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: info.provider.className.substringAfterLast('.')
                val packageLabel = runCatching {
                    val app = packageManager.getApplicationInfo(info.provider.packageName, 0)
                    packageManager.getApplicationLabel(app).toString()
                }.getOrDefault(info.provider.packageName)
                WidgetProviderChoice(info, label, packageLabel)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        setContent {
            WidgetProviderPicker(
                choices = choices,
                onPick = ::beginBind,
                onCancel = ::cancelAndFinish,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_WIDGET_ID, allocatedId)
        outState.putString(STATE_PROVIDER, pendingProvider?.flattenToString())
        super.onSaveInstanceState(outState)
    }

    private fun beginBind(info: AppWidgetProviderInfo) {
        if (allocatedId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runCatching { host.deleteAppWidgetId(allocatedId) }
        }
        allocatedId = host.allocateAppWidgetId()
        pendingProvider = info.provider

        val alreadyAllowed = runCatching {
            manager.bindAppWidgetIdIfAllowed(allocatedId, info.provider)
        }.getOrDefault(false)

        if (alreadyAllowed) {
            configureOrFinish(info)
            return
        }

        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        }
        val launched = runCatching {
            @Suppress("DEPRECATION")
            startActivityForResult(bindIntent, REQUEST_BIND)
        }.isSuccess

        if (!launched) {
            Toast.makeText(
                this,
                "Android could not open widget permission for this provider.",
                Toast.LENGTH_LONG,
            ).show()
            cancelAndFinish()
        }
    }

    @Deprecated("Deprecated in Android; retained for platform widget bind/configure compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND -> {
                if (resultCode != Activity.RESULT_OK) return cancelAndFinish()
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
                    ?: allocatedId
                allocatedId = id
                val info = manager.getAppWidgetInfo(id)
                    ?: manager.installedProviders.firstOrNull { it.provider == pendingProvider }
                    ?: return cancelAndFinish()
                configureOrFinish(info)
            }

            REQUEST_CONFIGURE -> {
                if (resultCode != Activity.RESULT_OK) return cancelAndFinish()
                val info = manager.getAppWidgetInfo(allocatedId)
                    ?: manager.installedProviders.firstOrNull { it.provider == pendingProvider }
                    ?: return cancelAndFinish()
                finishAddWidget(info)
            }
        }
    }

    private fun configureOrFinish(info: AppWidgetProviderInfo) {
        val configure = info.configure
        if (configure == null) {
            finishAddWidget(info)
            return
        }

        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
        }
        val launched = runCatching {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CONFIGURE)
        }.isSuccess

        if (!launched) {
            Toast.makeText(
                this,
                "This widget's configuration screen is unavailable.",
                Toast.LENGTH_LONG,
            ).show()
            cancelAndFinish()
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
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId),
        )
        allocatedId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
        finish()
    }

    private fun cancelAndFinish() {
        if (allocatedId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runCatching { host.deleteAppWidgetId(allocatedId) }
            allocatedId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
        pendingProvider = null
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val REQUEST_BIND = 7200
        private const val REQUEST_CONFIGURE = 7202
        private const val STATE_WIDGET_ID = "widget_id"
        private const val STATE_PROVIDER = "widget_provider"
    }
}
