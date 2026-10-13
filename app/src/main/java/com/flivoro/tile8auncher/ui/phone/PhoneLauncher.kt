package com.flivoro.tile8auncher.ui.phone

import android.content.Context
import android.content.Intent
import android.graphics.Matrix as PlatformMatrix
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.LiveTileNotificationStore
import com.flivoro.tile8auncher.features.LiveTileRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.PhoneBackgroundStyle
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.loadEnhancedApps
import com.flivoro.tile8auncher.features.recentlyInstalledApps as selectRecentlyInstalledApps
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import com.flivoro.tile8auncher.ui.components.WindowsTileFace
import com.flivoro.tile8auncher.ui.components.WindowsWallpaper
import com.flivoro.tile8auncher.ui.components.elasticVerticalScroll
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import kotlin.math.roundToInt

/** Phone Start coordinates are independent from Desktop's horizontal tile-group coordinates. */
internal data class PhoneTileSlot(val column: Int, val row: Int, val columns: Int, val rows: Int)

private const val MOSAIC_SETTINGS_ID = "mosaic.launcher.settings"

// Preview/instrumentation can inspect an exact motion timestamp independently
// of a device's vsync interval. Production always uses the display clock.
internal val LocalPhoneMotionSampleMillis = staticCompositionLocalOf<(() -> Int?)?> { null }

private fun projectPlaneMatrix(
    widthPx: Float,
    heightPx: Float,
    density: Float,
    elementLeftCssPx: Float,
    elementTopCssPx: Float,
    project: (Float, Float) -> PhoneProjectedPoint,
): PlatformMatrix {
    val source = floatArrayOf(0f, 0f, widthPx, 0f, widthPx, heightPx, 0f, heightPx)
    val destination = FloatArray(8)
    val corners = arrayOf(
        0f to 0f,
        widthPx to 0f,
        widthPx to heightPx,
        0f to heightPx,
    )
    corners.forEachIndexed { index, (xPx, yPx) ->
        val point = project(xPx / density, yPx / density)
        destination[index * 2] = (point.xCssPx - elementLeftCssPx) * density
        destination[index * 2 + 1] = (point.yCssPx - elementTopCssPx) * density
    }
    return PlatformMatrix().also { matrix ->
        if (!matrix.setPolyToPoly(source, 0, destination, 0, 4)) {
            matrix.reset()
        }
    }
}

private fun nestedProjectionCorrection(
    outer: PlatformMatrix,
    combined: PlatformMatrix,
    widthPx: Float,
    heightPx: Float,
): PlatformMatrix? {
    val inverseOuter = PlatformMatrix()
    if (!outer.invert(inverseOuter)) return null
    val source = floatArrayOf(0f, 0f, widthPx, 0f, widthPx, heightPx, 0f, heightPx)
    val destination = source.copyOf()
    combined.mapPoints(destination)
    inverseOuter.mapPoints(destination)
    return PlatformMatrix().also { correction ->
        if (!correction.setPolyToPoly(source, 0, destination, 0, 4)) return null
    }
}

private fun appListElementProjectionMatrix(
    widthPx: Float,
    heightPx: Float,
    density: Float,
    bounds: Rect,
    viewportBounds: Rect,
    motion: PhoneStartMotionFrame,
    cameraDistanceCssPx: Float = PhoneStartChoreography.CLASSIC_TILE_PERSPECTIVE_CSS_PX,
): PlatformMatrix {
    val leftCss = (bounds.left - viewportBounds.left) / density
    val topCss = (bounds.top - viewportBounds.top) / density
    val widthCss = widthPx / density
    val heightCss = heightPx / density
    val viewportWidthCss = viewportBounds.width / density
    val viewportHeightCss = viewportBounds.height / density
    return projectPlaneMatrix(widthPx, heightPx, density, leftCss, topCss) { xCss, yCss ->
        PhoneStartChoreography.projectPlanePoint(
            localXPx = xCss,
            localYPx = yCss,
            elementLeftCssPx = leftCss,
            elementTopCssPx = topCss,
            elementWidthCssPx = widthCss,
            elementHeightCssPx = heightCss,
            viewportWidthCssPx = viewportWidthCss,
            viewportHeightCssPx = viewportHeightCss,
            motion = motion,
            cameraDistanceCssPx = cameraDistanceCssPx,
        )
    }
}

/** First-fit cell packing; unlike LazyVerticalGrid it does not waste a row beside small tiles. */
internal fun packPhoneTiles(sizes: List<TileSize>, columns: Int): List<PhoneTileSlot> {
    require(columns == 4 || columns == 6)
    val used = mutableListOf<BooleanArray>()
    val result = ArrayList<PhoneTileSlot>(sizes.size)
    sizes.forEach { size ->
        val width = when (size) {
            TileSize.SMALL -> 1
            TileSize.MEDIUM -> 2
            TileSize.WIDE, TileSize.LARGE -> 4
        }.coerceAtMost(columns)
        val height = if (size == TileSize.LARGE) 4 else if (size == TileSize.SMALL) 1 else 2
        var y = 0
        var found = false
        while (!found) {
            while (used.size < y + height) used.add(BooleanArray(columns))
            for (x in 0..columns - width) {
                if ((y until y + height).all { r -> (x until x + width).all { c -> !used[r][c] } }) {
                    for (r in y until y + height) for (c in x until x + width) used[r][c] = true
                    result.add(PhoneTileSlot(x, y, width, height))
                    found = true
                    break
                }
            }
            y++
        }
    }
    return result
}

