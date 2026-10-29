package com.flivoro.tile8auncher.features

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.R
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

/** Package filtering is exact: a widget must belong to the long-pressed app. */
internal fun <T> widgetChoicesForPackage(
    choices: List<T>,
    packageName: String?,
    providerPackage: (T) -> String,
): List<T> = if (packageName == null) choices else choices.filter {
    providerPackage(it) == packageName
}

/**
 * Preview precedence matches Android's picker: Android 15 generated RemoteViews,
 * Android 12 previewLayout, then the provider's static previewImage. No widget
 * IDs are allocated and no live widget callbacks run in this picker.
 */
private fun widgetPreviewView(
    context: Context,
    info: AppWidgetProviderInfo,
    manager: AppWidgetManager,
    onPick: () -> Unit,
): FrameLayout = FrameLayout(context).apply {
    setBackgroundColor(android.graphics.Color.rgb(35, 35, 42))

    val generated: RemoteViews? = if (Build.VERSION.SDK_INT >= 35) runCatching {
        manager.getWidgetPreview(
            info.provider,
            info.profile,
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
        )
    }.getOrNull() else null
    val previewLayout: RemoteViews? = if (Build.VERSION.SDK_INT >= 31 && info.previewLayout != 0) {
        RemoteViews(info.provider.packageName, info.previewLayout)
    } else null

    val remotePreviewAdded = sequenceOf(generated, previewLayout).filterNotNull().any { remote ->
        runCatching {
            val preview = remote.apply(context, this)
            addView(preview, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ))
        }.isSuccess
    }

    if (!remotePreviewAdded) {
        val image = runCatching {
            info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
        }.getOrNull()
        if (image != null) {
            addView(ImageView(context).apply {
                setImageDrawable(image)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(8.dpPx(context), 8.dpPx(context), 8.dpPx(context), 8.dpPx(context))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            // The provider supplies neither a generated, layout, nor image preview.
            // Show its real icon, explicitly labelling this as a fallback.
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ImageView(context).apply {
                    setImageDrawable(runCatching { info.loadIcon(context, context.resources.displayMetrics.densityDpi) }
                        .getOrNull() ?: runCatching {
                        context.packageManager.getApplicationIcon(info.provider.packageName)
                    }.getOrNull())
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(52.dpPx(context), 52.dpPx(context)))
                addView(TextView(context).apply {
                    text = "Preview not provided by app"
                    setTextColor(android.graphics.Color.LTGRAY)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(0, 10.dpPx(context), 0, 0)
                })
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }
    // RemoteViews can contain PendingIntent buttons. The picker must never activate
    // those: tapping any part of a preview only selects the widget provider.
    addView(View(context).apply {
        isClickable = true
        setOnClickListener { onPick() }
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}

private fun Int.dpPx(context: Context): Int =
    (this * context.resources.displayMetrics.density + 0.5f).toInt()

@Composable
private fun WidgetChoiceCard(choice: WidgetProviderChoice, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val info = choice.info
    val hasCellDimensions = Build.VERSION.SDK_INT >= 31 &&
        info.targetCellWidth > 0 && info.targetCellHeight > 0
    val width = if (hasCellDimensions) info.targetCellWidth else info.minWidth.coerceAtLeast(1)
    val height = if (hasCellDimensions) info.targetCellHeight else info.minHeight.coerceAtLeast(1)
    val aspectRatio = (width.toFloat() / height).coerceIn(.25f, 5f)

    Column(
        Modifier.fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, Color(0xFF53465D), RoundedCornerShape(10.dp))
            .background(Color(0xFF281333), RoundedCornerShape(10.dp))
            .clickable { onPick(info) }
            .padding(12.dp)
            .testTag("widget-choice-${info.provider.flattenToString()}"),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Respect the widget's native shape instead of stretching every preview
            // into the same landscape rectangle.
            val availableWidth = maxWidth.coerceAtMost(320.dp)
            val previewHeight = (availableWidth.value / aspectRatio).dp.coerceAtMost(220.dp)
            val previewWidth = (previewHeight.value * aspectRatio).dp
            Box(Modifier.fillMaxWidth().height(previewHeight.coerceAtLeast(110.dp)),
                contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { widgetPreviewView(it, info, manager) { onPick(info) } },
                    modifier = Modifier.size(previewWidth, previewHeight)
                        .testTag("widget-preview-${info.provider.flattenToString()}"),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(choice.label, color = Color.White,
                    style = WindowsTypography.bodyMedium.copy(fontSize = 15.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(choice.packageLabel, color = Color.White.copy(alpha = .65f),
                    style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Add +", color = Color.White,
                style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun WidgetProviderPicker(
    choices: List<WidgetProviderChoice>,
    appName: String?,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onCancel: () -> Unit,
) {
    val isAppSheet = appName != null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isAppSheet) Color(0xB8000000) else Color(0xFF180424))
            .then(if (isAppSheet) Modifier.clickable(onClick = onCancel)
                else Modifier.statusBarsPadding()),
    ) {
        Column(
            Modifier
                .then(if (isAppSheet) Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().fillMaxHeight(.78f)
                    .background(Color(0xFF180424), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .clickable { /* consume taps within the sheet */ }
                else Modifier.fillMaxSize())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag(if (isAppSheet) "app-widget-bottom-sheet" else "widget-picker"),
        ) {
            Text(
                if (isAppSheet) "$appName widgets" else "Widgets",
                color = Color.White,
                style = WindowsTypography.displayLarge.copy(fontSize = if (isAppSheet) 27.sp else 38.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isAppSheet) "Choose a widget from this app to add to Start"
                    else "Choose a widget to pin to Start",
                color = Color.White.copy(alpha = .72f),
                style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
            )
            Spacer(Modifier.height(14.dp))

            if (choices.isEmpty()) {
                Text(
                    if (isAppSheet) "This app doesn't offer any widgets."
                        else "No Android widget providers are installed.",
                    color = Color.White.copy(alpha = .78f),
                    style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                )
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(
                        items = choices,
                        key = { it.info.provider.flattenToString() },
                    ) { choice ->
                        WidgetChoiceCard(choice, onPick)
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
        val packageFilter = intent.getStringExtra(EXTRA_PACKAGE_NAME)?.takeIf(String::isNotBlank)
        if (packageFilter != null) setTheme(R.style.Theme_Tile8Launcher_WidgetSheet)
        super.onCreate(savedInstanceState)
        host = LauncherWidgetHost.get(this)
        manager = AppWidgetManager.getInstance(this)
        allocatedId = savedInstanceState?.getInt(
            STATE_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = savedInstanceState?.getString(STATE_PROVIDER)
            ?.let(ComponentName::unflattenFromString)

        val choices = widgetChoicesForPackage(manager.installedProviders, packageFilter) {
            it.provider.packageName
        }
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

        val appName = packageFilter?.let { pkg ->
            runCatching { packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0),
            ).toString() }.getOrDefault(pkg)
        }

        setContent {
            WidgetProviderPicker(
                choices = choices,
                appName = appName,
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
        const val EXTRA_PACKAGE_NAME = "com.flivoro.tile8auncher.extra.WIDGET_PACKAGE_NAME"

        fun forApp(context: Context, packageName: String): Intent =
            Intent(context, WidgetPickerActivity::class.java).putExtra(EXTRA_PACKAGE_NAME, packageName)

        private const val REQUEST_BIND = 7200
        private const val REQUEST_CONFIGURE = 7202
        private const val STATE_WIDGET_ID = "widget_id"
        private const val STATE_PROVIDER = "widget_provider"
    }
}
