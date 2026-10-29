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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import kotlinx.coroutines.launch

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
            // A stack page owns a distinct host view. Rebinding an existing host view
            // loses its RemoteViews and can leave the previous widget's content behind.
            key(id) {
                AndroidView(
                    factory = { WidgetHostViewport(it, host.createView(it, id, info), info) },
                    modifier = Modifier.fillMaxSize().testTag("hosted-widget-$id"),
                )
            }
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
                    .align(Alignment.BottomEnd)
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

internal data class WidgetProviderChoice(
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
): FrameLayout = providerDimensions(context, info).let { dimensions ->
    WidgetPreviewViewport(context,
        (dimensions.width * context.resources.displayMetrics.density).toInt(),
        (dimensions.height * context.resources.displayMetrics.density).toInt())
}.apply {
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

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
                }, LinearLayout.LayoutParams(28.dpPx(context), 28.dpPx(context)))
                addView(TextView(context).apply {
                    text = "Preview unavailable"
                    setTextColor(android.graphics.Color.LTGRAY)
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setPadding(0, 4.dpPx(context), 0, 0)
                })
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }
}

private fun Int.dpPx(context: Context): Int =
    (this * context.resources.displayMetrics.density + 0.5f).toInt()

internal fun providerDimensions(context: Context, info: AppWidgetProviderInfo): WidgetDimensions =
    widgetDimensions(info.minWidth, info.minHeight, context.resources.displayMetrics.density,
        if (Build.VERSION.SDK_INT >= 31) info.targetCellWidth else 0,
        if (Build.VERSION.SDK_INT >= 31) info.targetCellHeight else 0)