/** The per-phone layout, size and color keys are exported by Til's existing feature backup. */
internal object PhoneLayoutStore {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE,
    )
    // WP8.1 and W10M are themes over ONE phone Start workspace.
    // Desktop keeps its independent layout and tiles.
    private fun key(mode: LauncherUiMode, suffix: String) =
        if (mode == LauncherUiMode.DESKTOP) "phone_DESKTOP_$suffix"
        else "phone_shared_$suffix"

    private fun legacyKey(mode: LauncherUiMode, suffix: String) =
        "phone_${mode.name}_$suffix"

    private fun migrateIfNeeded(context: Context, mode: LauncherUiMode, suffix: String) {
        if (mode == LauncherUiMode.DESKTOP) return
        val preferences = prefs(context)
        val destination = key(mode, suffix)
        if (preferences.contains(destination)) return
        // Give the currently selected phone mode precedence, then recover the
        // other mode's prior workspace. Migrate without clearing either backup.
        val old = listOf(mode,
            if (mode == LauncherUiMode.PHONE_8) LauncherUiMode.MOBILE_10
            else LauncherUiMode.PHONE_8)
            .map { legacyKey(it, suffix) }
            .firstOrNull(preferences::contains) ?: return
        preferences.edit {
            when (val value = preferences.all[old]) {
                is String -> putString(destination, value)
                is Long -> putLong(destination, value)
                is Int -> putInt(destination, value)
            }
        }
    }

    fun order(context: Context, mode: LauncherUiMode, defaults: List<TileModel>): List<String> {
        migrateIfNeeded(context, mode, "order")
        val raw = prefs(context).getString(key(mode, "order"), null)
            ?: return defaults.map(TileModel::id)
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { json.optString(it).takeIf(String::isNotBlank) }.distinct()
        }.getOrElse { defaults.map(TileModel::id) }
    }

    fun saveOrder(context: Context, mode: LauncherUiMode, ids: List<String>) {
        val array = JSONArray()
        ids.distinct().forEach(array::put)
        prefs(context).edit { putString(key(mode, "order"), array.toString()) }
    }

    fun resetOrder(context: Context, mode: LauncherUiMode) {
        prefs(context).edit { remove(key(mode, "order")) }
    }

    fun size(context: Context, mode: LauncherUiMode, tile: TileModel): TileSize {
        migrateIfNeeded(context, mode, "size_${tile.id}")
        val name = prefs(context).getString(key(mode, "size_${tile.id}"), tile.size.name)
        val chosen = TileSize.entries.firstOrNull { it.name == name } ?: tile.size
        return if (chosen == TileSize.LARGE) TileSize.WIDE else chosen
    }

    fun saveSize(context: Context, mode: LauncherUiMode, tileId: String, size: TileSize) {
        prefs(context).edit { putString(key(mode, "size_$tileId"), size.name) }
    }

    fun color(context: Context, mode: LauncherUiMode, tile: TileModel): Long {
        migrateIfNeeded(context, mode, "color_${tile.id}")
        return prefs(context).getLong(key(mode, "color_${tile.id}"), tile.colorValue)
    }

    fun saveColor(context: Context, mode: LauncherUiMode, tileId: String, color: Long) {
        prefs(context).edit { putLong(key(mode, "color_$tileId"), color) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneLauncherSurface(
    mode: LauncherUiMode,
    tiles: List<TileModel>,
    sections: List<AppSection>,
    appsRepository: AppsRepository,
    homeRequest: Int,
    entranceRequest: Int,
    launchingTileId: String?,
    isLaunching: Boolean,
    interactionEnabled: Boolean,
    wallpaperStyle: Int,
    onLaunch: (TileModel, Rect, LaunchOrigin) -> Unit,
    onExitFinished: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onVisiblePageChanged: (Boolean) -> Unit = {},
    resumeUsesBackMotion: Boolean = false,
    screenOffRequest: Int = 0,
    returningTileId: String? = null,
) {
    var classicHomeTap by remember(mode) { mutableStateOf(0) }
    var classicHomeEntrance by remember(mode) { mutableStateOf(-1) }
    val motionHomeRequest = homeRequest + classicHomeTap
    val motionUsesBack = resumeUsesBackMotion && classicHomeEntrance != entranceRequest
    val sampledMotionMillis = LocalPhoneMotionSampleMillis.current
    val context = LocalContext.current
    val isTen = mode == LauncherUiMode.MOBILE_10
    val densityScale = LocalDensity.current.density
    val accent = StartPersonalization.accentColor
    val catalog = remember(sections) {
        (sections.flatMap(AppSection::apps) + AppInfo(
            label = "Mosaic Settings",
            packageName = MOSAIC_SETTINGS_ID,
            activityName = "",
        )).distinctBy(AppInfo::packageName)
    }
    val byPackage = remember(catalog) { catalog.associateBy(AppInfo::packageName) }
    val desktopById = remember(tiles, mode) {
        tiles.associateBy(TileModel::id).mapValues { (id, tile) ->
            when (id) {
                "tile_settings" -> tile.copy(title = "Mosaic Settings", iconGlyph = "settings")
                "tile_mail", "tile_calendar" -> tile.copy(size = TileSize.MEDIUM)
                else -> tile
            }
        }
    }
    // Fresh phone modes get a phone-oriented default, rather than the desktop's
    // huge Mail/Calendar columns, Desktop, PC settings, and Reading List.
    // Saved user orders take precedence: this never migrates or clears existing pins.
    val phoneSeed = remember(tiles, catalog, mode) {
        val dialer = catalog.firstOrNull {
            it.label.equals("Phone", ignoreCase = true) ||
                it.label.equals("Dialer", ignoreCase = true)
        }
        val messages = catalog.firstOrNull {
            it.label.equals("Messages", ignoreCase = true) ||
                it.label.equals("Messaging", ignoreCase = true)
        }
        val topAppIds = listOfNotNull(dialer, messages).map { "app:" + it.packageName }
        val stockIds = if (mode == LauncherUiMode.MOBILE_10) listOf(
            "tile_mail", "tile_people", "tile_calendar", "tile_photos",
            "tile_camera", "tile_skype", "tile_weather", "tile_settings",
            "tile_store", "tile_clock", "tile_onedrive", "tile_maps",
        ) else listOf(
            "tile_people", "tile_mail", "tile_calendar", "tile_skype",
            "tile_photos", "tile_camera", "tile_ie", "tile_weather",
            "tile_settings", "tile_store", "tile_clock", "tile_maps",
        )
        val nativePhoneDefaults = (topAppIds + stockIds).distinct().mapNotNull { id ->
            desktopById[id] ?: if (id.startsWith("app:")) {
                byPackage[id.removePrefix("app:")]?.let { app ->
                    TileModel(id = id, title = app.label, packageName = app.packageName)
                }
            } else null
        }
        // Render callers such as emulator motion fixtures supply non-stock tile
        // IDs; preserve their complete fixture rather than producing an empty Start.
        nativePhoneDefaults.ifEmpty { tiles }
    }
    var order by remember(mode) {
        mutableStateOf(PhoneLayoutStore.order(context, mode, phoneSeed))
    }
    var editing by remember(mode) { mutableStateOf<String?>(null) }
    // Back returns to the previously visible page. Only Home and screen-off reset to Start;
    // entranceRequest must not recreate showApps or wipe the app drawer's scroll position.
    var showApps by rememberSaveable(mode, motionHomeRequest, screenOffRequest) {
        mutableStateOf(false)
    }
    LaunchedEffect(showApps) { onVisiblePageChanged(showApps) }
    var suppressReturnPaneTransition by remember(mode, entranceRequest, motionHomeRequest, screenOffRequest) {
        // A request counter > 0 means an event occurred in the past, NOT that Home is
        // currently returning. When the saved page is All Apps, never park the pane at Start.
        mutableStateOf(mode == LauncherUiMode.PHONE_8 && !showApps && !motionUsesBack &&
            (entranceRequest > 0 || motionHomeRequest > 0 || screenOffRequest > 0))
    }
    var alphabetOpen by remember(mode) { mutableStateOf(false) }
    var actionCenterOpen by remember(mode) { mutableStateOf(false) }
    var phoneSettingsOpen by remember(mode) { mutableStateOf(false) }
    var search by remember(mode) { mutableStateOf("") }
    var phone8SearchVisible by remember(mode) { mutableStateOf(false) }
    var phoneRevision by remember(mode) { mutableStateOf(0) }
    var folderRevision by remember(mode) { mutableIntStateOf(0) }
    val phoneFolders = remember(mode, folderRevision) {
        PhoneFolderStore.read(context, mode)
    }
    val foldersById = remember(phoneFolders) { phoneFolders.associateBy(PhoneFolder::id) }
    var openFolderId by remember(mode) { mutableStateOf<String?>(null) }
    var openFolderBounds by remember(mode) { mutableStateOf(Rect.Zero) }
    val densityRevision = LauncherFeatureRuntime.launcherModeRevision
    val mobileTilePictureMode = remember(mode, densityRevision) {
        isTen && LauncherFeatureStore.phoneBackgroundStyle(context) ==
            PhoneBackgroundStyle.TILE_PICTURE
    }
    val columns = remember(mode, densityRevision) { LauncherFeatureStore.phoneSmallColumns(context, mode) }
    val tileOpacity = remember(mode, densityRevision) {
        if (isTen) LauncherFeatureStore.phoneTileOpacity(context) else 1f
    }
    val appsState = rememberLazyListState()
    val startScroll = rememberScrollState()
    var surfaceBounds by remember(mode) { mutableStateOf(Rect.Zero) }
    var paneBounds by remember(mode) { mutableStateOf(Rect.Zero) }
    var appsListBounds by remember(mode) { mutableStateOf(Rect.Zero) }
    var homeBannerBounds by remember(mode) { mutableStateOf(Rect.Zero) }
    // Disco reads window.innerHeight (the whole CSS viewport), not just
    // the Start/Apps content pane below status and navigation surfaces.
    val viewportHeightCssPx = (surfaceBounds.height / densityScale)
        .takeIf { it > 0f } ?: 850f
    val isClassicPhone = mode == LauncherUiMode.PHONE_8
    // W10M All Apps stays settled on an ordinary Start -> Apps swipe. Its rows move
    // only for app launch and the independently measured return on Back.
    // Disco's #search-icon starts hidden and gets the .shown class only when
    // the user moves into All Apps. Its CSS enters after 200ms over 250ms,
    // then hides 500ms after returning to Start.
    val appSearchIconAlpha = remember(mode) { Animatable(0f) }
    LaunchedEffect(mode, showApps) {
        if (isClassicPhone) {
            if (showApps) {
                delay(200)
                appSearchIconAlpha.animateTo(1f, tween(250, easing = LinearEasing))
            } else {
                delay(500)
                appSearchIconAlpha.snapTo(0f)
            }
        }
    }
    // WP8.1 uses a 480-unit design viewport. Android dp must scale with width
    // here too: otherwise the app list grows relative to the native tile grid.
    val classicUnit = ((surfaceBounds.width / densityScale).takeIf { it > 0f }
        ?: LocalConfiguration.current.screenWidthDp.toFloat()) / 480f
    val appListContentLeftCssPx = if (isClassicPhone) 86f * classicUnit else 18f
    val appListSearchLeftCssPx = 26f * classicUnit
    val appListSearchSizeCssPx = 42f * classicUnit
    val appListContentWidthCssPx =
        (surfaceBounds.width / densityScale - if (isClassicPhone) 104f * classicUnit else 32f)
            .coerceAtLeast(1f)
    val listPhase = PhoneMotionPhase.FORWARD_OUT
    // Native forward entry is measured from the normal-speed reference.
    // The slowed orange-tile footage is used for geometry, not wall-clock delay.
    // One clock owns tiles, wallpaper and completion. A wall-clock delay in MainActivity
    // can expire before Compose has even presented the first animation frame.
    val duration = if (isLaunching && showApps && mode == LauncherUiMode.PHONE_8) {
        PhoneStartChoreography.totalMillis(
            mode, exiting = true, viewportHeightCssPx = viewportHeightCssPx,
        )
    } else if (isLaunching && showApps) {
        maxOf(PhoneMotionTimeline.totalMillis(mode, listPhase, 6),
            if (isTen) MobileStartMotion.EXIT_TOTAL_MS else 0)
    } else if (isTen && motionUsesBack && !isLaunching) {
        MobileStartMotion.BACK_TOTAL_MS
    } else if (isClassicPhone && motionUsesBack && !isLaunching && showApps) {
        PhoneStartChoreography.APPS_RETURN_TOTAL_MS
    } else if (isClassicPhone && !isLaunching && !motionUsesBack) {
        PhoneStartChoreography.classicNativeForwardTotalMillis(viewportHeightCssPx)
    } else PhoneStartChoreography.totalMillis(
        mode, exiting = isLaunching,
        viewportHeightCssPx = viewportHeightCssPx,
    )
    val motionClock = remember(mode, entranceRequest, motionHomeRequest, isLaunching, motionUsesBack) {
        Animatable(0f)
    }
    // This is a lifecycle phase, not a condition tied to the currently
    // selected panorama page. A completed Home return must NOT replay its
    // second-page 3D projection when the user swipes Apps -> Start later.
    var classicForwardEntranceActive by remember(
        mode, entranceRequest, motionHomeRequest, isLaunching, motionUsesBack,
    ) {
        mutableStateOf(isClassicPhone && !isLaunching && !motionUsesBack)
    }
    // Every layer shares one display-synchronized timeline. Relative tile
    // cascade delay is calibrated inside the classic choreography.
    fun sourceMotionMillis(): Int = sampledMotionMillis?.invoke() ?: motionClock.value.roundToInt()
    val classicAppsReturning = isClassicPhone && showApps && motionUsesBack && !isLaunching
    val classicAppsMotionActive = isClassicPhone && showApps && (isLaunching || motionUsesBack)
    fun classicAppsCameraDistance(): Float = if (classicAppsReturning)
        PhoneStartChoreography.appListReturnCameraDistance(surfaceBounds.width / densityScale)
        else PhoneStartChoreography.CLASSIC_TILE_PERSPECTIVE_CSS_PX
    fun classicAppsMotion(
        animationIndex: Float,
        tileLeftCssPx: Float,
        tileWidthCssPx: Float,
        selected: Boolean = false,
        letter: Boolean = false,
    ): PhoneStartMotionFrame = if (classicAppsReturning) {
        PhoneStartChoreography.sampleAppListReturn(
            sourceMotionMillis(), animationIndex, surfaceBounds.width / densityScale,
            tileLeftCssPx, letter,
        )
    } else PhoneStartChoreography.sampleAppListExit(
        sourceMotionMillis(), animationIndex, viewportHeightCssPx,
        surfaceBounds.width / densityScale, selected, letter, tileLeftCssPx, tileWidthCssPx,
    )
    fun mobileWallpaperAlpha(): Float = MobileStartMotion.wallpaperAlpha(
        exiting = isLaunching, elapsedMillis = sourceMotionMillis(),
        returningFromApp = motionUsesBack && !isLaunching,
    )
    val exitTileId = remember(isLaunching) { launchingTileId }
    val returnTileId = returningTileId.takeIf { motionUsesBack && !isLaunching }
    val latestExitFinished by rememberUpdatedState(onExitFinished)
    val latestDuration by rememberUpdatedState(duration)
    LaunchedEffect(mode, entranceRequest, motionHomeRequest, isLaunching, motionUsesBack) {
        snapshotFlow { paneBounds.width > 0f && surfaceBounds.height > 0f }.first { it }
        val animationDuration = latestDuration
        motionClock.animateTo(animationDuration.toFloat(), tween(animationDuration, easing = LinearEasing))
        classicForwardEntranceActive = false
        if (isLaunching) {
            // Let the completed pose be submitted before the external Activity is requested.
            withFrameNanos { }
            latestExitFinished()
        }
    }
    val hidden = LauncherFeatureStore.hiddenPackages(context) + LauncherFeatureStore.privatePackages(context)
    val visibleApps = remember(catalog, hidden) {
        catalog.filter { it.packageName !in hidden }.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.label },
        )
    }
    val showRecentlyInstalledPhoneApps = remember(mode, densityRevision) {
        mode == LauncherUiMode.MOBILE_10 &&
            LauncherFeatureStore.showRecentlyInstalledPhoneApps(context)
    }
    val recentlyInstalledApps by produceState(
        initialValue = emptyList<AppInfo>(),
        visibleApps,
        showRecentlyInstalledPhoneApps,
    ) {
        value = if (showRecentlyInstalledPhoneApps) withContext(Dispatchers.IO) {
            selectRecentlyInstalledApps(
                loadEnhancedApps(context.applicationContext, visibleApps),
            ).map { it.app }
        } else emptyList()
    }

    fun appTile(app: AppInfo): TileModel = TileModel(
        id = "phone_app_${app.packageName}",
        title = app.label,
        packageName = app.packageName,
        activityName = app.activityName,
        colorValue = appsRepository.getCachedAppAccentColor(app.packageName)
            ?: StartPersonalization.accentArgb,
        tileType = if (app.packageName == MOSAIC_SETTINGS_ID)
            com.flivoro.tile8auncher.data.TileType.SETTINGS
        else com.flivoro.tile8auncher.data.TileType.APP,
        iconGlyph = if (app.packageName == MOSAIC_SETTINGS_ID) "settings" else "",
    )

    fun tileFor(id: String): TileModel? {
        val source = desktopById[id] ?: when {
            id.startsWith("app:") -> byPackage[id.removePrefix("app:")]?.let(::appTile)
            id.startsWith("phone_folder_") -> foldersById[id]?.let { folder ->
                TileModel(
                    id = folder.id,
                    title = folder.name,
                    size = TileSize.MEDIUM,
                    colorValue = StartPersonalization.accentArgb,
                    iconGlyph = "folder",
                )
            }
            else -> null
        }
        return source?.copy(
            size = PhoneLayoutStore.size(context, mode, source),
            colorValue = PhoneLayoutStore.color(context, mode, source),
        )
    }

    val phoneTiles = remember(order, tiles, catalog, phoneRevision, folderRevision) {
        order.mapNotNull { id -> tileFor(id) }
    }
    val folderPreviews = remember(phoneFolders, catalog, tiles, phoneRevision) {
        phoneFolders.associate { folder ->
            folder.id to folder.children.mapNotNull { id -> tileFor(id) }
        }
    }

    fun saveOrder(next: List<String>) {
        order = next.distinct()
        PhoneLayoutStore.saveOrder(context, mode, order)
    }

    fun ungroupFolder(id: String) {
        val children = PhoneFolderStore.dissolve(context, mode, id)
        if (children.isEmpty()) return
        val oldIndex = order.indexOf(id).coerceAtLeast(0)
        val next = order.filterNot { it == id }.toMutableList()
        next.addAll(oldIndex.coerceAtMost(next.size), children)
        saveOrder(next)
        folderRevision++
        editing = null
        openFolderId = null
    }

    // Handle only a NEW Home request. Relaunching/recomposing the phone surface must not
    // replay an old Home event and overwrite the saved All Apps destination.
    var lastHandledHomeRequest by remember(mode) { mutableIntStateOf(motionHomeRequest) }
    LaunchedEffect(motionHomeRequest) {
        if (motionHomeRequest != lastHandledHomeRequest) {
            lastHandledHomeRequest = motionHomeRequest
            // DiscoLauncher snaps its panorama to Start, while the Start tiles
            // and the second All Apps page run their independent 3D entries.
            Snapshot.withMutableSnapshot {
                suppressReturnPaneTransition = mode == LauncherUiMode.PHONE_8
                showApps = false
                alphabetOpen = false
                editing = null
                actionCenterOpen = false
            }
            if (suppressReturnPaneTransition) {
                // Keep the outgoing Apps page composed behind Start until its
                // 100 ms delay + 750 ms source page-turn animation completes.
                delay(duration.toLong())
                suppressReturnPaneTransition = false
            }
        }
    }

    LaunchedEffect(entranceRequest, motionUsesBack) {
        if (entranceRequest > 0 && mode == LauncherUiMode.PHONE_8) {
            // The pane was already snapped when the entrance event changed
            // composition keys. Keep it parked there for the full 3D return,
            // including returns that previously had showApps=true.
            if (motionUsesBack) {
                Snapshot.withMutableSnapshot {
                    alphabetOpen = false
                    editing = null
                    actionCenterOpen = false
                }
            }
            delay(duration.toLong())
            suppressReturnPaneTransition = false
        }
    }

    BackHandler(interactionEnabled && (showApps || alphabetOpen || actionCenterOpen ||
        editing != null || openFolderId != null)) {
        when {
            openFolderId != null -> openFolderId = null
            editing != null -> editing = null
            alphabetOpen -> alphabetOpen = false
            actionCenterOpen -> actionCenterOpen = false
            else -> showApps = false
        }
    }

    // Both phone modes use the same finger-driven panorama. Initialise from the
    // remembered page so app Back does not briefly slide through Start.
    // The W10M tile/app launch effects remain on their independent motion clock.
    val classicPanorama = remember(mode) { Animatable(if (showApps) 1f else 0f) }
    val panoramaScope = rememberCoroutineScope()
    var classicDragPosition by remember(mode) { mutableStateOf<Float?>(null) }
    var classicReleasePosition by remember(mode) { mutableStateOf<Float?>(null) }
    var classicReleaseRequest by remember(mode) { mutableStateOf(0) }
    LaunchedEffect(mode, showApps, suppressReturnPaneTransition, classicReleaseRequest) {
        if (suppressReturnPaneTransition && isClassicPhone) {
            classicDragPosition = null
            classicReleasePosition = null
            classicPanorama.snapTo(0f)
        } else {
            classicReleasePosition?.let { classicPanorama.snapTo(it) }
            classicReleasePosition = null
            classicDragPosition = null
            classicPanorama.animateTo(if (showApps) 1f else 0f, tween(
                durationMillis = if (isTen) 260 else 270,
                easing = androidx.compose.animation.core.Easing {
                    PhoneMotionTimeline.exponentialEaseOut6(it)
                }))
        }
    }
    val gestureModifier = Modifier.pointerInput(mode, showApps, interactionEnabled, isLaunching) {
        if (!interactionEnabled || isLaunching) return@pointerInput
        val velocity = VelocityTracker()
        fun releasePanoramaDrag(cancelled: Boolean) {
            val position = classicDragPosition ?: return
            if (!cancelled) {
                val speed = velocity.calculateVelocity().x / densityScale
                showApps = if (kotlin.math.abs(speed) > 600f) speed < 0f else position >= .5f
            }
            classicReleasePosition = position
            classicReleaseRequest++
        }
        detectHorizontalDragGestures(
            onDragStart = {
                velocity.resetTracking()
                if (isClassicPhone) classicForwardEntranceActive = false
                classicDragPosition = classicPanorama.value
                panoramaScope.launch { classicPanorama.stop() }
            },
            onHorizontalDrag = { change, amount ->
                velocity.addPosition(change.uptimeMillis, change.position)
                classicDragPosition = ((classicDragPosition ?: classicPanorama.value) -
                    amount / paneBounds.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                change.consume()
            },
            onDragEnd = { releasePanoramaDrag(false) },
            onDragCancel = { releasePanoramaDrag(true) },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)
        .testTag("phone-navigation-state")
        .semantics { stateDescription = motionHomeRequest.toString() }
        .onGloballyPositioned { surfaceBounds = it.boundsInWindow() }) {
        if (isTen && !mobileTilePictureMode) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = mobileWallpaperAlpha()
            }) {
                WindowsWallpaper(wallpaperStyle = wallpaperStyle, enabled = false, scrollOffsetPx = { 0f })
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)))
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Android provides real cellular/Wi-Fi/battery status in its system status bar.
            // Do not draw invented signal icons immediately beneath those real indicators.
            if (isTen) {
                Row(Modifier.fillMaxWidth().height(22.dp).padding(end = 18.dp)
                    .graphicsLayer {
                        alpha = mobileWallpaperAlpha()
                    },
                    horizontalArrangement = Arrangement.End) {
                    Text("⌄", color = Color.White, fontSize = 20.sp,
                        modifier = Modifier.testTag("phone-action-center-trigger")
                            .clickable(enabled = interactionEnabled && !isLaunching) {
                            actionCenterOpen = !actionCenterOpen
                        })
                }
            }
            // Disco's #main-home-slider holds both slide pages in one panorama.
            // AnimatedContent was destroying the All Apps page whenever Start was selected,
            // so its independent 3D return was missing after launching from Start.
            // Keep both pages mounted and move the panorama with one shared X translation.
            // The drag position is composed immediately; only release settles
            // with the pre-existing 260/270 ms curve. Both panes translate together.
            val panoramaProgress = if (suppressReturnPaneTransition && isClassicPhone) 0f
                else classicDragPosition ?: classicPanorama.value
            Box(
                Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .onGloballyPositioned { paneBounds = it.boundsInWindow() }
                    .then(gestureModifier),
            ) {
                // Start is in front of the right-hand page during Disco's Home entrance.
                Box(
                    Modifier.fillMaxSize().zIndex(1f).graphicsLayer {
                        translationX = -paneBounds.width * panoramaProgress
                        // Each W10M zoom belongs to its own panorama page. A
                        // smaller returning Start tile must not extend past
                        // that offscreen page into the visible All Apps pane.
                        clip = isTen
                    },
                ) {
                    Column(Modifier.fillMaxSize().elasticVerticalScroll()
                        .verticalScroll(startScroll, enabled = interactionEnabled && !isLaunching)) {
                        Spacer(Modifier.height(if (isTen) 8.dp else 18.dp))
                        PhoneStartGrid(
                            tiles = phoneTiles,
                            columns = columns,
                            scrollOffsetPx = startScroll.value,
                            elapsedMillis = { sourceMotionMillis() },
                            surfaceBounds = surfaceBounds,
                            viewportBounds = paneBounds,
                            viewportHeightPx = paneBounds.height,
                            interactionEnabled = interactionEnabled,
                            launchingTileId = if (isLaunching) exitTileId else returnTileId,
                            isLaunching = isLaunching,
                            resumeUsesBackMotion = motionUsesBack,
                            mode = mode,
                            tileOpacity = tileOpacity,
                            wallpaperStyle = wallpaperStyle,
                            mobileTilePictureMode = mobileTilePictureMode,
                            appsRepository = appsRepository,
                            editingTileId = editing.takeIf { !showApps },
                            folderPreviews = folderPreviews,
                            onResizeTile = { id ->
                                tileFor(id)?.let { tile ->
                                    val size = when (tile.size) {
                                        TileSize.SMALL -> TileSize.MEDIUM
                                        TileSize.MEDIUM -> TileSize.WIDE
                                        TileSize.WIDE, TileSize.LARGE -> TileSize.SMALL
                                    }
                                    PhoneLayoutStore.saveSize(context, mode, tile.id, size)
                                    phoneRevision++
                                }
                            },
                            onUnpinTile = { id ->
                                if (id.startsWith("phone_folder_")) ungroupFolder(id)
                                else {
                                    saveOrder(order.filterNot { it == id })
                                    editing = null
                                }
                            },
                            onMergeTile = { fromId, toId ->
                                if (fromId in order && toId in order && fromId != toId) {
                                    val folderId = PhoneFolderStore.createOrAdd(
                                        context, mode, fromId, toId,
                                    )
                                    if (folderId != null) {
                                        val result = order.filterNot { it == fromId }.toMutableList()
                                        if (toId != folderId) {
                                            val index = result.indexOf(toId)
                                            if (index >= 0) result[index] = folderId
                                        }
                                        saveOrder(result)
                                        folderRevision++
                                        editing = null
                                    }
                                }
                            },
                            onMoveTile = { fromId, toId ->
                                val fromIndex = order.indexOf(fromId)
                                val toIndex = order.indexOf(toId)
                                if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                                    val nextOrder = order.toMutableList()
                                    val tileId = nextOrder.removeAt(fromIndex)
                                    val insertion = nextOrder.indexOf(toId).coerceAtLeast(0)
                                    nextOrder.add(insertion, tileId)
                                    saveOrder(nextOrder)
                                }
                            },
                            onClick = { tile, bounds ->
                                if (editing != null) editing = null
                                else if (tile.id == "phone_app_$MOSAIC_SETTINGS_ID" ||
                                    tile.id == "tile_settings") onOpenSettings()
                                else if (tile.id.startsWith("phone_folder_")) {
                                    openFolderId = tile.id
                                    openFolderBounds = bounds
                                } else onLaunch(tile, bounds, LaunchOrigin.START)
                            },
                            onLongClick = { editing = it },
                        )
                        fun bannerMotion(): PhoneStartMotionFrame {
                            val widthCss = (paneBounds.width / densityScale)
                                .takeIf { it > 0f } ?: (surfaceBounds.width / densityScale)
                            val leftCss = if (homeBannerBounds.width > 0f) {
                                (homeBannerBounds.left - paneBounds.left) / densityScale
                            } else widthCss - 54f
                            return PhoneStartChoreography.sample(
                                mode = mode,
                                exiting = isLaunching,
                                elapsedMillis = sourceMotionMillis(),
                                mobileRowFraction = 0f,
                                animationIndex = 0f,
                                viewportHeightCssPx = viewportHeightCssPx,
                                viewportWidthCssPx = widthCss,
                                tileLeftCssPx = leftCss,
                                resumeUsesBackMotion = motionUsesBack,
                                entryStaggerMultiplier =
                                    PhoneStartChoreography.NATIVE_FORWARD_STAGGER_MULTIPLIER,
                                tileWidthCssPx = (homeBannerBounds.width / densityScale)
                                    .takeIf { it > 0f } ?: 54f,
                            )
                        }
                        Spacer(Modifier.height(30.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                                .onGloballyPositioned { homeBannerBounds = it.boundsInWindow() }
                                .then(if (mode == LauncherUiMode.PHONE_8) {
                                    Modifier.graphicsLayer {
                                        alpha = bannerMotion().alpha
                                        rotationY = 0f
                                        translationX = 0f
                                        translationY = 0f
                                        scaleX = 1f
                                        scaleY = 1f
                                    }.drawWithContent {
                                        val frame = bannerMotion()
                                        val leftCss = (homeBannerBounds.left - paneBounds.left) / densityScale
                                        val topCss = (homeBannerBounds.top - paneBounds.top) / densityScale
                                        val widthCss = size.width / densityScale
                                        val heightCss = size.height / densityScale
                                        val pageWidthCss = paneBounds.width / densityScale
                                        val pageHeightCss = paneBounds.height / densityScale
                                        val matrix = projectPlaneMatrix(
                                            size.width, size.height, densityScale, leftCss, topCss,
                                        ) { xCss, yCss ->
                                            PhoneStartChoreography.projectPlanePoint(
                                                localXPx = xCss,
                                                localYPx = yCss,
                                                elementLeftCssPx = leftCss,
                                                elementTopCssPx = topCss,
                                                elementWidthCssPx = widthCss,
                                                elementHeightCssPx = heightCss,
                                                viewportWidthCssPx = pageWidthCss,
                                                viewportHeightCssPx = pageHeightCss,
                                                motion = frame,
                                            )
                                        }
                                        val canvas = drawContext.canvas.nativeCanvas
                                        val saveCount = canvas.save()
                                        canvas.concat(matrix)
                                        drawContent()
                                        canvas.restoreToCount(saveCount)
                                    }
                                } else Modifier.graphicsLayer {
                                    alpha = if (isTen) mobileWallpaperAlpha()
                                    else if (isLaunching && motionClock.value >= duration) 0f else 1f
                                }),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text("→", color = Color.White, fontSize = 34.sp,
                                modifier = Modifier.clickable(enabled = interactionEnabled && !isLaunching) { showApps = true }.padding(10.dp))
                        }
                    }
                }
                Box(
                    Modifier.fillMaxSize().zIndex(0f).graphicsLayer {
                        clip = isTen
                        // During Disco's forward Home entry, render the All Apps
                        // turn directly into viewport/world coordinates, not inside
                        // an offscreen pane clipped at x=+one viewport. Outside that
                        // short entrance, the second page stays at x=+width and
                        // both panes share the same panorama translation.
                        val homeTurnRunning = classicForwardEntranceActive &&
                            !showApps && motionClock.value < duration
                        translationX = if (homeTurnRunning) 0f
                            else paneBounds.width * (1f - panoramaProgress)
                    },
                ) {
                    // Play Disco's forward second-page entry on first launcher
                    // introduction as well as on every explicit Home return.
                    // The shared clock only restarts for lifecycle/launch events,
                    // not on a normal Start <-> All Apps panorama swipe.
                    // A forward return draws the second page into the Start viewport
                    // ONLY while Start is selected. Previously the projector stayed
                    // enabled after showApps=true, so settled app rows were pushed one
                    // full viewport to the right and the All Apps screen looked black.
                    val animateHomeAppsPage = classicForwardEntranceActive && !showApps
                    val snapAppsPageAway = mode == LauncherUiMode.PHONE_8 &&
                        suppressReturnPaneTransition && motionUsesBack
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer {
                            val listExitEnd = when {
                                isClassicPhone -> duration
                                isTen -> MobileStartMotion.EXIT_TOTAL_MS
                                else -> PhoneMotionTimeline.totalMillis(mode, listPhase, 6)
                            }
                            if (animateHomeAppsPage) {
                                val page = PhoneStartChoreography.sampleAppsPageEntry(
                                    elapsedMillis = sourceMotionMillis(),
                                    viewportWidthCssPx = surfaceBounds.width / densityScale,
                                )
                                alpha = page.alpha
                                rotationY = 0f
                                translationX = 0f
                                transformOrigin = TransformOrigin.Center
                                cameraDistance =
                                    PhoneStartChoreography.CLASSIC_APPS_PAGE_PERSPECTIVE_CSS_PX * densityScale
                            } else if (snapAppsPageAway) {
                                // Back-return leaves the second pane parked offscreen.
                                alpha = 0f
                                rotationY = 0f
                                translationX = 0f
                                transformOrigin = TransformOrigin.Center
                                cameraDistance =
                                    PhoneStartChoreography.CLASSIC_APPS_PAGE_PERSPECTIVE_CSS_PX * densityScale
                            } else {
                                alpha = if (isLaunching && sourceMotionMillis() >= listExitEnd) 0f else 1f
                                rotationY = 0f
                                translationX = 0f
                                transformOrigin = TransformOrigin.Center
                                cameraDistance = 1000f * densityScale
                            }
                        }
                        .then(if (animateHomeAppsPage) Modifier.drawWithContent {
                            val viewportWidthCssPx = size.width / densityScale
                            val viewportHeightCssPx = size.height / densityScale
                            val page = PhoneStartChoreography.sampleAppsPageEntry(
                                elapsedMillis = sourceMotionMillis(),
                                viewportWidthCssPx = viewportWidthCssPx,
                            )
                            val pageWidthPx = size.width
                            val pageHeightPx = size.height
                            val source = floatArrayOf(
                                0f, 0f,
                                pageWidthPx, 0f,
                                pageWidthPx, pageHeightPx,
                                0f, pageHeightPx,
                            )
                            fun projected(xPx: Float, yPx: Float): PhoneProjectedPoint =
                                PhoneStartChoreography.projectAppsPagePoint(
                                    pageLocalXPx = xPx / densityScale,
                                    pageLocalYPx = yPx / densityScale,
                                    viewportWidthCssPx = viewportWidthCssPx,
                                    viewportHeightCssPx = viewportHeightCssPx,
                                    rotationYDegrees = page.rotationY,
                                )
                            val corners = listOf(
                                projected(0f, 0f),
                                projected(pageWidthPx, 0f),
                                projected(pageWidthPx, pageHeightPx),
                                projected(0f, pageHeightPx),
                            )
                            val destination = FloatArray(8)
                            corners.forEachIndexed { index, point ->
                                // The whole Apps turn is projected in viewport/world
                                // coordinates while the outer page is at x=0; the
                                // normal +viewport pane translation is not applied
                                // concurrently with this projected transition.
                                destination[index * 2] = point.xCssPx * densityScale
                                destination[index * 2 + 1] = point.yCssPx * densityScale
                            }
                            val perspective = PlatformMatrix()
                            if (!perspective.setPolyToPoly(source, 0, destination, 0, 4)) {
                                drawContent()
                                return@drawWithContent
                            }
                            val canvas = drawContext.canvas.nativeCanvas
                            val saveCount = canvas.save()
                            canvas.concat(perspective)
                            drawContent()
                            canvas.restoreToCount(saveCount)
                        } else Modifier)
                        .then(if (suppressReturnPaneTransition && mode == LauncherUiMode.PHONE_8) {
                            Modifier.testTag("wp81-home-return-app-page")
                        } else Modifier)) {
                        Column(
                            Modifier.fillMaxSize().padding(
                                start = appListContentLeftCssPx.dp,
                                end = if (isClassicPhone) (18f * classicUnit).dp else 14.dp,
                                top = if (isClassicPhone) (24f * classicUnit).dp else 0.dp,
                                bottom = if (isClassicPhone) (24f * classicUnit).dp else 0.dp,
                            ),
                        ) {
                        if (isTen || phone8SearchVisible) {
                        var toolbarBounds by remember { mutableStateOf(Rect.Zero) }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)
                            .testTag("phone-apps-toolbar")
                            .onGloballyPositioned {
                                toolbarBounds = Rect(it.positionInWindow(),
                                    Size(it.size.width.toFloat(), it.size.height.toFloat()))
                            }
                            .graphicsLayer {
                                if (isTen && showApps && toolbarBounds.width > 0f) {
                                    val motion = MobileStartMotion.sample(
                                        exiting = isLaunching,
                                        returningFromApp = motionUsesBack && !isLaunching,
                                        elapsedMillis = if (isLaunching || motionUsesBack) sourceMotionMillis()
                                            else MobileStartMotion.ENTRY_TOTAL_MS,
                                        row = 0f,
                                        centerX = toolbarBounds.center.x,
                                        centerY = toolbarBounds.center.y,
                                        viewportCenterX = surfaceBounds.center.x,
                                        viewportCenterY = surfaceBounds.center.y,
                                    )
                                    alpha = motion.alpha
                                    translationX = motion.translationXPx
                                    translationY = motion.translationYPx
                                    scaleX = motion.scale
                                    scaleY = motion.scale
                                    transformOrigin = TransformOrigin.Center
                                }
                            }, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = search, onValueChange = { search = it },
                                enabled = interactionEnabled && !isLaunching,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White, fontSize = 16.sp,
                                    fontWeight = FontWeight.Light,
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                                    .border(1.dp, Color(0xFF787878))
                                    .background(Color.Black.copy(alpha = 0.18f))
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                                decorationBox = { inner ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f)) {
                                            if (search.isBlank()) Text(
                                                if (isTen) "Search" else "Search apps",
                                                color = Color(0xFFADADAD), fontSize = 16.sp,
                                                fontWeight = FontWeight.Light,
                                            )
                                            inner()
                                        }
                                        PhoneNavGlyph(PhoneNavKey.SEARCH, Modifier.size(19.dp))
                                    }
                                },
                            )
                        }
                        }
                        Spacer(Modifier.height(if (isClassicPhone) 0.dp else 12.dp))
                        val filtered = remember(visibleApps, search) {
                            visibleApps.filter { it.label.contains(search, ignoreCase = true) }
                        }
                        val groups = remember(
                            filtered,
                            recentlyInstalledApps,
                            showRecentlyInstalledPhoneApps,
                            search,
                        ) {
                            val alphabetical = filtered.groupBy {
                                it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter)?.toString() ?: "#"
                            }.toSortedMap()
                            if (showRecentlyInstalledPhoneApps && search.isBlank() &&
                                recentlyInstalledApps.isNotEmpty()
                            ) {
                                buildMap {
                                    put("Recently installed", recentlyInstalledApps)
                                    alphabetical.forEach { (letter, apps) -> put(letter, apps) }
                                }
                            } else alphabetical
                        }
                        val positions = remember(groups) {
                            var index = 0
                            buildMap {
                                groups.forEach { (letter, apps) ->
                                    put(letter, index)
                                    index += 1 + apps.size
                                }
                            }
                        }
                        val visibleListIndices = appsState.layoutInfo.visibleItemsInfo.map { it.index }
                        val visibleListTops = appsState.layoutInfo.visibleItemsInfo.map { it.offset.toFloat() }
                        val firstVisibleListTop = visibleListTops.minOrNull() ?: 0f
                        val lastVisibleListTop = visibleListTops.maxOrNull() ?: firstVisibleListTop
                        val firstVisibleListIndex = visibleListIndices.minOrNull()
                            ?: appsState.firstVisibleItemIndex
                        val lastVisibleListIndex = visibleListIndices.maxOrNull()
                            ?: firstVisibleListIndex
                        fun mobileAppsMotion(
                            bounds: Rect,
                            exiting: Boolean,
                            selected: Boolean = false,
                        ): PhoneStartMotionFrame {
                            val rowFraction = MobileStartMotion.rowFraction(
                                bounds.top - appsListBounds.top,
                                firstVisibleListTop,
                                lastVisibleListTop,
                            )
                            val backReturn = !exiting && motionUsesBack
                            val motion = MobileStartMotion.sample(
                                exiting = exiting,
                                elapsedMillis = when {
                                    exiting -> sourceMotionMillis()
                                    backReturn -> sourceMotionMillis()
                                    else -> MobileStartMotion.ENTRY_TOTAL_MS
                                },
                                row = rowFraction,
                                centerX = bounds.center.x,
                                centerY = bounds.center.y,
                                viewportCenterX = surfaceBounds.center.x,
                                viewportCenterY = surfaceBounds.center.y,
                                selected = selected,
                                returningFromApp = backReturn,
                            )
                            return PhoneStartMotionFrame(
                                alpha = motion.alpha,
                                rotationY = 0f,
                                translationXPx = motion.translationXPx,
                                translationYPx = motion.translationYPx,
                                translationZPx = 0f,
                                scale = motion.scale,
                                pivotX = motion.pivotX,
                            )
                        }
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        LazyColumn(state = appsState, modifier = Modifier.fillMaxSize()
                            .onGloballyPositioned {
                                appsListBounds = Rect(
                                    it.positionInWindow(),
                                    Size(it.size.width.toFloat(), it.size.height.toFloat()),
                                )
                            }
                            .elasticVerticalScroll(),
                            userScrollEnabled = interactionEnabled && !isLaunching) {
                            groups.forEach { (letter, groupApps) ->
                                item(key = "letter_$letter") {
                                    var headerBounds by remember(letter) { mutableStateOf(Rect.Zero) }
                                    fun headerMotion(): PhoneStartMotionFrame =
                                        classicAppsMotion(
                                            animationIndex = PhoneStartChoreography.appListAnimationIndex(
                                                positions[letter] ?: 0,
                                                firstVisibleListIndex, lastVisibleListIndex,
                                            ),
                                            letter = true,
                                            tileLeftCssPx = appListContentLeftCssPx,
                                            tileWidthCssPx = appListContentWidthCssPx,
                                        )
                                    val letterModifier = Modifier.fillMaxWidth()
                                        .then(if (isClassicPhone) Modifier.height((73f * classicUnit).dp)
                                            .padding(bottom = (11f * classicUnit).dp) else Modifier)
                                        .then(if (mode == LauncherUiMode.PHONE_8) {
                                            Modifier.onGloballyPositioned { headerBounds = it.boundsInWindow() }
                                                .drawWithContent {
                                                    if (!classicAppsMotionActive || headerBounds.width <= 0f) {
                                                        drawContent()
                                                    } else {
                                                        val canvas = drawContext.canvas.nativeCanvas
                                                        val saveCount = canvas.save()
                                                        canvas.concat(appListElementProjectionMatrix(
                                                            size.width, size.height, densityScale,
                                                            headerBounds, paneBounds, headerMotion(), classicAppsCameraDistance(),
                                                        ))
                                                        drawContent()
                                                        canvas.restoreToCount(saveCount)
                                                    }
                                                }
                                                .graphicsLayer { alpha = if (classicAppsMotionActive) headerMotion().alpha else 1f }
                                        } else if (isTen) {
                                            Modifier.onGloballyPositioned {
                                                headerBounds = Rect(it.positionInWindow(),
                                                    Size(it.size.width.toFloat(), it.size.height.toFloat()))
                                            }
                                                .graphicsLayer {
                                                    if (showApps && headerBounds.width > 0f) {
                                                        val motion = mobileAppsMotion(headerBounds, isLaunching)
                                                        alpha = motion.alpha
                                                        translationX = motion.translationXPx
                                                        translationY = motion.translationYPx
                                                        scaleX = motion.scale
                                                        scaleY = motion.scale
                                                        transformOrigin = TransformOrigin(motion.pivotX, .5f)
                                                    }
                                                }
                                        } else Modifier).clickable(
                                            enabled = interactionEnabled && !isLaunching &&
                                                (letter == "#" || (letter.length == 1 && letter[0].isLetter())),
                                        ) { alphabetOpen = true }
                                    if (isClassicPhone && letter.length == 1) {
                                        Row(letterModifier, verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier.size((62f * classicUnit).dp)
                                                    .border((2f * classicUnit).dp, accent),
                                                contentAlignment = Alignment.TopStart,
                                            ) {
                                                Text(letter.lowercase(), color = accent, fontSize = (40f * classicUnit).sp,
                                                    fontWeight = FontWeight.Light,
                                                    modifier = Modifier.padding(start = (7f * classicUnit).dp))
                                            }
                                        }
                                    } else {
                                        Text(letter, color = accent,
                                            fontSize = if (letter == "Recently installed") 20.sp else 29.sp,
                                            fontWeight = FontWeight.Light,
                                            modifier = letterModifier.padding(
                                                vertical = if (isClassicPhone) 0.dp else 10.dp,
                                                horizontal = if (isClassicPhone) 0.dp else 2.dp,
                                            ))
                                    }
                                }
                                itemsIndexed(groupApps, key = { _, app -> app.packageName }) { appIndex, app ->
                                    val icon = rememberAppIcon(appsRepository, app.packageName)
                                    var bounds by remember { mutableStateOf(Rect.Zero) }
                                    var mobileBounds by remember { mutableStateOf(Rect.Zero) }
                                    val listIndex = (positions[letter] ?: 0) + appIndex + 1
                                    val animationIndex = PhoneStartChoreography.appListAnimationIndex(
                                        listIndex, firstVisibleListIndex, lastVisibleListIndex,
                                    )
                                    val ordinal = (listIndex - appsState.firstVisibleItemIndex).coerceIn(0, 6)
                                    fun appRowMotion(): PhoneStartMotionFrame =
                                        classicAppsMotion(
                                            animationIndex = animationIndex,
                                            selected = (if (isLaunching) exitTileId else returnTileId) ==
                                                            "phone_app_${app.packageName}",
                                            tileLeftCssPx = appListContentLeftCssPx,
                                            tileWidthCssPx = appListContentWidthCssPx,
                                        )
                                    // W10M app launch/return uses the approved shared Start zoom.
                                    // Ordinary panorama swipes keep rows settled.
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .height(if (isClassicPhone) (73f * classicUnit).dp else 60.dp)
                                            .padding(bottom = if (isClassicPhone) (11f * classicUnit).dp else 0.dp)
                                            .onGloballyPositioned {
                                                bounds = it.boundsInWindow()
                                                mobileBounds = Rect(
                                                    it.positionInWindow(),
                                                    Size(it.size.width.toFloat(), it.size.height.toFloat()),
                                                )
                                            }
                                            .then(if (mode == LauncherUiMode.PHONE_8) {
                                                // Project the faded layer, not the drawing inside
                                                // its untransformed offscreen bounds. Otherwise
                                                // alpha < 1 clips the turning icon, then its edge
                                                // jumps out when the fade reaches full opacity.
                                                Modifier.drawWithContent {
                                                    if (!classicAppsMotionActive || bounds.width <= 0f) {
                                                        drawContent()
                                                    } else {
                                                        val canvas = drawContext.canvas.nativeCanvas
                                                        val saveCount = canvas.save()
                                                        canvas.concat(appListElementProjectionMatrix(
                                                            size.width, size.height, densityScale,
                                                            bounds, paneBounds, appRowMotion(), classicAppsCameraDistance(),
                                                        ))
                                                        drawContent()
                                                        canvas.restoreToCount(saveCount)
                                                    }
                                                }.graphicsLayer {
                                                    alpha = if (classicAppsMotionActive) appRowMotion().alpha else 1f
                                                }
                                            } else Modifier.graphicsLayer {
                                                if (isTen && showApps && mobileBounds.width > 0f) {
                                                    val motion = mobileAppsMotion(
                                                        mobileBounds,
                                                        exiting = isLaunching,
                                                        selected = (if (isLaunching) exitTileId else returnTileId) ==
                                                            "phone_app_${app.packageName}",
                                                    )
                                                    alpha = motion.alpha
                                                    translationX = motion.translationXPx
                                                    translationY = motion.translationYPx
                                                    scaleX = motion.scale
                                                    scaleY = motion.scale
                                                    transformOrigin = TransformOrigin(motion.pivotX, .5f)
                                                } else if (isLaunching) {
                                                    val rowMotion = PhoneMotionTimeline.sample(
                                                        mode, listPhase, sourceMotionMillis(), ordinal,
                                                        selectedTile = exitTileId == "phone_app_${app.packageName}",
                                                    )
                                                    alpha = rowMotion.alpha
                                                    rotationY = rowMotion.rotationY
                                                    translationY = rowMotion.offsetYPx * densityScale
                                                    scaleX = rowMotion.scale
                                                    scaleY = rowMotion.scale
                                                    transformOrigin = TransformOrigin(rowMotion.pivotX, .5f)
                                                    cameraDistance = maxOf(900f, 2f * bounds.width, 2f * bounds.height)
                                                }
                                            })
                                            .combinedClickable(
                                                enabled = interactionEnabled && !isLaunching,
                                                onClick = {
                                                    if (app.packageName == MOSAIC_SETTINGS_ID) onOpenSettings()
                                                    else onLaunch(appTile(app), bounds, LaunchOrigin.ALL_APPS)
                                                },
                                                onLongClick = { editing = "app:${app.packageName}" },
                                            )
                                            .padding(vertical = if (isClassicPhone) 0.dp else 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier
                                            .size(if (isClassicPhone) (62f * classicUnit).dp else 45.dp)
                                            .background(
                                                if (isTen) appsRepository.getCachedAppAccentColor(app.packageName)
                                                    ?.let { Color(it) } ?: accent else accent
                                            ), contentAlignment = Alignment.Center) {
                                            if (app.packageName == MOSAIC_SETTINGS_ID) {
                                                com.flivoro.tile8auncher.ui.components.MetroIcon(
                                                    glyph = "settings", size = 27.dp)
                                            } else if (icon != null) androidx.compose.foundation.Image(
                                                bitmap = icon, contentDescription = null,
                                                modifier = Modifier.size(if (isClassicPhone) (42f * classicUnit).dp else 28.dp))
                                            else Text(app.label.take(1), color = Color.White,
                                                fontSize = if (isClassicPhone) (34f * classicUnit).sp else 22.sp)
                                        }
                                        Text(app.label, color = Color.White,
                                            fontSize = if (isClassicPhone) (30f * classicUnit).sp else 18.sp,
                                            fontWeight = if (isClassicPhone) FontWeight.Light else FontWeight.Normal,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                                .padding(
                                                    start = if (isClassicPhone) (18f * classicUnit).dp else 14.dp,
                                                    top = 0.dp,
                                                    bottom = 0.dp,
                                                ))
                                    }
                                }
                            }
                        }
                        if (alphabetOpen) {
                            Dialog(
                                onDismissRequest = { alphabetOpen = false },
                                properties = androidx.compose.ui.window.DialogProperties(
                                    usePlatformDefaultWidth = false,
                                ),
                            ) {
                                Box(
                                    Modifier.fillMaxSize().background(Color.Black)
                                        .clickable { alphabetOpen = false },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val letters = listOf(
                                        "#", "A", "B", "C", "D", "E", "F", "G",
                                        "H", "I", "J", "K", "L", "M", "N",
                                        "O", "P", "Q", "R", "S", "T", "U",
                                        "V", "W", "X", "Y", "Z",
                                    )
                                    val count = if (isTen) 5 else 6
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        letters.chunked(count).forEach { chunk ->
                                            Row(Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                chunk.forEach { letter ->
                                                    val available = letter in positions
                                                    Box(
                                                        Modifier.weight(1f).height(48.dp)
                                                            .background(if (available) accent else Color(0xFF292929))
                                                            .clickable(enabled = available) {
                                                                alphabetOpen = false
                                                                scope.launch {
                                                                    appsState.scrollToItem(positions.getValue(letter))
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(if (isTen) letter else letter.lowercase(),
                                                            color = if (available) Color.White else Color.Gray,
                                                            fontSize = 24.sp, fontWeight = FontWeight.Light)
                                                    }
                                                }
                                                repeat(count - chunk.size) { Spacer(Modifier.weight(1f)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (isClassicPhone && !phone8SearchVisible) {
                        var searchIconBounds by remember { mutableStateOf(Rect.Zero) }
                        fun searchIconMotion(): PhoneStartMotionFrame =
                            classicAppsMotion(
                                animationIndex = 1f,
                                tileLeftCssPx = appListSearchLeftCssPx,
                                tileWidthCssPx = appListSearchSizeCssPx,
                            )
                        Box(
                            Modifier.offset(x = appListSearchLeftCssPx.dp, y = (24f * classicUnit).dp)
                                .size(appListSearchSizeCssPx.dp)
                                .onGloballyPositioned { searchIconBounds = it.boundsInWindow() }
                                .drawWithContent {
                                    if (!classicAppsMotionActive || searchIconBounds.width <= 0f) {
                                        drawContent()
                                    } else {
                                        val canvas = drawContext.canvas.nativeCanvas
                                        val saveCount = canvas.save()
                                        canvas.concat(appListElementProjectionMatrix(
                                            size.width, size.height, densityScale,
                                            searchIconBounds, paneBounds, searchIconMotion(), classicAppsCameraDistance(),
                                        ))
                                        drawContent()
                                        canvas.restoreToCount(saveCount)
                                    }
                                }
                                .graphicsLayer {
                                    alpha = appSearchIconAlpha.value *
                                        (if (classicAppsMotionActive) searchIconMotion().alpha else 1f)
                                }
                                .border((2.5f * classicUnit).dp, Color.White, CircleShape)
                                .testTag("phone-app-search-toggle")
                                .clickable(enabled = interactionEnabled && !isLaunching) {
                                    phone8SearchVisible = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.foundation.Canvas(Modifier.size((23f * classicUnit).dp)) {
                                val stroke = (2.5f * classicUnit).dp.toPx()
                                drawCircle(Color.White, radius = size.minDimension * .25f,
                                    center = androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .36f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                                drawLine(Color.White,
                                    androidx.compose.ui.geometry.Offset(size.width * .44f, size.height * .56f),
                                    androidx.compose.ui.geometry.Offset(size.width * .12f, size.height * .88f),
                                    strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            }
                        }
                    }
                }
            }
            }
            Row(Modifier.fillMaxWidth().height(48.dp).background(Color.Black),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.testTag("phone-bottom-back")
                    .clickable(enabled = interactionEnabled && !isLaunching) {
                        when {
                            alphabetOpen -> alphabetOpen = false
                            editing != null -> editing = null
                            showApps -> showApps = false
                        }
                    }.padding(horizontal = 26.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center) {
                    PhoneNavGlyph(PhoneNavKey.BACK, Modifier.size(27.dp))
                }
                Box(Modifier.testTag("phone-bottom-home")
                    .clickable(enabled = interactionEnabled && !isLaunching) {
                        if (isClassicPhone && showApps) {
                            classicHomeEntrance = entranceRequest
                            classicHomeTap++
                        }
                        editing = null
                        showApps = false
                        actionCenterOpen = false
                    }.padding(horizontal = 26.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center) {
                    PhoneNavGlyph(PhoneNavKey.START, Modifier.size(24.dp))
                }
                Box(Modifier.testTag("phone-bottom-search")
                    .clickable(enabled = interactionEnabled && !isLaunching) { showApps = true }
                    .padding(horizontal = 26.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center) {
                    PhoneNavGlyph(PhoneNavKey.SEARCH, Modifier.size(25.dp))
                }
            }
        }

        if (!isTen) {
            // Swipe just below Android's status indicators to open the WP8.1 overlay;
            // Android's real notification shade remains system-owned.
            Box(Modifier.align(Alignment.TopCenter).statusBarsPadding()
                .fillMaxWidth().height(20.dp)
                .testTag("phone-action-center-trigger")
                .clickable(enabled = interactionEnabled && !isLaunching) {
                    actionCenterOpen = true
                }
                .pointerInput(interactionEnabled) {
                    if (interactionEnabled) {
                        var distance = 0f
                        detectVerticalDragGestures(
                            onDragStart = { distance = 0f },
                            onVerticalDrag = { change, dy ->
                                distance += dy
                                if (distance > 38f) {
                                    actionCenterOpen = true
                                    change.consume()
                                }
                            },
                            onDragEnd = { distance = 0f },
                            onDragCancel = { distance = 0f },
                        )
                    }
                })
        }

        if (actionCenterOpen) {
            var quickActionsExpanded by remember(mode) { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().statusBarsPadding()
                .background(Color.Black).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isTen) "Notifications" else "ACTION CENTER",
                        color = Color.White, fontSize = if (isTen) 18.sp else 21.sp,
                        fontWeight = FontWeight.Light)
                    Text(if (isTen) (if (quickActionsExpanded) "Collapse  ⌃" else "Expand  ⌄")
                        else "Close  ⌃",
                        color = accent, fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            if (isTen) quickActionsExpanded = !quickActionsExpanded
                            else actionCenterOpen = false
                        }.padding(8.dp))
                }
                Spacer(Modifier.height(14.dp))
                val actions = listOf(
                    "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
                    "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
                    "Airplane mode" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
                    "All settings" to "",
                    "Display" to Settings.ACTION_DISPLAY_SETTINGS,
                    "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                    "Sound" to Settings.ACTION_SOUND_SETTINGS,
                    "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
                )
                val visibleActions = if (isTen && quickActionsExpanded) actions else actions.take(4)
                visibleActions.chunked(4).forEach { group ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        group.forEach { (title, intentAction) ->
                            Box(Modifier.weight(1f).height(if (isTen) 72.dp else 58.dp)
                                .background(Color(0xFF303030))
                                .clickable {
                                    actionCenterOpen = false
                                    if (intentAction.isEmpty()) onOpenSettings()
                                    else runCatching { context.startActivity(Intent(intentAction)) }
                                }.padding(5.dp), contentAlignment = Alignment.Center) {
                                Text(title, color = Color.White,
                                    fontSize = if (isTen) 11.sp else 10.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(if (isTen) "Notifications" else "notifications",
                    color = accent, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                val activeNotifications = LiveTileNotificationStore.current()
                    .filter { it.packageName !in hidden }
                    .take(8)
                if (!LiveTileRuntime.hasNotificationAccess(context)) {
                    Text("Enable notification access to see Android messages here.",
                        color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        }.padding(vertical = 16.dp))
                } else if (activeNotifications.isEmpty()) {
                    Text("No new notifications", color = Color.LightGray,
                        fontSize = 15.sp, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        activeNotifications.forEach { notice ->
                            Column(Modifier.fillMaxWidth().background(Color(0xFF333333))
                                .clickable {
                                    actionCenterOpen = false
                                    runCatching {
                                        context.packageManager.getLaunchIntentForPackage(notice.packageName)
                                            ?.let(context::startActivity)
                                    }
                                }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(notice.title.ifBlank { notice.packageName },
                                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (notice.text.isNotBlank()) {
                                    Text(notice.text, color = Color.LightGray, fontSize = 13.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Close  ⌃", color = Color.White, modifier = Modifier.clickable {
                    actionCenterOpen = false
                }.padding(8.dp))
            }
        }

    }

    openFolderId?.let { folderId ->
        phoneFolders.firstOrNull { it.id == folderId }?.let { folder ->
            PhoneFolderSurface(
                folder = folder,
                children = folder.children.mapNotNull { tileFor(it) },
                repository = appsRepository,
                onOpenChild = { childId ->
                    tileFor(childId)?.let { child ->
                        openFolderId = null
                        onLaunch(child, openFolderBounds, LaunchOrigin.START)
                    }
                },
                onRemoveChild = { childId ->
                    val remaining = PhoneFolderStore.removeChild(context, mode, folderId, childId)
                    val index = order.indexOf(folderId).coerceAtLeast(0)
                    val result = order.toMutableList()
                    if (remaining.size < 2) {
                        result.remove(folderId)
                        result.addAll(index.coerceAtMost(result.size), remaining + childId)
                        openFolderId = null
                    } else {
                        result.add((index + 1).coerceAtMost(result.size), childId)
                    }
                    saveOrder(result)
                    folderRevision++
                },
                onRename = { name ->
                    PhoneFolderStore.rename(context, mode, folderId, name)
                    folderRevision++
                },
                onUngroup = { ungroupFolder(folderId) },
                onDismiss = { openFolderId = null },
            )
        }
    }

    editing?.takeIf { showApps }?.let { id ->
        val tile = tileFor(id)
        if (tile != null) {
            AlertDialog(
                onDismissRequest = { editing = null },
                containerColor = Color(0xFF1E1E1E),
                title = { Text(tile.title, color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (id.startsWith("app:")) {
                            Text("Pin to Start", color = accent,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    saveOrder(order + id)
                                    editing = null
                                    showApps = false
                                }.padding(8.dp))
                            Text("App info", color = Color.White,
                                modifier = Modifier.clickable {
                                    tile.packageName?.let(onOpenAppInfo)
                                    editing = null
                                }.padding(8.dp))
                        } else {
                            Text("Resize", color = Color.White, fontSize = 17.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TileSize.entries.filter { it != TileSize.LARGE }
                                    .forEach { size ->
                                    Text(size.name.take(1), color = if (size == tile.size) accent else Color.White,
                                        modifier = Modifier.clickable {
                                            PhoneLayoutStore.saveSize(context, mode, id, size)
                                            phoneRevision++
                                            editing = null
                                        }.padding(8.dp))
                                }
                            }
                            Text("Tile accent", color = Color.White, fontSize = 17.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0xFF0078D7L,0xFF603CBA,0xFF008A00,0xFFE51400,0xFF00ABA9,0xFFD80073)
                                    .forEach { value ->
                                        Box(Modifier.size(28.dp).background(Color(value)).clickable {
                                            PhoneLayoutStore.saveColor(context, mode, id, value)
                                            phoneRevision++
                                            editing = null
                                        })
                                    }
                            }
                            Text("Move to beginning", color = Color.White,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    saveOrder(listOf(id) + order.filterNot { it == id })
                                    editing = null
                                }.padding(7.dp))
                            Text("Unpin from phone Start", color = Color.White,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    saveOrder(order.filterNot { it == id })
                                    editing = null
                                }.padding(7.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { editing = null }) {
                    Text("Done", color = accent)
                } },
            )
        } else editing = null
    }
}

@Composable
private fun PhoneStartGrid(
    tiles: List<TileModel>,
    columns: Int,
    scrollOffsetPx: Int,
    elapsedMillis: () -> Int,
    surfaceBounds: Rect,
    viewportBounds: Rect,
    viewportHeightPx: Float,
    interactionEnabled: Boolean,
    launchingTileId: String?,
    isLaunching: Boolean,
    resumeUsesBackMotion: Boolean,
    mode: LauncherUiMode,
    tileOpacity: Float,
    wallpaperStyle: Int,
    mobileTilePictureMode: Boolean,
    appsRepository: AppsRepository,
    editingTileId: String?,
    onResizeTile: (String) -> Unit,
    onUnpinTile: (String) -> Unit,
    onMoveTile: (String, String) -> Unit,
    onMergeTile: (String, String) -> Unit,
    folderPreviews: Map<String, List<TileModel>>,
    onClick: (TileModel, Rect) -> Unit,
    onLongClick: (String) -> Unit,
) {
    val slots = remember(tiles, columns) { packPhoneTiles(tiles.map(TileModel::size), columns) }
    var tileDragOffset by remember(editingTileId) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    // Keep clipping/visibility tied to the pane height, but use the full
    // source window height to calculate Disco's CSS stagger scale.
    val viewportHeightCssPx = (surfaceBounds.height / density)
        .takeIf { it > 0f } ?: viewportHeightPx / density
    val viewportWidthCssPx = surfaceBounds.width / density
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Native 480-unit viewport: 12-unit gutters; 24-unit four-column
        // margins, 12-unit six-column margins. Scale with viewport width,
        // not Android density. Keep W10M's established 4dp/10dp geometry.
        val gap = if (mode == LauncherUiMode.PHONE_8) maxWidth * .01875f else 4.dp
        val side = if (mode == LauncherUiMode.PHONE_8) {
            maxWidth * if (columns == 6) .025f else .05f
        } else 10.dp
        val cell = (maxWidth - side * 2 - gap * (columns - 1)) / columns
        val cellStepPx = with(LocalDensity.current) { (cell + gap).toPx() }.coerceAtLeast(1f)
        val topInsetPx = with(LocalDensity.current) { (if (mode == LauncherUiMode.MOBILE_10) 8.dp else 18.dp).toPx() }
        val visibleSlotIndices = slots.indices.filter { index ->
            val slot = slots[index]
            val top = topInsetPx + slot.row * cellStepPx - scrollOffsetPx
            val slotHeightPx = (cell * slot.rows + gap * (slot.rows - 1)).value * density
            val leftCss = (side + (cell + gap) * slot.column).value
            val widthCss = (cell * slot.columns + gap * (slot.columns - 1)).value
            top >= -slotHeightPx && top + slotHeightPx <= viewportHeightPx + slotHeightPx &&
                leftCss >= -widthCss && leftCss + widthCss <= viewportWidthCssPx + widthCss
        }
        // DiscoLauncher reverses the visible DOM order before assigning a 0..1
        // animation index, so the last (usually bottom-right) tile starts first.
        val animationOrder = visibleSlotIndices.asReversed().withIndex()
            .associate { (rank, slotIndex) -> slotIndex to rank }
        val visibleSlotCount = visibleSlotIndices.size
        val forwardWavePositions = slots.map { slot ->
            PhoneStartChoreography.nativeForwardWavePosition(
                (side + (cell + gap) * slot.column +
                    (cell * slot.columns + gap * (slot.columns - 1)) / 2).value,
                (topInsetPx - scrollOffsetPx) / density +
                    ((cell + gap) * slot.row +
                        (cell * slot.rows + gap * (slot.rows - 1)) / 2).value,
                viewportWidthCssPx,
            )
        }
        val firstForwardWavePosition = visibleSlotIndices.minOfOrNull { forwardWavePositions[it] } ?: 0f
        val visibleTops = slots.mapNotNull { slot ->
            val top = topInsetPx + slot.row * cellStepPx - scrollOffsetPx
            val slotHeight = slot.rows * cellStepPx
            if (top + slotHeight > 0f && top < viewportHeightPx) top else null
        }
        val firstVisibleTop = visibleTops.minOrNull() ?: 0f
        val lastVisibleTop = visibleTops.maxOrNull() ?: firstVisibleTop
        val heightRows = slots.maxOfOrNull { it.row + it.rows } ?: 1
        val tilePicture = (mode == LauncherUiMode.PHONE_8 || mobileTilePictureMode) &&
            (StartPersonalization.customWallpaperUri != null || wallpaperStyle > 0)
        Box(Modifier.fillMaxWidth().height((cell + gap) * heightRows)) {
            // Picture pixels are part of each tile's transformed face below.
            // The empty grid and gutters remain black without a detached mask.
            tiles.forEachIndexed { index, tile ->
                val pos = slots[index]
                val rowFraction = MobileStartMotion.rowFraction(
                    topInsetPx + pos.row * cellStepPx - scrollOffsetPx,
                    firstVisibleTop, lastVisibleTop,
                )
                key(tile.id) {
                    val editKey = if (tile.id.startsWith("phone_app_")) {
                        "app:" + tile.id.removePrefix("phone_app_")
                    } else tile.id
                    val isSelectedForEdit = editingTileId == editKey
                    PhoneTile(
                        tile = tile, mode = mode,
                        visibleRowFraction = rowFraction,
                        animationIndex = if (mode == LauncherUiMode.PHONE_8 &&
                            !isLaunching) {
                            PhoneStartChoreography.nativeForwardAnimationIndex(
                                forwardWavePositions[index], firstForwardWavePosition,
                            )
                        } else animationOrder[index]?.let { rank ->
                            PhoneStartChoreography.visibleTileAnimationIndex(rank, visibleSlotCount)
                        } ?: 0f,
                        viewportHeightCssPx = viewportHeightCssPx,
                        viewportWidthCssPx = viewportWidthCssPx,
                        tileLeftCssPx = (side + (cell + gap) * pos.column).value,
                        tileWidthCssPx = (cell * pos.columns + gap * (pos.columns - 1)).value,
                        surfaceBounds = surfaceBounds,
                        viewportBounds = viewportBounds,
                        interactionEnabled = interactionEnabled,
                        exiting = isLaunching,
                        elapsedMillis = elapsedMillis,
                        selectedTile = launchingTileId == tile.id,
                        tileOpacity = if (tilePicture &&
                            tile.tileType != com.flivoro.tile8auncher.data.TileType.PHOTOS) 0f else tileOpacity,
                        tilePictureContent = if (tilePicture &&
                            tile.tileType != com.flivoro.tile8auncher.data.TileType.PHOTOS) {
                            {
                                // Align a shared-picture coordinate space to the tile.
                                // This composable is inside PhoneTile's existing graphics
                                // transform, so tiles and wallpaper depart together.
                                Box(Modifier.fillMaxSize().clipToBounds()) {
                                    WindowsWallpaper(
                                        modifier = Modifier
                                            .offset(
                                                x = -(side + (cell + gap) * pos.column),
                                                y = -((cell + gap) * pos.row),
                                            )
                                            .requiredSize(
                                                width = maxWidth,
                                                height = (cell + gap) * heightRows,
                                            ),
                                        wallpaperStyle = wallpaperStyle,
                                        enabled = false,
                                    )
                                }
                            }
                        } else null,
                        repository = appsRepository,
                        resumeUsesBackMotion = resumeUsesBackMotion,
                        modifier = Modifier
                            .offset(x = side + (cell + gap) * pos.column,
                                    y = (cell + gap) * pos.row)
                            .size(width = cell * pos.columns + gap * (pos.columns - 1),
                                  height = cell * pos.rows + gap * (pos.rows - 1))
                            .zIndex(if (isSelectedForEdit) 4f else 0f)
                            .graphicsLayer {
                                if (isSelectedForEdit) {
                                    translationX = tileDragOffset.x
                                    translationY = tileDragOffset.y
                                }
                            },
                        onClick = { rect -> onClick(tile, rect) },
                        onLongClick = {
                            onLongClick(
                                if (tile.id.startsWith("phone_app_"))
                                    "app:" + tile.id.removePrefix("phone_app_")
                                else tile.id
                            )
                        },
                    )
                    folderPreviews[tile.id]?.let { children ->
                        PhoneFolderPreview(
                            children = children,
                            repository = appsRepository,
                            modifier = Modifier
                                .offset(x = side + (cell + gap) * pos.column,
                                    y = (cell + gap) * pos.row)
                                .size(
                                    width = cell * pos.columns + gap * (pos.columns - 1),
                                    height = cell * pos.rows + gap * (pos.rows - 1),
                                )
                                .zIndex(1f),
                        )
                    }
                    if (isSelectedForEdit) {
                        Box(Modifier
                            .offset(x = side + (cell + gap) * pos.column,
                                y = (cell + gap) * pos.row)
                            .size(width = cell * pos.columns + gap * (pos.columns - 1),
                                height = cell * pos.rows + gap * (pos.rows - 1))
                            .zIndex(5f)
                            .graphicsLayer {
                                translationX = tileDragOffset.x
                                translationY = tileDragOffset.y
                            }
                            .border(2.dp, Color.White)
                            .testTag("phone-tile-drag-handle")
                            .pointerInput(editKey, slots, cellStepPx, interactionEnabled) {
                                if (!interactionEnabled || isLaunching) return@pointerInput
                                detectDragGestures(
                                    onDrag = { change, amount ->
                                        tileDragOffset += amount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        val moved = tileDragOffset
                                        val fromSlot = slots[index]
                                        val currentCenterX = (fromSlot.column + fromSlot.columns * .5f) * cellStepPx + moved.x
                                        val currentCenterY = (fromSlot.row + fromSlot.rows * .5f) * cellStepPx + moved.y
                                        if (moved.getDistance() > cellStepPx * .22f) {
                                            val destination = slots.indices
                                                .filter { it != index }
                                                .minByOrNull { target ->
                                                    val slot = slots[target]
                                                    val targetX = (slot.column + slot.columns * .5f) * cellStepPx
                                                    val targetY = (slot.row + slot.rows * .5f) * cellStepPx
                                                    val dx = currentCenterX - targetX
                                                    val dy = currentCenterY - targetY
                                                    dx * dx + dy * dy
                                                }
                                            destination?.let { target ->
                                                val chosen = slots[target]
                                                val centerX = (chosen.column + chosen.columns * .5f) * cellStepPx
                                                val centerY = (chosen.row + chosen.rows * .5f) * cellStepPx
                                                val targetId = tiles[target].id.let { tileId ->
                                                    if (tileId.startsWith("phone_app_"))
                                                        "app:" + tileId.removePrefix("phone_app_")
                                                    else tileId
                                                }
                                                val centered = kotlin.math.abs(currentCenterX - centerX) <
                                                    chosen.columns * cellStepPx * .32f &&
                                                    kotlin.math.abs(currentCenterY - centerY) <
                                                    chosen.rows * cellStepPx * .32f
                                                if (centered) onMergeTile(editKey, targetId)
                                                else onMoveTile(editKey, targetId)
                                            }
                                        }
                                        tileDragOffset = Offset.Zero
                                    },
                                    onDragCancel = { tileDragOffset = Offset.Zero },
                                )
                            }) {
                            Box(Modifier.align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-8).dp)
                                .size(28.dp).background(Color.White, CircleShape)
                                .clickable { onUnpinTile(editKey) },
                                contentAlignment = Alignment.Center) {
                                Text("×", color = Color.Black, fontSize = 22.sp)
                            }
                            Box(Modifier.align(Alignment.BottomEnd)
                                .offset(x = 8.dp, y = 8.dp)
                                .size(28.dp).background(Color.White, CircleShape)
                                .clickable { onResizeTile(editKey) },
                                contentAlignment = Alignment.Center) {
                                Text("↘", color = Color.Black, fontSize = 19.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneTile(
    tile: TileModel,
    mode: LauncherUiMode,
    visibleRowFraction: Float,
    animationIndex: Float,
    viewportHeightCssPx: Float,
    viewportWidthCssPx: Float,
    tileLeftCssPx: Float,
    tileWidthCssPx: Float,
    resumeUsesBackMotion: Boolean,
    surfaceBounds: Rect,
    viewportBounds: Rect,
    interactionEnabled: Boolean,
    exiting: Boolean,
    elapsedMillis: () -> Int,
    selectedTile: Boolean,
    tileOpacity: Float,
    tilePictureContent: (@Composable () -> Unit)? = null,
    repository: AppsRepository,
    modifier: Modifier,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    // Read the master timebase during graphics rendering, not during composition.
    // Video analysis found lower/right WP8.1 tiles depart before upper/left tiles.
    var bounds by remember(tile.id) { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current.density
    val icon = rememberAppIcon(repository, tile.packageName)

    fun currentMotion(): PhoneStartMotionFrame = if (mode == LauncherUiMode.MOBILE_10) {
        val mobile = MobileStartMotion.sample(
            exiting = exiting,
            elapsedMillis = elapsedMillis(),
            row = visibleRowFraction,
            centerX = bounds.center.x, centerY = bounds.center.y,
            viewportCenterX = surfaceBounds.center.x, viewportCenterY = surfaceBounds.center.y,
            selected = selectedTile,
            returningFromApp = resumeUsesBackMotion && !exiting,
        )
        PhoneStartMotionFrame(
            alpha = mobile.alpha,
            rotationY = mobile.rotationY,
            translationXPx = mobile.translationXPx,
            translationYPx = mobile.translationYPx,
            translationZPx = 0f,
            scale = mobile.scale,
            pivotX = mobile.pivotX,
        )
    } else PhoneStartChoreography.sample(
        mode = mode, exiting = exiting, elapsedMillis = elapsedMillis(),
        mobileRowFraction = visibleRowFraction,
        animationIndex = animationIndex,
        viewportHeightCssPx = viewportHeightCssPx,
        viewportWidthCssPx = viewportWidthCssPx,
        tileLeftCssPx = tileLeftCssPx,
        resumeUsesBackMotion = resumeUsesBackMotion,
        tileWidthCssPx = tileWidthCssPx,
        selected = selectedTile,
        entryStaggerMultiplier =
            PhoneStartChoreography.NATIVE_FORWARD_STAGGER_MULTIPLIER,
    )

    fun elementLeftCssPx(): Float = if (bounds.width > 0f && viewportBounds.width > 0f) {
        (bounds.left - viewportBounds.left) / density
    } else tileLeftCssPx

    fun elementTopCssPx(): Float = if (bounds.height > 0f && viewportBounds.height > 0f) {
        (bounds.top - viewportBounds.top) / density
    } else 0f

    fun tileProjectionMatrix(
        widthPx: Float,
        heightPx: Float,
        motion: PhoneStartMotionFrame,
        inner: PhoneStartInnerFrame? = null,
    ): PlatformMatrix {
        val leftCss = elementLeftCssPx()
        val topCss = elementTopCssPx()
        val widthCss = widthPx / density
        val heightCss = heightPx / density
        val pageWidthCss = (viewportBounds.width / density)
            .takeIf { it > 0f } ?: viewportWidthCssPx
        val pageHeightCss = (viewportBounds.height / density)
            .takeIf { it > 0f } ?: viewportHeightCssPx
        val cssInner = inner?.copy(translationXPx = inner.translationXPx / density)
        return projectPlaneMatrix(widthPx, heightPx, density, leftCss, topCss) { xCss, yCss ->
            PhoneStartChoreography.projectPlanePoint(
                localXPx = xCss,
                localYPx = yCss,
                elementLeftCssPx = leftCss,
                elementTopCssPx = topCss,
                elementWidthCssPx = widthCss,
                elementHeightCssPx = heightCss,
                viewportWidthCssPx = pageWidthCss,
                viewportHeightCssPx = pageHeightCss,
                motion = motion,
                inner = cssInner,
                cameraDistanceCssPx = if (!exiting) {
                    PhoneStartChoreography.nativeCameraDistance(pageWidthCss)
                } else PhoneStartChoreography.CLASSIC_TILE_PERSPECTIVE_CSS_PX,
            )
        }
    }

    WindowsTileFace(
        tile = tile,
        appIcon = icon,
        backgroundAlpha = tileOpacity,
        backgroundContent = tilePictureContent,
        // Native glyphs and labels remain attached to the tile face throughout its turn.
        innerModifier = Modifier,
        modifier = modifier
            .onGloballyPositioned {
                // boundsInWindow clips partially visible tiles, moving their apparent centre.
                // The zoom anchor must use the full, untransformed tile geometry.
                bounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
            }
            .then(if (mode == LauncherUiMode.PHONE_8) {
                Modifier.graphicsLayer {
                    alpha = currentMotion().alpha
                    rotationY = 0f
                    translationX = 0f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                }.drawWithContent {
                    val motion = currentMotion()
                    val matrix = tileProjectionMatrix(size.width, size.height, motion)
                    val canvas = drawContext.canvas.nativeCanvas
                    val saveCount = canvas.save()
                    canvas.concat(matrix)
                    drawContent()
                    canvas.restoreToCount(saveCount)
                }
            } else Modifier.graphicsLayer {
                val motion = currentMotion()
                alpha = motion.alpha
                rotationY = motion.rotationY
                translationX = motion.translationXPx
                translationY = motion.translationYPx
                scaleX = motion.scale
                scaleY = motion.scale
                transformOrigin = TransformOrigin(motion.pivotX, .5f)
                cameraDistance = maxOf(900f, 2f * bounds.width, 2f * bounds.height)
            })
            .phoneToolkitTilePress(
                enabled = interactionEnabled && !exiting,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}


/** Windows Phone navigation glyphs drawn independently of Android's installed fonts. */
internal enum class PhoneNavKey { BACK, START, SEARCH }

@Composable
internal fun PhoneNavGlyph(key: PhoneNavKey, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val lineWidth = size.minDimension * .075f
        val white = Color.White
        when (key) {
            PhoneNavKey.BACK -> {
                drawLine(white, androidx.compose.ui.geometry.Offset(w * .17f, h * .51f),
                    androidx.compose.ui.geometry.Offset(w * .82f, h * .51f),
                    lineWidth, cap = androidx.compose.ui.graphics.StrokeCap.Square)
                drawLine(white, androidx.compose.ui.geometry.Offset(w * .17f, h * .51f),
                    androidx.compose.ui.geometry.Offset(w * .48f, h * .20f),
                    lineWidth, cap = androidx.compose.ui.graphics.StrokeCap.Square)
                drawLine(white, androidx.compose.ui.geometry.Offset(w * .17f, h * .51f),
                    androidx.compose.ui.geometry.Offset(w * .48f, h * .82f),
                    lineWidth, cap = androidx.compose.ui.graphics.StrokeCap.Square)
            }
            PhoneNavKey.START -> {
                val square = androidx.compose.ui.geometry.Size(w * .30f, h * .30f)
                for (x in listOf(.16f, .54f)) for (y in listOf(.16f, .54f)) {
                    drawRect(white,
                        topLeft = androidx.compose.ui.geometry.Offset(w * x, h * y),
                        size = square)
                }
            }
            PhoneNavKey.SEARCH -> {
                drawCircle(white, radius = size.minDimension * .265f,
                    center = androidx.compose.ui.geometry.Offset(w * .39f, h * .39f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = lineWidth))
                drawLine(white, androidx.compose.ui.geometry.Offset(w * .59f, h * .59f),
                    androidx.compose.ui.geometry.Offset(w * .85f, h * .85f),
                    lineWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}