@Composable
internal fun WidgetChoiceCard(
    choice: WidgetProviderChoice,
    selected: Boolean = false,
    enabled: Boolean = true,
    onPick: (AppWidgetProviderInfo) -> Unit,
) {
    val context = LocalContext.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val info = choice.info
    val dimensions = remember(context, info) { providerDimensions(context, info) }
    val sizeLabel = if (Build.VERSION.SDK_INT >= 31 &&
        info.targetCellWidth > 0 && info.targetCellHeight > 0)
        "${info.targetCellWidth} × ${info.targetCellHeight} cells"
    else "${dimensions.width.toInt()} × ${dimensions.height.toInt()} dp"
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .985f else 1f, label = "widget-card-press")
    val shape = RoundedCornerShape(10.dp)

    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) Color(0xFF45285C) else Color(0xFF281333))
            .border(if (selected) 2.dp else 1.dp,
                if (selected) Color(0xFFC6A4FF) else Color(0xFF53465D), shape)
            .clickable(enabled = enabled, interactionSource = interactions,
                indication = androidx.compose.material3.ripple(), role = Role.Button) { onPick(info) }
            .semantics {
                stateDescription = if (selected) "Adding widget" else "Add to Start, $sizeLabel"
                if (selected) liveRegion = LiveRegionMode.Polite
            }
            .padding(12.dp)
            .testTag("widget-choice-${info.provider.flattenToString()}"),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val preview = fitWidgetPreview(dimensions, maxWidth.value.coerceAtMost(280f))
            // Use native dp dimensions and shrink only when necessary. A 1×1 clock
            // must not be inflated into the same huge frame as a 4×4 calendar.
            Box(Modifier.fillMaxWidth().height(preview.height.dp.coerceAtLeast(64.dp)),
                contentAlignment = Alignment.Center) {
                key(info.provider) {
                    AndroidView(
                        factory = { widgetPreviewView(it, info, manager) },
                        modifier = Modifier.size(preview.width.dp, preview.height.dp)
                            .testTag("widget-preview-${info.provider.flattenToString()}"),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(choice.label, color = Color.White,
                    style = WindowsTypography.bodyMedium.copy(fontSize = 15.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${choice.packageLabel} · $sizeLabel", color = Color.White.copy(alpha = .65f),
                    style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                CircularProgressIndicator(Modifier.padding(start = 12.dp).size(16.dp),
                    color = Color(0xFFC6A4FF), strokeWidth = 2.dp)
            }
            Text(if (selected) "Adding…" else "Add +", color = Color.White,
                style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun WidgetProviderPicker(
    choices: List<WidgetProviderChoice>,
    appName: String?,
    pendingProvider: ComponentName?,
    errorMessage: String?,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismissSheet: () -> Unit = {
        scope.launch { sheetState.hide(); onCancel() }
    }
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = Color(0xFF180424),
        contentColor = Color.White,
        tonalElevation = 0.dp,
        scrimColor = Color(0x99000000),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF8B78A5)) },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f)
            .padding(horizontal = 20.dp)
            .testTag(if (appName != null) "app-widget-bottom-sheet" else "widget-picker")) {
            Text(if (appName != null) "$appName widgets" else "Widgets",
                color = Color.White,
                style = WindowsTypography.displayLarge.copy(fontSize = 27.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("Tap a widget to add it to Start", color = Color.White.copy(alpha = .72f),
                style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp))
            Spacer(Modifier.height(14.dp))
            if (errorMessage != null) {
                Text(errorMessage, color = Color(0xFFFFC6C6),
                    style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                    modifier = Modifier.padding(bottom = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (choices.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Text(if (appName != null) "This app doesn't offer any widgets."
                        else "No Android widget providers are installed.",
                        color = Color.White.copy(alpha = .78f),
                        style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp))
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    items(choices, key = { it.info.provider.flattenToString() }) { choice ->
                        WidgetChoiceCard(choice, selected = pendingProvider == choice.info.provider,
                            enabled = pendingProvider == null, onPick = onPick)
                    }
                }
            }
            Text("Cancel", color = Color.White,
                style = WindowsTypography.bodyMedium.copy(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(onClick = dismissSheet).padding(vertical = 14.dp, horizontal = 4.dp))
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
    private var pendingProvider by mutableStateOf<ComponentName?>(null)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val packageFilter = intent.getStringExtra(EXTRA_PACKAGE_NAME)?.takeIf(String::isNotBlank)
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
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFC6A4FF))) {
                BackHandler(onBack = ::cancelAndFinish)
                WidgetProviderPicker(
                    choices = choices,
                    appName = appName,
                    pendingProvider = pendingProvider,
                    errorMessage = errorMessage,
                    onPick = ::beginBind,
                    onCancel = ::cancelAndFinish,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_WIDGET_ID, allocatedId)
        outState.putString(STATE_PROVIDER, pendingProvider?.flattenToString())
        super.onSaveInstanceState(outState)
    }

    private fun beginBind(info: AppWidgetProviderInfo) {
        // Rapid taps must not delete an ID while Android is binding/configuring it.
        if (pendingProvider != null || allocatedId != AppWidgetManager.INVALID_APPWIDGET_ID) return
        errorMessage = null
        allocatedId = runCatching { host.allocateAppWidgetId() }.getOrElse {
            errorMessage = "Android couldn't prepare this widget. Please try again."
            return
        }
        pendingProvider = info.provider
        val dimensions = providerDimensions(this, info)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, dimensions.width.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, dimensions.width.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, dimensions.height.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, dimensions.height.toInt())
        }

        val alreadyAllowed = runCatching {
            manager.bindAppWidgetIdIfAllowed(allocatedId, info.profile, info.provider, options)
        }.getOrDefault(false)

        if (alreadyAllowed) {
            configureOrFinish(info)
            return
        }

        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
        }
        val launched = runCatching {
            @Suppress("DEPRECATION")
            startActivityForResult(bindIntent, REQUEST_BIND)
        }.isSuccess

        if (!launched) {
            resetPending("Android couldn't open this widget's permission screen. Try another widget.")
        }
    }

    @Deprecated("Deprecated in Android; retained for platform widget bind/configure compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND -> {
                if (resultCode != Activity.RESULT_OK) return resetPending("Widget permission was cancelled. Nothing was added.")
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
                    ?: allocatedId
                if (id != allocatedId) return resetPending("Android returned a different widget. Please try again.")
                val info = manager.getAppWidgetInfo(id)
                    ?: return resetPending("This widget could not be connected. Please try again.")
                configureOrFinish(info)
            }

            REQUEST_CONFIGURE -> {
                if (resultCode != Activity.RESULT_OK) return resetPending("Widget setup was cancelled. Nothing was added.")
                val info = manager.getAppWidgetInfo(allocatedId)
                    ?: return resetPending("This widget is no longer available. Try another widget.")
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

        val launched = runCatching {
            host.startAppWidgetConfigureActivityForResult(this, allocatedId, 0, REQUEST_CONFIGURE, null)
        }.isSuccess

        if (!launched) {
            resetPending("This widget's setup screen is unavailable. Try another widget.")
        }
    }

    private fun finishAddWidget(info: AppWidgetProviderInfo) {
        if (allocatedId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            manager.getAppWidgetInfo(allocatedId) == null) {
            resetPending("This widget is no longer available. Try another widget.")
            return
        }
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
            size = widgetTileSize(providerDimensions(this, info)),
            colorValue = 0xFF202020,
            iconGlyph = "app",
            groupName = "Widgets",
            order = current.size,
        )
        repo.savePinnedTiles(current)
        LauncherFeatureRuntime.notifyPinnedTilesChanged()
        Toast.makeText(this, "$title added to Start", Toast.LENGTH_SHORT).show()
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId),
        )
        allocatedId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
        finish()
    }

    private fun resetPending(message: String? = null) {
        if (allocatedId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runCatching { host.deleteAppWidgetId(allocatedId) }
            allocatedId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
        pendingProvider = null
        errorMessage = message
    }

    private fun cancelAndFinish() {
        resetPending()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        // Back/dismiss must release allocations, while rotation and an external
        // configuration screen must retain the ID restored from saved state.
        if (isFinishing) resetPending()
        super.onDestroy()
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

