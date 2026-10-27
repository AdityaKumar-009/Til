package com.flivoro.tile8auncher.ui.phone

import android.content.Context
import android.content.Intent
import android.graphics.Matrix as PlatformMatrix
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import com.flivoro.tile8auncher.ui.components.LocalStoryLaunchScene
import com.flivoro.tile8auncher.ui.components.storyLaunchActor
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
import androidx.compose.ui.graphics.ColorFilter
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import com.flivoro.tile8auncher.R
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.SharedStartWorkspace
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.AppIconStyle
import com.flivoro.tile8auncher.features.LiveTileNotificationStore
import com.flivoro.tile8auncher.features.LiveTileRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.PhoneBackgroundStyle
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.canRequestAppUninstall
import com.flivoro.tile8auncher.features.loadEnhancedApps
import com.flivoro.tile8auncher.features.recentlyInstalledApps as selectRecentlyInstalledApps
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import com.flivoro.tile8auncher.ui.components.WindowsTileFace
import com.flivoro.tile8auncher.ui.components.highlightedAppLabel
import com.flivoro.tile8auncher.ui.components.WindowsWallpaper
import com.flivoro.tile8auncher.ui.components.elasticVerticalScroll
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

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
    reusedMatrix: PlatformMatrix? = null,
    sourceScratch: FloatArray? = null,
    destinationScratch: FloatArray? = null,
    project: (Float, Float) -> PhoneProjectedPoint,
): PlatformMatrix {
    val source = sourceScratch ?: FloatArray(8)
    source[0] = 0f; source[1] = 0f
    source[2] = widthPx; source[3] = 0f
    source[4] = widthPx; source[5] = heightPx
    source[6] = 0f; source[7] = heightPx
    val destination = destinationScratch ?: FloatArray(8)
    for (index in 0..3) {
        val x = if (index == 1 || index == 2) widthPx else 0f
        val y = if (index >= 2) heightPx else 0f
        val point = project(x / density, y / density)
        destination[index * 2] = (point.xCssPx - elementLeftCssPx) * density
        destination[index * 2 + 1] = (point.yCssPx - elementTopCssPx) * density
    }
    return (reusedMatrix ?: PlatformMatrix()).also { matrix ->
        if (!matrix.setPolyToPoly(source, 0, destination, 0, 4)) matrix.reset()
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

/**
 * Keep a viewport-sized overscan on both sides of the visible phone Start grid.
 * The underlying grid geometry never changes; only offscreen tile composition is skipped.
 */
internal fun shouldComposePhoneStartTile(
    topPx: Float, bottomPx: Float, viewportHeightPx: Float,
): Boolean {
    if (viewportHeightPx <= 0f) return true
    return bottomPx >= -viewportHeightPx && topPx <= viewportHeightPx * 2f
}

/** The per-phone layout, size and color keys are exported by Til's existing feature backup. */
internal object PhoneLayoutStore {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE,
    )
    // Phone presentation preferences are shared by WP8.1/W10M. Pin membership comes from
    // SharedStartWorkspace, also used by Desktop; tile sizing remains appropriate to each grid.
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
        if (raw == null) {
            val startingIds = defaults.map(TileModel::id)
            // Store the real first-time phone layout once. Switching between
            // WP8.1 and W10M should not replace it with a different seed.
            // Motion-test fixtures (which lack the standard Settings tile)
            // intentionally remain ephemeral.
            if (mode != LauncherUiMode.DESKTOP &&
                startingIds.contains("tile_settings") &&
                defaults.any { it.id.startsWith("app:") }) {
                saveOrder(context, mode, startingIds)
            }
            return startingIds
        }
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { json.optString(it).takeIf(String::isNotBlank) }.distinct()
        }.getOrElse { defaults.map(TileModel::id) }
    }

    fun hasSavedOrder(context: Context, mode: LauncherUiMode): Boolean {
        migrateIfNeeded(context, mode, "order")
        return prefs(context).contains(key(mode, "order"))
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

    fun hasCustomColor(context: Context, mode: LauncherUiMode, tileId: String): Boolean {
        migrateIfNeeded(context, mode, "color_$tileId")
        return prefs(context).contains(key(mode, "color_$tileId"))
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
    launchMotionMode: LauncherUiMode? = null,
    launchTiming: com.flivoro.tile8auncher.data.LaunchTiming = com.flivoro.tile8auncher.data.LaunchTiming(),
    hideOriginalTileId: String? = null,
    borrowedDesktopRetreat: () -> Float = { 0f },
    borrowedDesktopOrigin: LaunchOrigin? = null,
    onLaunch: (TileModel, Rect, LaunchOrigin) -> Unit,
    onExitFinished: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onUninstallApp: (String) -> Unit = {},
    onVisiblePageChanged: (Boolean) -> Unit = {},
    resumeUsesBackMotion: Boolean = false,
    screenOffRequest: Int = 0,
    returningTileId: String? = null,
    preparedRecentApps: List<AppInfo>? = null,
    onSharedPinsChanged: ((List<String>) -> Unit)? = null,
) {
    var classicHomeTap by remember(mode) { mutableStateOf(0) }
    var classicHomeEntrance by remember(mode) { mutableStateOf(-1) }
    val motionHomeRequest = homeRequest + classicHomeTap
    val motionUsesBack = resumeUsesBackMotion && classicHomeEntrance != entranceRequest
    val sampledMotionMillis = LocalPhoneMotionSampleMillis.current
    val storyScene = LocalStoryLaunchScene.current
    val context = LocalContext.current
    val isTen = mode == LauncherUiMode.MOBILE_10
    // Keep layout mode separate: only the active APP EXIT may borrow the other
    // phone's authentic curves. Home/Back and all entrance behavior stays native.
    val exitMotionMode = if (isLaunching) launchMotionMode ?: mode else mode
    val exitUsesMobile = if (isLaunching) exitMotionMode == LauncherUiMode.MOBILE_10 else isTen
    val exitUsesClassic = if (isLaunching) exitMotionMode == LauncherUiMode.PHONE_8
        else mode == LauncherUiMode.PHONE_8
    val densityScale = LocalDensity.current.density
    val accent = StartPersonalization.wallpaperAccentColor
    val catalog = remember(sections, tiles) {
        val originals = sections.flatMap(AppSection::apps)
        // Do not inject a synthetic app into the isolated Compose motion fixtures.
        val settingsEntry = if (tiles.any { it.id == "tile_settings" }) listOf(
            AppInfo(
                label = "Mosaic Settings",
                packageName = MOSAIC_SETTINGS_ID,
                activityName = "",
            ),
        ) else emptyList()
        (originals + settingsEntry).distinctBy(AppInfo::packageName)
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
        phoneStartSeed(tiles, catalog, mode, readPhoneAppRoles(context))
    }
    // Wait for the actual launcher catalogue before sealing the one-time
    // default layout. Otherwise Phone/Messages appear on some cold starts
    // and vanish on others depending on asynchronous package scanning.
    val appCatalogReady = catalog.any { it.packageName != MOSAIC_SETTINGS_ID }
    var order by remember(mode) {
        val saved = PhoneLayoutStore.order(context, mode, phoneSeed)
        mutableStateOf(if (onSharedPinsChanged == null) saved else
            SharedStartWorkspace.projectPhoneOrder(tiles, saved, PhoneFolderStore.read(context, mode)))
    }
    LaunchedEffect(mode, appCatalogReady, phoneSeed) {
        if (onSharedPinsChanged == null && appCatalogReady && !PhoneLayoutStore.hasSavedOrder(context, mode)) {
            val stable = phoneSeed.map(TileModel::id).distinct()
            if (stable.isNotEmpty()) {
                order = stable
                PhoneLayoutStore.saveOrder(context, mode, stable)
            }
        }
    }
    var editing by remember(mode) { mutableStateOf<String?>(null) }
    var deletingTileId by remember(mode) { mutableStateOf<String?>(null) }
    var appContextBounds by remember(mode) { mutableStateOf(Rect.Zero) }
    // Back returns to the previously visible page. Only Home and screen-off reset to Start;
    // entranceRequest must not recreate showApps or wipe the app drawer's scroll position.
    var showApps by rememberSaveable(mode, motionHomeRequest, screenOffRequest) {
        mutableStateOf(false)
    }
    // Record the source pane when the Back-to-launcher event begins. This must
    // NOT follow showApps as the user swipes afterward: Start must remain in
    // its settled pose during an All Apps-origin Back, even if the user swipes
    // back to Start before the shared 534ms return clock has finished.
    val mobileBackFromApps = remember(
        mode, entranceRequest, motionHomeRequest, isLaunching, motionUsesBack,
    ) { isTen && motionUsesBack && showApps }
    val startUsesBackMotion = motionUsesBack && !mobileBackFromApps
    // Disco pins with a deliberate 750ms panorama return; ordinary
    // swipes and Back retain their independently tuned timing.
    var classicPinReturn by remember(mode) { mutableStateOf(false) }
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
    val phoneFolders = remember(mode, folderRevision, tiles) {
        PhoneFolderStore.read(context, mode)
    }
    LaunchedEffect(tiles, phoneFolders) {
        if (onSharedPinsChanged != null) {
            val projected = SharedStartWorkspace.projectPhoneOrder(tiles, order, phoneFolders)
            if (projected != order) order = projected
        }
    }
    val foldersById = remember(phoneFolders) { phoneFolders.associateBy(PhoneFolder::id) }
    var openFolderId by rememberSaveable(mode) { mutableStateOf<String?>(null) }
    val densityRevision = LauncherFeatureRuntime.launcherModeRevision
    val phoneTilePictureMode = remember(mode, densityRevision) {
        LauncherFeatureStore.backgroundStyle(context, mode) ==
            PhoneBackgroundStyle.TILE_PICTURE
    }
    // Every wallpaper style, including procedural purple (0), supports tile pictures.
    // The cached procedural raster is supplied by PhoneTilePicture for style 0.
    val effectiveTilePictureMode = phoneTilePictureMode
    val columns = remember(mode, densityRevision) { LauncherFeatureStore.phoneSmallColumns(context, mode) }
    val tileOpacity = remember(mode, densityRevision) {
        if (isTen) LauncherFeatureStore.phoneTileOpacity(context) else 1f
    }
    val appsState = rememberLazyListState()
    // The sticky overlay is a sibling of the scroll pane. The latest
    // alphabet item offsets are published from the inner list composition.
    var classicStickyPositions by remember(mode) {
        mutableStateOf<Map<String, Int>>(emptyMap())
    }
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
    // Disco automatically leaves Start edit mode after 30 seconds without
    // selecting another tile. Reselecting restarts this idle timeout.
    LaunchedEffect(editing, showApps, mode) {
        if (isClassicPhone && !showApps && editing != null) {
            delay(30_000L)
            editing = null
        }
    }
    val classicAppMenuOpen = isClassicPhone && showApps &&
        editing?.startsWith("app:") == true
    val classicAppMenuDepth by animateFloatAsState(
        targetValue = if (classicAppMenuOpen) 1f else 0f,
        animationSpec = tween(
            if (classicAppMenuOpen) 500 else 350,
            easing = if (classicAppMenuOpen)
                androidx.compose.animation.core.CubicBezierEasing(.42f, 0f, 1f, 1f)
            else androidx.compose.animation.core.CubicBezierEasing(.075f, .82f, .165f, 1f),
        ),
        label = "disco-app-menu-background",
    )
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
    // Search mode in Disco moves the list from left 81 to 19 and down to 83.
    // This track is independent of the working launcher/return animations.
    val classicSearchProgress by animateFloatAsState(
        targetValue = if (isClassicPhone && phone8SearchVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (phone8SearchVisible) 400 else 100,
            easing = if (phone8SearchVisible)
                androidx.compose.animation.core.CubicBezierEasing(.5f, 0f, 0f, 1f)
            else androidx.compose.animation.core.CubicBezierEasing(.5f, 0f, 1f, 1f),
        ),
        label = "classic-search-content",
    )
    val appListContentLeftCssPx = if (isClassicPhone) 81f * classicUnit else 18f
    val appListSearchLeftCssPx = 25f * classicUnit
    val appListSearchSizeCssPx = 42f * classicUnit
    val appListContentWidthCssPx =
        (surfaceBounds.width / densityScale - if (isClassicPhone) 100f * classicUnit else 32f)
            .coerceAtLeast(1f)
    val listPhase = PhoneMotionPhase.FORWARD_OUT
    // Native forward entry is measured from the normal-speed reference.
    // The slowed orange-tile footage is used for geometry, not wall-clock delay.
    // One clock owns tiles, wallpaper and completion. A wall-clock delay in MainActivity
    // can expire before Compose has even presented the first animation frame.
    val duration = if (isLaunching && showApps && exitMotionMode == LauncherUiMode.PHONE_8) {
        PhoneStartChoreography.totalMillis(
            LauncherUiMode.PHONE_8, exiting = true, viewportHeightCssPx = viewportHeightCssPx,
        )
    } else if (isLaunching && showApps) {
        MobileAppsMotion.EXIT_TOTAL_MS
    } else if (isLaunching) {
        if (exitMotionMode == LauncherUiMode.MOBILE_10) MobileStartMotion.EXIT_TOTAL_MS
        else PhoneStartChoreography.totalMillis(
            LauncherUiMode.PHONE_8, exiting = true, viewportHeightCssPx = viewportHeightCssPx,
        )
    } else if (isTen && motionUsesBack && !isLaunching) {
        if (showApps) MobileAppsMotion.BACK_TOTAL_MS else MobileStartMotion.BACK_TOTAL_MS
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
    fun sourceMotionMillis(): Int = if (storyScene != null) 2000
        else sampledMotionMillis?.invoke() ?: motionClock.value.roundToInt()
    // Start is a stationary sibling during a return sourced from All Apps.
    // Keep its native entry/Back animation only when Start was the source.
    fun startMotionMillis(): Int = if (mobileBackFromApps && !isLaunching)
        MobileStartMotion.ENTRY_TOTAL_MS else sourceMotionMillis()
    val classicAppsReturning = isClassicPhone && showApps && motionUsesBack && !isLaunching
    val classicAppsMotionActive = showApps &&
        ((isLaunching && exitMotionMode == LauncherUiMode.PHONE_8) ||
            (!isLaunching && isClassicPhone && motionUsesBack))
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
    fun mobileWallpaperAlpha(): Float = if (isLaunching && !exitUsesMobile) 1f
    else if (showApps) MobileAppsMotion.chromeAlpha(
        exiting = isLaunching, elapsedMillis = sourceMotionMillis(),
        returningFromApp = motionUsesBack && !isLaunching,
    )
    else MobileStartMotion.wallpaperAlpha(
        exiting = isLaunching, elapsedMillis = startMotionMillis(),
        returningFromApp = startUsesBackMotion && !isLaunching,
    )
    val exitTileId = remember(isLaunching) { launchingTileId }
    val returnTileId = returningTileId.takeIf { motionUsesBack && !isLaunching }
    val latestExitFinished by rememberUpdatedState(onExitFinished)
    val latestDuration by rememberUpdatedState(duration)
    LaunchedEffect(mode, entranceRequest, motionHomeRequest, isLaunching, motionUsesBack) {
        snapshotFlow { paneBounds.width > 0f && surfaceBounds.height > 0f }.first { it }
        val nativeDuration = latestDuration
        // Preserve the authentic choreography's native millisecond samples.
        // User timing changes only how quickly the shared scene clock travels
        // through those samples; tiles, wallpaper, and app-list rows remain synced.
        val requestedDuration = if (isLaunching) launchTiming.playbackDuration(nativeDuration)
            else nativeDuration
        val selectedEasing = if (isLaunching &&
            launchTiming.curve != com.flivoro.tile8auncher.data.TimeCurve.REFERENCE)
            androidx.compose.animation.core.Easing { launchTiming.transform(it) }
        else LinearEasing
        motionClock.animateTo(nativeDuration.toFloat(),
            tween(requestedDuration, easing = selectedEasing))
        classicForwardEntranceActive = false
        if (isLaunching) {
            // Let the completed pose be submitted before the external Activity is requested.
            withFrameNanos { }
            latestExitFinished()
        }
    }
    val hidden = LauncherFeatureStore.hiddenPackages(context) + LauncherFeatureStore.privatePackages(context)
    val visibleApps = remember(catalog, hidden) {
        phoneAppsInDisplayOrder(catalog.filter { it.packageName !in hidden })
    }
    val showRecentlyInstalledPhoneApps = remember(mode, densityRevision) {
        mode == LauncherUiMode.MOBILE_10 &&
            LauncherFeatureStore.showRecentlyInstalledPhoneApps(context)
    }
    val recentlyInstalledApps by produceState(
        initialValue = preparedRecentApps ?: emptyList<AppInfo>(),
        visibleApps,
        showRecentlyInstalledPhoneApps,
        preparedRecentApps,
    ) {
        value = if (!showRecentlyInstalledPhoneApps) emptyList()
        else if (preparedRecentApps != null) {
            val allowed = visibleApps.map(AppInfo::packageName).toSet()
            preparedRecentApps.filter { it.packageName in allowed }
        } else withContext(Dispatchers.IO) {
            selectRecentlyInstalledApps(
                loadEnhancedApps(context.applicationContext, visibleApps),
            ).map { it.app }
        }
    }

    val systemAppCache = remember { mutableMapOf<String, Boolean>() }
    fun isPlatformApp(packageName: String): Boolean =
        systemAppCache.getOrPut(packageName) {
            runCatching {
                val flags = context.packageManager.getApplicationInfo(packageName, 0).flags
                flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or
                    android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }.getOrDefault(false)
        }

    val appRoles = remember(mode, LauncherFeatureRuntime.iconsRevision) { readPhoneAppRoles(context) }
    fun appTile(app: AppInfo): TileModel = TileModel(
        id = "phone_app_${app.packageName}",
        title = app.label,
        packageName = app.packageName,
        activityName = app.activityName,
        // Match All Apps' resolved icon background for pinned apps, whether
        // the package is user-installed or a system app. Only the default
        // Phone/Messages Metro launchers intentionally follow the theme accent.
        colorValue = when {
            appRoles.glyph(app.packageName, app.label) != null ->
                StartPersonalization.wallpaperAccentArgb
            else -> appsRepository.getDiscoTileAccentColor(app.packageName)
                ?: StartPersonalization.wallpaperAccentArgb
        },
        tileType = if (app.packageName == MOSAIC_SETTINGS_ID)
            com.flivoro.tile8auncher.data.TileType.SETTINGS
        else com.flivoro.tile8auncher.data.TileType.APP,
        iconGlyph = if (app.packageName == MOSAIC_SETTINGS_ID) "settings" else "",
    )

    fun tileFor(id: String): TileModel? {
        val source = desktopById[id].takeUnless { id.startsWith("app:") } ?: when {
            id.startsWith("app:") -> byPackage[id.removePrefix("app:")]?.let(::appTile)
            id.startsWith("phone_folder_") -> foldersById[id]?.let { folder ->
                TileModel(
                    id = folder.id,
                    title = folder.name,
                    size = TileSize.MEDIUM,
                    colorValue = StartPersonalization.wallpaperAccentArgb,
                    iconGlyph = "folder",
                )
            }
            else -> null
        }
        return source?.copy(
            size = PhoneLayoutStore.size(context, mode, source),
            // WP8.1 and W10M are skins of the same pinned workspace:
            // default Metro tiles follow the accent in both modes, while
            // app-brand colors and user-picked colors remain untouched.
            colorValue = if (usesPhoneThemeAccent(
                source.id, source.title,
                PhoneLayoutStore.hasCustomColor(context, mode, source.id),
            )) StartPersonalization.wallpaperAccentArgb
            else PhoneLayoutStore.color(context, mode, source),
        )
    }

    // Accent is snapshot-backed: include it in memoization so changing the
    // selected theme recolors the Start tiles immediately without relaunch.
    val currentAccentArgb = StartPersonalization.wallpaperAccentArgb
    val phoneTiles = remember(order, tiles, catalog, phoneRevision, folderRevision,
        currentAccentArgb) {
        order.mapNotNull { id -> tileFor(id) }
    }
    val folderPreviews = remember(phoneFolders, catalog, tiles, phoneRevision,
        currentAccentArgb) {
        phoneFolders.associate { folder ->
            folder.id to folder.children.mapNotNull { id -> tileFor(id) }
        }
    }

    fun saveOrder(next: List<String>) {
        order = next.distinct()
        PhoneLayoutStore.saveOrder(context, mode, order)
        onSharedPinsChanged?.invoke(order)
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

    val expandedFolder = openFolderId?.let { folderId ->
        phoneFolders.firstOrNull { it.id == folderId }?.let { folder ->
            PhoneFolderExpansion(
                folder = folder,
                children = folderPreviews[folderId].orEmpty(),
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
                onReorderChild = { from, to ->
                    val children = folder.children.toMutableList()
                    if (from in children && to in children && from != to) {
                        children.remove(from)
                        children.add(children.indexOf(to), from)
                        PhoneFolderStore.save(context, mode, phoneFolders.map {
                            if (it.id == folderId) it.copy(children = children) else it
                        })
                        folderRevision++
                    }
                },
                onDismiss = { openFolderId = null },
            )
        }
    }

    // Native inline folders stay attached to their Start row. If that row is
    // low on the screen, pan just enough for the newly opened child tiles to
    // remain visible; otherwise the fold-out is incorrectly offscreen.
    // Cancelling this effect on a second tap also cancels its scroll animation.
    LaunchedEffect(
        openFolderId, mode, phoneTiles, columns, paneBounds.width, paneBounds.height,
    ) {
        val folder = expandedFolder?.takeIf { mode == LauncherUiMode.MOBILE_10 }
            ?: return@LaunchedEffect
        if (paneBounds.width <= 0f || paneBounds.height <= 0f) return@LaunchedEffect
        val index = phoneTiles.indexOfFirst { it.id == folder.folder.id }
        if (index < 0) return@LaunchedEffect
        val slots = packPhoneTiles(phoneTiles.map(TileModel::size), columns)
        val insertion = phoneFolderInsertionRow(slots, index)
        val cellPx = (paneBounds.width - (20f + 4f * (columns - 1)) * densityScale) /
            columns
        val stepPx = cellPx + 4f * densityScale
        val childRows = packPhoneTiles(folder.children.map(TileModel::size), columns)
            .maxOfOrNull { it.row + it.rows } ?: 0
        val headerTopPx = insertion * stepPx + 8f * densityScale
        val expandedHeightPx = childRows * stepPx + 58f * densityScale
        withFrameNanos { } // Wait for the new inline space to enter the scroll layout.
        val target = PhoneFolderMotion.focusScrollTarget(
            startScroll.value, headerTopPx, expandedHeightPx, paneBounds.height,
        )
        if (target > startScroll.value) startScroll.animateScrollTo(
            target,
            animationSpec = tween(320,
                easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
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
        editing != null || openFolderId != null || (isClassicPhone && phone8SearchVisible))) {
        when {
            openFolderId != null -> openFolderId = null
            editing != null -> editing = null
            alphabetOpen -> alphabetOpen = false
            isClassicPhone && phone8SearchVisible -> { phone8SearchVisible = false; search = "" }
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
                durationMillis = if (isTen) 260
                    else if (isClassicPhone && classicPinReturn) 750 else 270,
                easing = androidx.compose.animation.core.Easing {
                    PhoneMotionTimeline.exponentialEaseOut6(it)
                }))
            if (!showApps) classicPinReturn = false
        }
    }
    val gestureModifier = Modifier.pointerInput(mode, showApps, interactionEnabled, isLaunching) {
        if (!interactionEnabled || isLaunching) return@pointerInput
        val velocity = VelocityTracker()
        fun releasePanoramaDrag(cancelled: Boolean) {
            val position = classicDragPosition ?: return
            // Keep the exact finger position painted until the settling
            // LaunchedEffect applies snapTo(position). This also guarantees a
            // canceled/disposed pointer handler cannot leave drag override
            // active forever, hiding all subsequent Animatable updates.
            classicReleasePosition = position
            classicDragPosition = null
            if (!cancelled) {
                val speed = velocity.calculateVelocity().x / densityScale
                showApps = if (kotlin.math.abs(speed) > 600f) speed < 0f else position >= .5f
            }
            classicReleaseRequest++
        }
        try {
            detectHorizontalDragGestures(
            onDragStart = {
                velocity.resetTracking()
                if (isClassicPhone) classicForwardEntranceActive = false
                classicDragPosition = classicPanorama.value
                // Drag uses classicDragPosition instead of Animatable.value.
                // Do not launch an asynchronous stop(): it can run AFTER the
                // release starts animateTo(), cancel that new animation and
                // leave the entire Start/Apps panorama frozen mid-swipe.
                // releasePanoramaDrag triggers the single settling effect,
                // whose snapTo() safely interrupts any previous animation.
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
        } finally {
            // pointerInput is recreated on page/interaction changes; coroutine
            // disposal is not guaranteed to invoke onDragCancel. Always finish
            // an unfinished finger drag on that path as well.
            releasePanoramaDrag(true)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)
        .testTag("phone-navigation-state")
        .semantics { stateDescription = motionHomeRequest.toString() }
        .onGloballyPositioned { surfaceBounds = it.boundsInWindow() }) {
        if (!effectiveTilePictureMode) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = (if (exitUsesMobile) mobileWallpaperAlpha() else 1f) *
                    (1f - borrowedDesktopRetreat().coerceIn(0f, 1f))
            }) {
                WindowsWallpaper(wallpaperStyle = wallpaperStyle, enabled = false, scrollOffsetPx = { 0f })
                if (isTen) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)))
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Android provides real cellular/Wi-Fi/battery status in its system status bar.
            // Do not draw invented signal icons immediately beneath those real indicators.
            if (isTen) {
                Row(Modifier.fillMaxWidth().height(22.dp).padding(end = 18.dp)
                    .storyLaunchActor("phone-status", LaunchOrigin.START, sourceCandidate = false)
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
                else classicDragPosition ?: classicReleasePosition ?: classicPanorama.value
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
                        val retreat = if (borrowedDesktopOrigin == LaunchOrigin.START)
                            borrowedDesktopRetreat().coerceIn(0f, 1f) else 0f
                        alpha = 1f - retreat
                        scaleX = 1f - .12f * retreat
                        scaleY = 1f - .12f * retreat
                    },
                ) {
                    Column(Modifier.fillMaxSize().elasticVerticalScroll(
                        classicPhonePhysics = isClassicPhone,
                        maxOverscroll = if (isClassicPhone) (187.5f * classicUnit).dp else 96.dp,
                    )
                        .verticalScroll(startScroll, enabled = interactionEnabled && !isLaunching)) {
                        Spacer(Modifier.height(if (isTen) 8.dp else 25.dp))
                        PhoneStartGrid(
                            tiles = phoneTiles,
                            columns = columns,
                            scrollOffsetPx = startScroll.value,
                            elapsedMillis = { startMotionMillis() },
                            surfaceBounds = surfaceBounds,
                            viewportBounds = paneBounds,
                            viewportHeightPx = paneBounds.height,
                            interactionEnabled = interactionEnabled,
                            launchingTileId = if (isLaunching) exitTileId
                                else returnTileId.takeUnless { mobileBackFromApps },
                            isLaunching = isLaunching,
                            resumeUsesBackMotion = startUsesBackMotion,
                            mode = mode,
                            launchMotionMode = exitMotionMode,
                            hideOriginalTileId = hideOriginalTileId,
                            tileOpacity = tileOpacity,
                            wallpaperStyle = wallpaperStyle,
                            mobileTilePictureMode = effectiveTilePictureMode,
                            appsRepository = appsRepository,
                            editingTileId = editing.takeIf { !showApps },
                            deletingTileId = deletingTileId,
                            folderPreviews = folderPreviews,
                            expandedFolder = expandedFolder,
                            onResizeTile = { id ->
                                tileFor(id)?.let { tile ->
                                    val size = if (isClassicPhone) {
                                        // Disco wTileMenu.previousSize cycles the
                                        // supported sizes in reverse, not forward.
                                        when (tile.size) {
                                            TileSize.SMALL -> TileSize.WIDE
                                            TileSize.MEDIUM -> TileSize.SMALL
                                            TileSize.WIDE -> TileSize.MEDIUM
                                            TileSize.LARGE -> TileSize.WIDE
                                        }
                                    } else when (tile.size) {
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
                                else if (isClassicPhone) {
                                    // Disco animates the inner tile down to 50%
                                    // and zero opacity for 200ms before removing it.
                                    deletingTileId = id
                                    panoramaScope.launch {
                                        delay(200)
                                        saveOrder(order.filterNot { it == id })
                                        editing = null
                                        deletingTileId = null
                                    }
                                } else {
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
                                if (editing != null) {
                                    if (isClassicPhone) {
                                        // Disco selects the newly tapped tile while staying
                                        // in home edit mode, instead of closing the editor.
                                        editing = if (tile.id.startsWith("phone_app_"))
                                            "app:" + tile.id.removePrefix("phone_app_")
                                        else tile.id
                                    } else editing = null
                                } else if (tile.id.startsWith("phone_folder_")) {
                                    openFolderId = tile.id.takeUnless { openFolderId == it }
                                } else onLaunch(tile, bounds, LaunchOrigin.START)
                            },
                            onLongClick = { editing = it },
                            onExitEdit = { editing = null },
                        )
                        fun bannerMotion(): PhoneStartMotionFrame {
                            val widthCss = (paneBounds.width / densityScale)
                                .takeIf { it > 0f } ?: (surfaceBounds.width / densityScale)
                            val leftCss = if (homeBannerBounds.width > 0f) {
                                (homeBannerBounds.left - paneBounds.left) / densityScale
                            } else widthCss - 54f
                            return PhoneStartChoreography.sample(
                                mode = exitMotionMode,
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
                            Modifier.fillMaxWidth().padding(
                                start = 20.dp,
                                end = if (isClassicPhone) (25f * classicUnit).dp else 20.dp,
                            )
                                .onGloballyPositioned { homeBannerBounds = it.boundsInWindow() }
                                .storyLaunchActor("phone-all-apps-banner", LaunchOrigin.START, sourceCandidate = false)
                                .then(if (exitUsesClassic) {
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
                                    if (isLaunching && exitUsesMobile && homeBannerBounds.width > 0f) {
                                        val m = MobileStartMotion.sample(
                                            exiting = true,
                                            elapsedMillis = sourceMotionMillis(),
                                            row = 1f,
                                            centerX = homeBannerBounds.center.x,
                                            centerY = homeBannerBounds.center.y,
                                            viewportCenterX = surfaceBounds.center.x,
                                            viewportCenterY = surfaceBounds.center.y,
                                        )
                                        alpha = m.alpha
                                        translationX = m.translationXPx
                                        translationY = m.translationYPx
                                        scaleX = m.scale
                                        scaleY = m.scale
                                    } else {
                                        alpha = if (isTen) mobileWallpaperAlpha()
                                        else if (isLaunching && motionClock.value >= duration) 0f else 1f
                                    }
                                }),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            if (isClassicPhone) {
                                // Disco's #app-page-icon: 42px outlined circle with its
                                // actual MDI U+F0054 (arrow-right), not a Unicode arrow.
                                Box(
                                    modifier = Modifier
                                        .size((42f * classicUnit).dp)
                                        .background(Color.Black, CircleShape)
                                        .border((3f * classicUnit).dp, Color.White, CircleShape)
                                        .clickable(enabled = interactionEnabled && !isLaunching) {
                                            showApps = true
                                        },
                                ) {
                                    DiscoSourceArrowGlyph(
                                        drawableId = R.drawable.disco_mdi_arrow_right,
                                        classicUnit = classicUnit,
                                    )
                                }
                            } else {
                                Text("→", color = Color.White, fontSize = 34.sp,
                                    modifier = Modifier.clickable(
                                        enabled = interactionEnabled && !isLaunching,
                                    ) { showApps = true }.padding(10.dp))
                            }
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
                        val homeTurnRunning = storyScene == null && classicForwardEntranceActive &&
                            !showApps && motionClock.value < duration
                        translationX = if (homeTurnRunning) 0f
                            else paneBounds.width * (1f - panoramaProgress)
                        // Desktop's Start launch must leave this sibling page untouched.
                        val retreat = if (borrowedDesktopOrigin == LaunchOrigin.ALL_APPS)
                            borrowedDesktopRetreat().coerceIn(0f, 1f) else 0f
                        alpha = 1f - retreat
                        scaleX = 1f - .12f * retreat
                        scaleY = 1f - .12f * retreat
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
                    val animateHomeAppsPage = storyScene == null && classicForwardEntranceActive && !showApps
                    val snapAppsPageAway = mode == LauncherUiMode.PHONE_8 &&
                        suppressReturnPaneTransition && motionUsesBack
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer {
                            val listExitEnd = when {
                                exitUsesClassic -> duration
                                exitUsesMobile -> MobileStartMotion.EXIT_TOTAL_MS
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
                                start = if (isClassicPhone) ((81f - 62f * classicSearchProgress) * classicUnit).dp else appListContentLeftCssPx.dp,
                                end = if (isClassicPhone) (18f * classicUnit).dp else 14.dp,
                                top = if (isClassicPhone) ((24f + 59f * classicSearchProgress) * classicUnit).dp else 0.dp,
                                bottom = if (isClassicPhone) (24f * classicUnit).dp else 0.dp,
                            ),
                        ) {
                        if (isTen) {
                        var toolbarBounds by remember { mutableStateOf(Rect.Zero) }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)
                            .testTag("phone-apps-toolbar")
                            .storyLaunchActor("phone-apps-toolbar", LaunchOrigin.ALL_APPS, sourceCandidate = false)
                            .onGloballyPositioned {
                                toolbarBounds = Rect(it.positionInWindow(),
                                    Size(it.size.width.toFloat(), it.size.height.toFloat()))
                            }
                            .graphicsLayer {
                                if (isTen && showApps && toolbarBounds.width > 0f) {
                                    if (isLaunching && exitUsesClassic) {
                                        val motion = PhoneStartChoreography.sampleAppListExit(
                                            elapsedMillis = sourceMotionMillis(),
                                            animationIndex = 0f,
                                            viewportHeightCssPx = viewportHeightCssPx,
                                            viewportWidthCssPx = surfaceBounds.width / densityScale,
                                            tileLeftCssPx = (toolbarBounds.left - paneBounds.left) / densityScale,
                                            tileWidthCssPx = toolbarBounds.width / densityScale,
                                        )
                                        alpha = motion.alpha
                                        rotationY = motion.rotationY
                                        translationX = motion.translationXPx * densityScale
                                        transformOrigin = TransformOrigin(motion.pivotX, .5f)
                                        cameraDistance = PhoneStartChoreography.CLASSIC_TILE_PERSPECTIVE_CSS_PX * densityScale
                                    } else {
                                        alpha = MobileAppsMotion.chromeAlpha(
                                            exiting = isLaunching,
                                            returningFromApp = motionUsesBack && !isLaunching,
                                            elapsedMillis = sourceMotionMillis(),
                                        )
                                    }
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
                            phone8SearchVisible,
                            isClassicPhone,
                        ) {
                            val alphabetical = filtered.groupBy { phoneAlphabetGroup(it.label) }
                            // Disco's order is #, A..Z, then the symbols globe.
                            val orderedAlphabetical = buildMap<String, List<AppInfo>> {
                                alphabetical["#"]?.let { put("#", it) }
                                ('A'..'Z').forEach { letter ->
                                    alphabetical[letter.toString()]?.let { put(letter.toString(), it) }
                                }
                                alphabetical["&"]?.let { put("&", it) }
                            }
                            if (isClassicPhone && phone8SearchVisible) {
                                // Search results are one continuous list, without A-Z anchors.
                                mapOf("" to filtered)
                            } else if (showRecentlyInstalledPhoneApps && search.isBlank() &&
                                recentlyInstalledApps.isNotEmpty()
                            ) {
                                buildMap {
                                    put("Recently installed", recentlyInstalledApps)
                                    orderedAlphabetical.forEach { (letter, apps) -> put(letter, apps) }
                                }
                            } else orderedAlphabetical
                        }
                        PhoneDrawerIconPrefetch(appsRepository, appsState, groups, mode)
                        val positions = remember(groups) {
                            var index = 0
                            buildMap {
                                groups.forEach { (letter, apps) ->
                                    put(letter, index)
                                    index += (if (letter.isEmpty()) 0 else 1) + apps.size
                                }
                            }
                        }
                        LaunchedEffect(positions) {
                            classicStickyPositions = positions
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
                            val motion = MobileAppsMotion.sample(
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
                            .elasticVerticalScroll(
                                classicPhonePhysics = isClassicPhone,
                                maxOverscroll = if (isClassicPhone) (187.5f * classicUnit).dp else 96.dp,
                                onTopPullRelease = if (isClassicPhone && !phone8SearchVisible) {
                                    {
                                        phone8SearchVisible = true
                                        search = ""
                                        panoramaScope.launch { appsState.animateScrollToItem(0) }
                                    }
                                } else null,
                            ),
                            userScrollEnabled = interactionEnabled && !isLaunching &&
                                !(isClassicPhone && editing?.startsWith("app:") == true)) {
                            if (isClassicPhone && phone8SearchVisible && filtered.isEmpty()) {
                                item(key = "classic-empty-search") {
                                    Text(
                                        "No results for \"$search\"",
                                        color = Color.LightGray,
                                        fontSize = (25f * classicUnit).sp,
                                        fontWeight = FontWeight.Light,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                            }
                            groups.forEach { (letter, groupApps) ->
                                if (letter.isNotEmpty()) item(key = "letter_$letter") {
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
                                        .storyLaunchActor("phone-letter-$letter", LaunchOrigin.ALL_APPS, sourceCandidate = false)
                                        .then(if (isClassicPhone) Modifier.graphicsLayer {
                                            alpha = 1f - .5f * classicAppMenuDepth
                                            scaleX = 1f - .02f * classicAppMenuDepth
                                            scaleY = 1f - .02f * classicAppMenuDepth
                                        } else Modifier)
                                        .then(if (isClassicPhone) Modifier.height((64f * classicUnit).dp)
                                            .padding(bottom = 0.dp) else Modifier)
                                        .then(if (exitUsesClassic) {
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
                                        } else if (exitUsesMobile) {
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
                                                Modifier.padding((5f * classicUnit).dp)
                                                    .size((52f * classicUnit).dp)
                                                    .border((3f * classicUnit).dp, accent),
                                                contentAlignment = Alignment.TopStart,
                                            ) {
                                                if (letter == "&") {
                                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        DiscoGlobeSymbol(accent)
                                                    }
                                                } else Text(
                                                    letter.lowercase(), color = accent,
                                                    fontSize = (30f * classicUnit).sp,
                                                    fontWeight = FontWeight.Light,
                                                    modifier = Modifier.padding(
                                                        start = (8f * classicUnit).dp,
                                                        top = (5f * classicUnit).dp,
                                                    ),
                                                )
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
                                // The same app can appear in Recently installed and
                                // its alphabet section. LazyColumn keys are global.
                                itemsIndexed(groupApps, key = { _, app -> "$letter:${app.packageName}" }) { appIndex, app ->
                                    val discoLayered = LauncherFeatureStore.appIconStyle(context) ==
                                        AppIconStyle.DISCO_LAYERED
                                    // Share WP8.1's normalized layered artwork,
                                    // optional painted backdrop and stored accent.
                                    val visual = rememberWp81TileVisual(
                                        appsRepository, app.packageName,
                                        discoLayered,
                                    )
                                    val icon = visual.icon
                                    // W10M All Apps uses the same resolved color as
                                    // its Start tile, including custom and stock tiles.
                                    val startAppTile = if (isTen)
                                        phoneTiles.firstOrNull { it.packageName == app.packageName }
                                            ?: appTile(app)
                                    else null
                                    val manuallyColored = if (isTen && startAppTile != null)
                                        PhoneLayoutStore.hasCustomColor(context, mode, startAppTile.id)
                                    else false
                                    val rowColor = if (isTen && startAppTile != null)
                                        resolvePhoneTileBackgroundArgb(
                                            startAppTile.id, startAppTile.title,
                                            startAppTile.colorValue, visual.accent,
                                            manuallyColored, discoLayered,
                                        )
                                    else null
                                    val showBrandBackground = if (isTen && startAppTile != null)
                                        canUsePhoneBrandColor(
                                            startAppTile.id, startAppTile.title,
                                            manuallyColored, discoLayered,
                                        )
                                    else discoLayered
                                    val plateSideDp = if (isClassicPhone) 52f * classicUnit else 45f
                                    val artworkSideDp = phoneAllAppsGlyphSizeDp(plateSideDp)
                                    val settingsGlyphSideDp = if (isClassicPhone) 27f
                                        else phoneAllAppsSettingsGlyphSizeDp(plateSideDp)
                                    val fallbackGlyphSp = if (isClassicPhone) 22f * classicUnit
                                        else phoneAllAppsFallbackSizeSp(plateSideDp)
                                    var bounds by remember { mutableStateOf(Rect.Zero) }
                                    var mobileBounds by remember { mutableStateOf(Rect.Zero) }
                                    var rowCoordinates by remember {
                                        mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null)
                                    }
                                    fun currentRowBounds(): Rect = rowCoordinates
                                        ?.takeIf { it.isAttached }?.let {
                                            Rect(it.positionInWindow(),
                                                Size(it.size.width.toFloat(), it.size.height.toFloat()))
                                        } ?: bounds
                                    val rowInteraction = remember(app.packageName) { MutableInteractionSource() }
                                    val rowPressed by rowInteraction.collectIsPressedAsState()
                                    val listIndex = (positions[letter] ?: 0) + appIndex +
                                        (if (letter.isEmpty()) 0 else 1)
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
                                    // All Apps uses its separate row/chrome choreography.
                                    // Ordinary panorama swipes keep rows settled.
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .height(if (isClassicPhone) (64f * classicUnit).dp else 60.dp)
                                            .storyLaunchActor("phone_app_${app.packageName}", LaunchOrigin.ALL_APPS)
                                            .padding(bottom = 0.dp)
                                            .onGloballyPositioned {
                                                rowCoordinates = it
                                                mobileBounds = Rect(
                                                    it.positionInWindow(),
                                                    Size(it.size.width.toFloat(), it.size.height.toFloat()),
                                                )
                                                bounds = mobileBounds
                                            }
                                            .then(if (if (isLaunching)
                                                exitMotionMode == LauncherUiMode.PHONE_8
                                            else mode == LauncherUiMode.PHONE_8) {
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
                                                if ((if (isLaunching) exitMotionMode == LauncherUiMode.MOBILE_10 else isTen) &&
                                                    showApps && mobileBounds.width > 0f) {
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
                                            .then(if (isClassicPhone) Modifier.graphicsLayer {
                                                val isHeld = editing == "app:${app.packageName}"
                                                // Disco's .app-menu-back-intro retreats to -5px
                                                // over 500ms, then the 20px depth pose settles.
                                                // The selected row remains a crisp foreground clone.
                                                val retreat = if (isHeld) 0f else classicAppMenuDepth
                                                alpha = 1f - .5f * retreat
                                                val distance = 1000f
                                                val scale = distance / (distance + 20f * retreat)
                                                scaleX = scale
                                                scaleY = scale
                                            } else Modifier)
                                            .graphicsLayer {
                                                if (hideOriginalTileId == "phone_app_${app.packageName}" ||
                                                    hideOriginalTileId == "app_${app.packageName}") alpha = 0f
                                            }
                                            .combinedClickable(
                                                interactionSource = rowInteraction,
                                                indication = null,
                                                enabled = interactionEnabled && !isLaunching,
                                                onClick = {
                                                    // Treat Mosaic Settings exactly like any held
                                                    // app: its real row bounds drive native exit.
                                                    val launchedApp = appTile(app)
                                                    onLaunch(
                                                        if (rowColor != null)
                                                            launchedApp.copy(colorValue = rowColor)
                                                        else launchedApp,
                                                        currentRowBounds(), LaunchOrigin.ALL_APPS,
                                                    )
                                                },
                                                onLongClick = {
                                                    editing = "app:${app.packageName}"
                                                    appContextBounds = currentRowBounds()
                                                },
                                            )
                                            .background(if (isTen && (rowPressed ||
                                                (isLaunching && exitTileId == "phone_app_${app.packageName}")))
                                                Color(0xFF3A3A3A) else Color.Transparent)
                                            .padding(vertical = if (isClassicPhone) 0.dp else 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier
                                            .then(if (isClassicPhone) Modifier.padding((5f * classicUnit).dp) else Modifier)
                                            .size(plateSideDp.dp)
                                            .background(
                                                if (isTen) Color(rowColor ?: StartPersonalization.wallpaperAccentArgb)
                                                else if (discoLayered) visual.accent
                                                    ?.let { Color(it) } ?: accent else accent
                                            ), contentAlignment = Alignment.Center) {
                                            if (showBrandBackground && visual.richBackground != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = visual.richBackground,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                )
                                            }
                                            // WP8.1 app-list plates are 52 units wide; keep an
                                            // 11-unit clear inset around the displayed 30-unit
                                            // artwork. Start tile logos retain their own sizing.
                                            if (app.packageName == MOSAIC_SETTINGS_ID) {
                                                com.flivoro.tile8auncher.ui.components.MetroIcon(
                                                    glyph = "settings", size = settingsGlyphSideDp.dp)
                                            } else if (icon != null) androidx.compose.foundation.Image(
                                                bitmap = icon, contentDescription = null,
                                                modifier = Modifier.size(artworkSideDp.dp),
                                                colorFilter = null)
                                            else Text(app.label.take(1), color = Color.White,
                                                fontSize = fallbackGlyphSp.sp)
                                        }
                                        // Classic highlights only within search mode;
                                        // W10M highlights whenever its search box is used.
                                        val activeSearch = if (isClassicPhone &&
                                            !phone8SearchVisible) "" else search
                                        val displayTitle = remember(app.label, activeSearch, accent) {
                                            highlightedAppLabel(app.label, activeSearch, accent)
                                        }
                                        Text(displayTitle, color = Color.White,
                                            fontSize = if (isClassicPhone) (30f * classicUnit).sp else 18.sp,
                                            fontWeight = if (isClassicPhone) FontWeight.Light else FontWeight.Normal,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                                .padding(
                                                    start = if (isClassicPhone) (12f * classicUnit).dp else 14.dp,
                                                    top = 0.dp,
                                                    bottom = 0.dp,
                                                ))
                                    }
                                }
                            }
                        }
                        if (isClassicPhone) {
                            ClassicPhoneAlphabetSelector(
                                visible = alphabetOpen,
                                availableLetters = positions.keys,
                                accent = accent,
                                onSelect = { letter ->
                                    alphabetOpen = false
                                    positions[letter]?.let { destination ->
                                        scope.launch { appsState.scrollToItem(destination) }
                                    }
                                },
                                onDismiss = { alphabetOpen = false },
                            )
                        }
                        if (alphabetOpen && isTen) {
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
                                        "V", "W", "X", "Y", "Z", "&",
                                    )
                                    val count = if (isTen) 5 else 6
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                                        .clickable(interactionSource = remember { MutableInteractionSource() },
                                            indication = null, onClick = {}),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        letters.chunked(count).forEach { chunk ->
                                            Row(Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                chunk.forEach { letter ->
                                                    val available = letter in positions
                                                    Box(
                                                        Modifier.weight(1f).heightIn(min = 48.dp)
                                                            .background(if (available) accent else Color(0xFF292929))
                                                            .clickable(enabled = available) {
                                                                alphabetOpen = false
                                                                scope.launch {
                                                                    appsState.scrollToItem(positions.getValue(letter))
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        if (letter == "&") DiscoGlobeSymbol(
                                                            if (available) Color.White else Color.Gray)
                                                        else Text(letter,
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
                    // Disco keeps #sticky-letter outside the scrollable list.
                    // The following header pushes the sticky letter off-screen.
                    if (isClassicPhone && showApps && !phone8SearchVisible &&
                        !alphabetOpen && !isLaunching &&
                        (!motionUsesBack || sourceMotionMillis() >= duration)) {
                        val visibleIndex = appsState.firstVisibleItemIndex
                        val crossed = classicStickyPositions.entries.lastOrNull { entry ->
                            entry.key.length == 1 &&
                                (entry.value < visibleIndex ||
                                    (entry.value == visibleIndex &&
                                        appsState.firstVisibleItemScrollOffset > 0))
                        }
                        crossed?.let { section ->
                            val following = classicStickyPositions.entries.firstOrNull {
                                it.key.length == 1 && it.value > section.value
                            }
                            val headerHeightPx = with(LocalDensity.current) {
                                (64f * classicUnit).dp.toPx()
                            }
                            val followingOffset = following?.let { next ->
                                appsState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == next.value }?.offset?.toFloat()
                            }
                            val upwardPushPx = if (followingOffset == null) 0f
                                else (followingOffset - headerHeightPx).coerceAtMost(0f)
                            Row(
                                Modifier.offset(
                                    x = appListContentLeftCssPx.dp,
                                    y = (24f * classicUnit).dp +
                                        with(LocalDensity.current) { upwardPushPx.toDp() },
                                ).fillMaxWidth()
                                    .height((64f * classicUnit).dp)
                                    .background(Color.Black)
                                    .graphicsLayer {
                                        alpha = 1f - .5f * classicAppMenuDepth
                                        scaleX = 1f - .02f * classicAppMenuDepth
                                        scaleY = 1f - .02f * classicAppMenuDepth
                                    }
                                    .testTag("classic-sticky-letter")
                                    .storyLaunchActor("phone-sticky-letter", LaunchOrigin.ALL_APPS, sourceCandidate = false)
                                    .clickable(enabled = interactionEnabled) {
                                        alphabetOpen = true
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.padding((5f * classicUnit).dp)
                                        .size((52f * classicUnit).dp)
                                        .border((3f * classicUnit).dp, accent)
                                        .testTag("classic-sticky-letter-tile")
                                        .padding(
                                            start = (8f * classicUnit).dp,
                                            top = (5f * classicUnit).dp,
                                        ),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    if (section.key == "&") {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            DiscoGlobeSymbol(accent)
                                        }
                                    } else Text(
                                        section.key.lowercase(),
                                        color = accent,
                                        fontSize = (30f * classicUnit).sp,
                                        fontWeight = FontWeight.Light,
                                    )
                                }
                            }
                        }
                    }
                    if (isClassicPhone) {
                        ClassicPhoneSearchField(
                            visible = phone8SearchVisible,
                            query = search,
                            onQueryChange = { search = it },
                            onClose = { phone8SearchVisible = false; search = "" },
                            classicUnit = classicUnit,
                            enabled = interactionEnabled && !isLaunching,
                            accent = accent,
                            modifier = Modifier
                                .offset(x = (24f * classicUnit).dp, y = (4f * classicUnit).dp)
                                .width(((surfaceBounds.width / densityScale)
                                    .takeIf { it > 0f } ?: 480f) .let { (it - 48f * classicUnit).coerceAtLeast(80f) }.dp)
                                .height((48f * classicUnit).dp),
                        )
                    }
                    if (isClassicPhone) {
                        var searchIconBounds by remember { mutableStateOf(Rect.Zero) }
                        fun searchIconMotion(): PhoneStartMotionFrame =
                            classicAppsMotion(
                                animationIndex = 1f,
                                tileLeftCssPx = appListSearchLeftCssPx,
                                tileWidthCssPx = appListSearchSizeCssPx,
                            )
                        Box(
                            Modifier.offset(x = appListSearchLeftCssPx.dp, y = (26f * classicUnit).dp)
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
                                    alpha = appSearchIconAlpha.value * (1f - classicSearchProgress) *
                                        (if (classicAppsMotionActive) searchIconMotion().alpha else 1f)
                                }
                                .border((2.5f * classicUnit).dp, Color.White, CircleShape)
                                .testTag("phone-app-search-toggle")
                                .storyLaunchActor("phone-search-toggle", LaunchOrigin.ALL_APPS, sourceCandidate = false)
                                .clickable(enabled = interactionEnabled && !isLaunching && !phone8SearchVisible) {
                                    phone8SearchVisible = true
                                    panoramaScope.launch { appsState.scrollToItem(0) }
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
            // Android owns the navigation bar. System Back is handled by
            // BackHandler above; system Home returns to the real launcher.

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
                                    if (intentAction.isEmpty()) {
                                        actionCenterOpen = false
                                        onOpenSettings()
                                    } else runCatching { context.startActivity(Intent(intentAction)) }
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

    // Native Windows Phone uses a compact menu beside the held app row.
    editing?.takeIf { showApps && it.startsWith("app:") }?.let { id ->
        val pkg = id.removePrefix("app:")
        val app = byPackage[pkg]
        if (app != null) {
            val uninstallable = remember(pkg) {
                canRequestAppUninstall(context, pkg)
            }
            // WP8.1 is placed in window coordinates by the context menu itself.
            // W10M continues to use the existing compact strip placement.
            val tenMenuHeightPx = with(LocalDensity.current) { 265.dp.toPx() }
            PhoneAppContextMenu(
                mode = mode,
                packageName = pkg,
                alreadyPinned = if (onSharedPinsChanged == null) id in order else tiles.any {
                    it.packageName == pkg || (pkg == MOSAIC_SETTINGS_ID && it.id == "tile_settings")
                },
                isUninstallable = uninstallable,
                xPx = if (isClassicPhone) appContextBounds.center.x.toInt()
                    else appContextBounds.left.toInt().coerceAtLeast(8),
                yPx = if (isClassicPhone) 0 else appContextBounds.bottom.toInt().coerceIn(
                    0, (surfaceBounds.bottom - tenMenuHeightPx).toInt().coerceAtLeast(0),
                ),
                rowBoundsInWindow = appContextBounds,
                viewportBoundsInWindow = paneBounds,
                onPinChanged = {
                    val sharedPinned = onSharedPinsChanged != null && tiles.any {
                        it.packageName == pkg || (pkg == MOSAIC_SETTINGS_ID && it.id == "tile_settings")
                    }
                    if (sharedPinned) {
                        saveOrder(SharedStartWorkspace.orderWithoutPackage(context, mode, order, tiles, pkg))
                        folderRevision++
                    } else if (id in order) saveOrder(order.filterNot { it == id })
                    else {
                        val nextOrder = (order + id).distinct()
                        saveOrder(nextOrder)
                        if (isClassicPhone) {
                            panoramaScope.launch {
                                // Source: 300ms pre-pan, then center the newly pinned
                                // tile over 500ms instead of jumping to Start's bottom.
                                delay(300)
                                classicPinReturn = true
                                showApps = false
                                delay(300)
                                val selectedTiles = nextOrder.mapNotNull(::tileFor)
                                val placements = packPhoneTiles(selectedTiles.map(TileModel::size), columns)
                                // Persisted pin keys are "app:package", while
                                // rendered tile IDs are "phone_app_package".
                                val renderedId = if (id.startsWith("app:"))
                                    "phone_app_${id.removePrefix("app:")}" else id
                                val targetIndex = selectedTiles.indexOfFirst {
                                    it.id == renderedId
                                }
                                if (targetIndex >= 0) {
                                    val pos = placements[targetIndex]
                                    val viewportWidthDp = paneBounds.width / densityScale
                                    val edge = if (columns == 6) .025f else 25f / 480f
                                    val spacing = .025f * viewportWidthDp
                                    val cellDp = (viewportWidthDp * (1f - edge * 2) -
                                        spacing * (columns - 1)) / columns
                                    val rowDp = cellDp + spacing
                                    val tileCenterDp = (pos.row + pos.rows * .5f) * rowDp
                                    val viewportHeightDp = paneBounds.height / densityScale
                                    val target = ((tileCenterDp - viewportHeightDp * .5f) *
                                        densityScale).toInt().coerceIn(0, startScroll.maxValue)
                                    startScroll.animateScrollTo(target, animationSpec = tween(500))
                                }
                            }
                        }
                    }
                },
                onAppInfo = {
                    if (pkg == MOSAIC_SETTINGS_ID) {
                        // Preserve the long-pressed row origin for its launch.
                        onLaunch(appTile(app), appContextBounds, LaunchOrigin.ALL_APPS)
                    } else onOpenAppInfo(pkg)
                },
                onUninstall = { onUninstallApp(pkg) },
                onDismiss = { editing = null },
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
    launchMotionMode: LauncherUiMode = mode,
    hideOriginalTileId: String? = null,
    tileOpacity: Float,
    wallpaperStyle: Int,
    mobileTilePictureMode: Boolean,
    appsRepository: AppsRepository,
    editingTileId: String?,
    deletingTileId: String?,
    onResizeTile: (String) -> Unit,
    onUnpinTile: (String) -> Unit,
    onMoveTile: (String, String) -> Unit,
    onMergeTile: (String, String) -> Unit,
    folderPreviews: Map<String, List<TileModel>>,
    expandedFolder: PhoneFolderExpansion? = null,
    folderRevealProgress: Float? = null,
    onDragOutside: ((String) -> Unit)? = null,
    photoOriginYPx: Float = 0f,
    sharedPhotoHeightPx: Float? = null,
    onClick: (TileModel, Rect) -> Unit,
    onLongClick: (String) -> Unit,
    onExitEdit: () -> Unit,
) {
    val slots = remember(tiles, columns) { packPhoneTiles(tiles.map(TileModel::size), columns) }
    // Retain the outgoing folder through collapse; otherwise the space and
    // its children disappear in a single frame before the grid can flow back.
    var retainedFolder by remember { mutableStateOf(expandedFolder) }
    // Key the handoff by identity, not PhoneFolderExpansion's freshly allocated
    // callbacks. Retain the closing content until the gap is completely collapsed.
    LaunchedEffect(expandedFolder?.folder?.id) {
        if (expandedFolder != null) {
            retainedFolder = expandedFolder
        } else {
            delay(350) // Longer than W10M's 260 ms closing motion.
            retainedFolder = null
        }
    }
    val displayedFolder = expandedFolder ?: retainedFolder
    val folderIndex = tiles.indexOfFirst { it.id == displayedFolder?.folder?.id }
    val insertionRow = remember(slots, folderIndex) { phoneFolderInsertionRow(slots, folderIndex) }
    val childRows = remember(displayedFolder?.children, columns) {
        packPhoneTiles(displayedFolder?.children.orEmpty().map(TileModel::size), columns)
            .maxOfOrNull { it.row + it.rows } ?: 0
    }
    val classicEditing = mode == LauncherUiMode.PHONE_8 && editingTileId != null
    // Disco has a 500ms .home-menu-back-intro before its fully edited state.
    // The held tile grows to 112% during that phase, then settles at 105%.
    // Switching selection inside edit mode is immediate: it must NOT
    // restart the source's initial 500ms edit-mode entrance.
    var classicEditIntro by remember(classicEditing) { mutableStateOf(classicEditing) }
    LaunchedEffect(classicEditing) {
        if (classicEditing) {
            classicEditIntro = true
            delay(500)
            classicEditIntro = false
        } else classicEditIntro = false
    }
    // A single clock gives the surrounding tiles subtle phase-shifted motion.
    val editProgress by animateFloatAsState(
        targetValue = if (classicEditing) 1f else 0f,
        animationSpec = tween(
            if (classicEditing) 750 else 500,
            easing = if (classicEditing)
                androidx.compose.animation.core.CubicBezierEasing(.075f, .82f, .165f, 1f)
            else androidx.compose.animation.core.CubicBezierEasing(.25f, 1f, .25f, 1f),
        ),
        label = "disco-home-editor-page",
    )
    val classicWobble = if (classicEditing) {
        val transition = rememberInfiniteTransition(label = "disco-perlin-clock")
        val milliseconds by transition.animateFloat(
            initialValue = 0f, targetValue = 30000f,
            animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
            label = "disco-edit-noise-time",
        )
        milliseconds
    } else 0f
    var tileDragOffset by remember(editingTileId) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    // Keep clipping/visibility tied to the pane height, but use the full
    // source window height to calculate Disco's CSS stagger scale.
    val viewportHeightCssPx = (surfaceBounds.height / density)
        .takeIf { it > 0f } ?: viewportHeightPx / density
    val viewportWidthCssPx = surfaceBounds.width / density
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val classicUnit = maxWidth.value / 480f
        // Disco paints 6 CSS-pixel insets on both sides of each GridStack cell:
        // the visible gap is 12/480 of the viewport. Painted gutters are
        // 19+6 = 25 units (four columns) or 6+6 = 12 (six columns).
        // Scale with width; keep W10M's independent 4dp/10dp geometry.
        val gap = if (mode == LauncherUiMode.PHONE_8) maxWidth * .025f else 4.dp
        val side = if (mode == LauncherUiMode.PHONE_8) {
            maxWidth * if (columns == 6) .025f else (25f / 480f)
        } else 10.dp
        val cell = (maxWidth - side * 2 - gap * (columns - 1)) / columns
        val expanded = expandedFolder != null && folderIndex >= 0
        val fullFolderHeight = (cell + gap) * childRows + 58.dp
        // Keep the classic phone's original easing. W10M needs one coordinated
        // progress clock for grid displacement, the title, and the child reveal.
        val classicExpansionHeight by animateDpAsState(
            targetValue = if (expanded) fullFolderHeight else 0.dp,
            animationSpec = tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "phone-inline-folder-space-classic",
        )
        val mobileFolderProgress by animateFloatAsState(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (expanded) 330 else 260,
                easing = androidx.compose.animation.core.CubicBezierEasing(.20f, .80f, .20f, 1f),
            ),
            label = "w10m-inline-folder-motion",
        )
        val expansionHeight = if (mode == LauncherUiMode.MOBILE_10)
            fullFolderHeight * mobileFolderProgress else classicExpansionHeight
        val folderMotion = if (mode == LauncherUiMode.MOBILE_10)
            PhoneFolderMotion.frame(mobileFolderProgress) else PhoneFolderMotion.Frame.Visible
        val expansionPx = with(LocalDensity.current) { expansionHeight.toPx() }
        fun rowTopPx(row: Int, step: Float): Float = row * step +
            if (folderIndex >= 0 && row >= insertionRow) expansionPx else 0f

        val cellStepPx = with(LocalDensity.current) { (cell + gap).toPx() }.coerceAtLeast(1f)
        val topInsetPx = with(LocalDensity.current) { (if (mode == LauncherUiMode.MOBILE_10) 8.dp else 25.dp).toPx() }
        val visibleSlotIndices = slots.indices.filter { index ->
            val slot = slots[index]
            val top = topInsetPx + rowTopPx(slot.row, cellStepPx) - scrollOffsetPx
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
                    rowTopPx(slot.row, cellStepPx) / density +
                        ((cell * slot.rows + gap * (slot.rows - 1)) / 2).value,
                viewportWidthCssPx,
            )
        }
        val firstForwardWavePosition = visibleSlotIndices.minOfOrNull { forwardWavePositions[it] } ?: 0f
        val visibleTops = slots.mapNotNull { slot ->
            val top = topInsetPx + rowTopPx(slot.row, cellStepPx) - scrollOffsetPx
            val slotHeight = slot.rows * cellStepPx
            if (top + slotHeight > 0f && top < viewportHeightPx) top else null
        }
        val firstVisibleTop = visibleTops.minOrNull() ?: 0f
        val lastVisibleTop = visibleTops.maxOrNull() ?: firstVisibleTop
        val heightRows = slots.maxOfOrNull { it.row + it.rows } ?: 1
        val gridWidthForPhoto = maxWidth
        val tilePicture = mobileTilePictureMode
        // Preserve the entire choreography during launch, return-source selection,
        // editing, or deletion; idle scrolling can safely cull distant actors.
        val keepAllActors = isLaunching || editingTileId != null || deletingTileId != null
        val composedIndices = if (keepAllActors || viewportHeightPx <= 0f) {
            slots.indices.toList()
        } else slots.indices.filter { index ->
            val slot = slots[index]
            val top = topInsetPx + rowTopPx(slot.row, cellStepPx) - scrollOffsetPx
            val height = (cell * slot.rows + gap * (slot.rows - 1)).value * density
            shouldComposePhoneStartTile(top, top + height, viewportHeightPx) ||
                tiles[index].id == launchingTileId
        }
        Box(Modifier.fillMaxWidth().height((cell + gap) * heightRows + expansionHeight)
            .then(if (editingTileId != null && mode == LauncherUiMode.PHONE_8)
                Modifier.clickable(onClick = onExitEdit) else Modifier)) {
            // Picture pixels are part of each tile's transformed face below.
            // The empty grid and gutters remain black without a detached mask.
            composedIndices.forEach { index ->
                val tile = tiles[index]
                val pos = slots[index]
                val rowFraction = MobileStartMotion.rowFraction(
                    topInsetPx + rowTopPx(pos.row, cellStepPx) - scrollOffsetPx,
                    firstVisibleTop, lastVisibleTop,
                )
                key(tile.id) {
                    val editKey = if (tile.id.startsWith("phone_app_")) {
                        "app:" + tile.id.removePrefix("phone_app_")
                    } else tile.id
                    val isSelectedForEdit = editingTileId == editKey
                    // Disco's edited tile is not animated with one scale tween.
                    // CSS separately animates `transform: scale(.925 -> 1)` and
                    // `scale: 1 -> 1.05`, using DIFFERENT overshooting curves.
                    // Combining them into one 1.05 target flattens the "pop".
                    val editTransformScale by animateFloatAsState(
                        targetValue = when {
                            editingTileId == null -> 1f
                            !classicEditing -> if (isSelectedForEdit) .975f else 1f
                            isSelectedForEdit && classicEditIntro -> 1.12f
                            isSelectedForEdit || classicEditIntro -> 1f
                            else -> .925f
                        },
                        animationSpec = when {
                            mode != LauncherUiMode.PHONE_8 -> tween(250)
                            classicEditIntro -> tween(500, easing = LinearEasing)
                            isSelectedForEdit -> tween(
                                250,
                                easing = androidx.compose.animation.core.CubicBezierEasing(
                                    .43f, 1.5f, .5f, 1f,
                                ),
                            )
                            classicEditing -> tween(
                                // Disco gives deselected tiles a 50ms transform,
                                // not the selected tile's 250ms spring-like curve.
                                50,
                                easing = androidx.compose.animation.core.CubicBezierEasing(
                                    .25f, .1f, .25f, 1f,
                                ),
                            )
                            else -> tween(250)
                        },
                        label = "disco-edit-transform-scale",
                    )
                    val editPopScale by animateFloatAsState(
                        targetValue = if (classicEditing && isSelectedForEdit &&
                            !classicEditIntro) 1.05f else 1f,
                        animationSpec = if (classicEditing && isSelectedForEdit &&
                            !classicEditIntro) tween(
                            250,
                            easing = androidx.compose.animation.core.CubicBezierEasing(
                                .43f, 2f, .5f, 1f,
                            ),
                        ) else tween(0), // CSS removes the extra scale immediately on deselection.
                        label = "disco-edit-pop-scale",
                    )
                    val editScale = editTransformScale * editPopScale
                    val childReveal = if (mode == LauncherUiMode.MOBILE_10 &&
                        folderRevealProgress != null) {
                        PhoneFolderMotion.childFrame(folderRevealProgress, index)
                    } else PhoneFolderMotion.Frame.Visible
                    val folderFaceProgress = if (mode == LauncherUiMode.MOBILE_10 &&
                        tile.id == displayedFolder?.folder?.id) mobileFolderProgress else 0f
                    val deletion by animateFloatAsState(
                        targetValue = if (deletingTileId == editKey) 1f else 0f,
                        animationSpec = tween(
                            200,
                            easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, .58f, 1f),
                        ),
                        label = "disco-unpin-delete",
                    )
                    // Disco starts background tile shake immediately when selection
                    // changes; a 440ms wobble ramp made the old tile feel delayed.
                    val editAlpha by animateFloatAsState(
                        targetValue = when {
                            editingTileId == null || isSelectedForEdit -> 1f
                            classicEditing && classicEditIntro -> 1f
                            classicEditing -> .60f
                            else -> .68f
                        },
                        animationSpec = tween(
                            250,
                            easing = if (classicEditing && isSelectedForEdit)
                                androidx.compose.animation.core.CubicBezierEasing(.43f, 1.5f, .5f, 1f)
                            else androidx.compose.animation.core.CubicBezierEasing(.25f, .1f, .25f, 1f),
                        ), label = "classic-edit-opacity",
                    )
                    // Animated reflow is only activated when editing reorders tiles;
                    // app launch/return interpolation is not modified.
                    val tileLeft by animateDpAsState(
                        side + (cell + gap) * pos.column,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium),
                        label = "phone-reflow-x",
                    )
                    val tileTop by animateDpAsState(
                        (cell + gap) * pos.row,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium),
                        label = "phone-reflow-y",
                    )
                    val displacedTop = tileTop +
                        if (folderIndex >= 0 && pos.row >= insertionRow) expansionHeight else 0.dp
                    PhoneTile(
                        tile = tile, mode = mode,
                        launchMotionMode = launchMotionMode,
                        hideOriginalForDesktopFlip = tile.id == hideOriginalTileId,
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
                        tileOpacity = if (tilePicture) 0f else tileOpacity,
                        tilePictureContent = if (tilePicture) {
                            { inverseTileProjection ->
                                com.flivoro.tile8auncher.ui.components.PhoneTilePicture(
                                    modifier = Modifier.fillMaxSize(),
                                    wallpaperStyle = wallpaperStyle,
                                    tileLeftPx = (side + (cell + gap) * pos.column).value * density,
                                    tileTopPx = photoOriginYPx + rowTopPx(pos.row, cellStepPx),
                                    gridWidthPx = gridWidthForPhoto.value * density,
                                    gridHeightPx = sharedPhotoHeightPx ?: ((cell + gap) * heightRows).value * density,
                                    inverseTileProjection = inverseTileProjection,
                                )
                            }
                        } else null,
                        repository = appsRepository,
                        folderChildren = folderPreviews[tile.id].orEmpty(),
                        folderOpenProgress = folderFaceProgress,
                        resumeUsesBackMotion = resumeUsesBackMotion,
                        modifier = Modifier.testTag("phone-start-tile-${tile.id}")
                            .offset(x = tileLeft, y = displacedTop)
                            .size(width = cell * pos.columns + gap * (pos.columns - 1),
                                  height = cell * pos.rows + gap * (pos.rows - 1))
                            .zIndex(if (isSelectedForEdit) 4f else 0f)
                            .graphicsLayer {
                                alpha = editAlpha * (1f - deletion) *
                                    childReveal.childrenOpacity
                                scaleX = editScale * (1f - .5f * deletion) *
                                    childReveal.childrenScale
                                scaleY = editScale * (1f - .5f * deletion) *
                                    childReveal.childrenScale
                                translationX = if (isSelectedForEdit) tileDragOffset.x
                                    else if (classicEditing && !classicEditIntro)
                                        ClassicPhoneEditMotion.wobble(tile.id, classicWobble, false) *
                                            density * editProgress * .35f
                                    else 0f
                                translationY = (if (isSelectedForEdit) tileDragOffset.y
                                    else if (classicEditing && !classicEditIntro)
                                        ClassicPhoneEditMotion.wobble(tile.id, classicWobble, true) *
                                            density * editProgress * .35f
                                    else 0f) + childReveal.childrenOffsetDp * density
                            },
                        // The launch controller must receive the same artwork/color
                        // that this physical phone tile resolved, not the desktop seed.
                        onClick = { renderedTile, rect ->
                            // Tapping ordinary Start tiles while a folder is expanded
                            // dismisses it rather than accidentally launching behind it.
                            // Folder children use their own grid with expandedFolder=null
                            // and retain the normal originating-tile launch animation.
                            if (expandedFolder != null &&
                                tile.id != expandedFolder.folder.id) expandedFolder.onDismiss()
                            else onClick(renderedTile, rect)
                        },
                        onPressStart = if (mode == LauncherUiMode.PHONE_8 &&
                            classicEditing && !isSelectedForEdit) {
                            { onClick(tile, Rect.Zero) }
                        } else null,
                        tiltEnabled = !(mode == LauncherUiMode.PHONE_8 && classicEditing),
                        onLongClick = {
                            onLongClick(
                                if (tile.id.startsWith("phone_app_"))
                                    "app:" + tile.id.removePrefix("phone_app_")
                                else tile.id
                            )
                        },
                    )
                    if (isSelectedForEdit && deletingTileId != editKey &&
                        (!classicEditing || !classicEditIntro)) {
                        Box(Modifier
                            .offset(x = tileLeft, y = displacedTop)
                            .size(width = cell * pos.columns + gap * (pos.columns - 1),
                                height = cell * pos.rows + gap * (pos.rows - 1))
                            .zIndex(5f)
                            .graphicsLayer {
                                translationX = tileDragOffset.x
                                translationY = tileDragOffset.y
                                if (mode == LauncherUiMode.PHONE_8) {
                                    // Buttons and their input surface must follow
                                    // the exact selected-tile scale, not float at
                                    // the original unscaled grid-slot coordinates.
                                    scaleX = editScale
                                    scaleY = editScale
                                }
                            }
                            .then(if (mode == LauncherUiMode.PHONE_8) Modifier
                                else Modifier.border(2.dp, Color.White))
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
                                        if (onDragOutside != null && (currentCenterY < 0f ||
                                            currentCenterY > heightRows * cellStepPx)) {
                                            onDragOutside(editKey)
                                        } else if (moved.getDistance() > cellStepPx * .22f) {
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
                            if (mode == LauncherUiMode.PHONE_8) {
                                // Disco's overlay is two outlined 42px circles at
                                // (-15,-15) and (right+15,bottom+15), not a full border.
                                Box(Modifier.align(Alignment.TopEnd)
                                    .offset(x = (15f * classicUnit).dp, y = (-15f * classicUnit).dp)
                                    .size((42f * classicUnit).dp)
                                    .background(Color.Black, CircleShape)
                                    .border((3f * classicUnit).dp, Color.White, CircleShape)
                                    .clickable { onUnpinTile(editKey) }
                                    .testTag("disco-tile-unpin"),
                                    contentAlignment = Alignment.Center) {
                                    // The source draws a crossed-circle control;
                                    // font glyphs have device-dependent baselines.
                                    androidx.compose.foundation.Canvas(Modifier.size((23f * classicUnit).dp)) {
                                        val stroke = (2.6f * classicUnit).dp.toPx()
                                        drawLine(Color.White,
                                            Offset(size.width * .18f, size.height * .18f),
                                            Offset(size.width * .82f, size.height * .82f),
                                            strokeWidth = stroke,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                        drawLine(Color.White,
                                            Offset(size.width * .82f, size.height * .18f),
                                            Offset(size.width * .18f, size.height * .82f),
                                            strokeWidth = stroke,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    }
                                }
                                Box(Modifier.align(Alignment.BottomEnd)
                                    .offset(x = (15f * classicUnit).dp, y = (15f * classicUnit).dp)
                                    .size((42f * classicUnit).dp)
                                    .background(Color.Black, CircleShape)
                                    .border((3f * classicUnit).dp, Color.White, CircleShape)
                                    .clickable { onResizeTile(editKey) }
                                    .testTag("disco-tile-resize"),
                                    contentAlignment = Alignment.Center) {
                                    // DiscoElements.wTileMenu uses the MDI U+F004D
                                    // glyph, with 30x16 source clipping and a 35%/47%
                                    // rotation pivot. The former three-line sketch
                                    // had the wrong arrow proportions and terminals.
                                    DiscoSourceArrowGlyph(
                                        drawableId = R.drawable.disco_mdi_arrow_left,
                                        classicUnit = classicUnit,
                                        rotationDegrees = when (tile.size) {
                                            TileSize.SMALL -> 225f
                                            TileSize.MEDIUM -> 45f
                                            TileSize.WIDE -> 0f
                                            TileSize.LARGE -> 90f
                                        },
                                    )
                                }
                            } else {
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
            displayedFolder?.takeIf { folderIndex >= 0 && expansionHeight > 0.dp }?.let { folder ->
                Column(Modifier.fillMaxWidth().offset(y = (cell + gap) * insertionRow)
                    .height(expansionHeight).clipToBounds().testTag("phone-expanded-folder")) {
                    Box(Modifier.graphicsLayer {
                        alpha = folderMotion.headerOpacity
                        translationY = folderMotion.headerOffsetDp * density
                    }) {
                        PhoneFolderHeader(folder, interactionEnabled && !isLaunching &&
                            expandedFolder != null)
                    }
                    // Only WP8.1 retains the legacy group reveal. W10M reveals
                    // each actual child tile, with one shared reversible clock.
                    Box(if (mode != LauncherUiMode.MOBILE_10) Modifier.graphicsLayer {
                        alpha = folderMotion.childrenOpacity
                        translationY = folderMotion.childrenOffsetDp * density
                        scaleX = folderMotion.childrenScale
                        scaleY = folderMotion.childrenScale
                        transformOrigin = TransformOrigin(.5f, 0f)
                    } else Modifier) {
                    PhoneStartGrid(
                        tiles = folder.children, columns = columns,
                        scrollOffsetPx = scrollOffsetPx - ((cellStepPx * insertionRow) + (48f * density)).roundToInt(),
                        elapsedMillis = elapsedMillis, surfaceBounds = surfaceBounds,
                        viewportBounds = viewportBounds, viewportHeightPx = viewportHeightPx,
                        interactionEnabled = interactionEnabled && expandedFolder != null,
                        launchingTileId = launchingTileId, isLaunching = isLaunching,
                        resumeUsesBackMotion = resumeUsesBackMotion, mode = mode,
                        launchMotionMode = launchMotionMode, hideOriginalTileId = hideOriginalTileId,
                        tileOpacity = tileOpacity, wallpaperStyle = wallpaperStyle,
                        mobileTilePictureMode = mobileTilePictureMode, appsRepository = appsRepository,
                        editingTileId = editingTileId, deletingTileId = deletingTileId,
                        onResizeTile = onResizeTile, onUnpinTile = folder.onRemoveChild,
                        onMoveTile = folder.onReorderChild, onMergeTile = folder.onReorderChild,
                        folderPreviews = emptyMap(),
                        folderRevealProgress = if (mode == LauncherUiMode.MOBILE_10)
                            mobileFolderProgress else null,
                        onDragOutside = folder.onRemoveChild,
                        photoOriginYPx = photoOriginYPx + cellStepPx * insertionRow + 48f * density,
                        sharedPhotoHeightPx = sharedPhotoHeightPx ?: ((cell + gap) * heightRows).value * density,
                        onClick = onClick, onLongClick = onLongClick, onExitEdit = onExitEdit,
                    )
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
    launchMotionMode: LauncherUiMode = mode,
    hideOriginalForDesktopFlip: Boolean = false,
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
    tilePictureContent: (@Composable (inverseTileProjection: () -> PlatformMatrix?) -> Unit)? = null,
    repository: AppsRepository,
    modifier: Modifier,
    onClick: (TileModel, Rect) -> Unit,
    onLongClick: () -> Unit,
    onPressStart: (() -> Unit)? = null,
    tiltEnabled: Boolean = true,
    folderChildren: List<TileModel> = emptyList(),
    folderOpenProgress: Float = 0f,
) {
    // Read the master timebase during graphics rendering, not during composition.
    // Video analysis found lower/right WP8.1 tiles depart before upper/left tiles.
    var bounds by remember(tile.id) { mutableStateOf(Rect.Zero) }
    // Reuse these only in this tile's UI-thread drawing callbacks. Each tile has
    // its own set, so sibling projections cannot clobber one another.
    val nativeProjection = remember(tile.id) { PlatformMatrix() }
    val projectionFrom = remember(tile.id) { FloatArray(8) }
    val projectionTo = remember(tile.id) { FloatArray(8) }
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val layered = LauncherFeatureStore.appIconStyle(context) ==
        AppIconStyle.DISCO_LAYERED
    val visual = rememberWp81TileVisual(
        repository, tile.packageName, mode == LauncherUiMode.PHONE_8 && layered,
    )
    val icon = visual.icon
    val classicStockTile = tile.id.startsWith("tile_")
    val roleGlyph = remember(context, tile.packageName, tile.title) {
        readPhoneAppRoles(context).glyph(tile.packageName, tile.title)
    }
    val classicPrimaryAppTile = tile.id.startsWith("phone_app_") && roleGlyph != null
    val userChoseColor = PhoneLayoutStore.hasCustomColor(context, mode, tile.id)
    // Keep W10M on the same WP8.1 tile-color policy, including the
    // stock Metro accent, manual overrides and remembered brand plate.
    val allowClassicBrandColor = canUsePhoneBrandColor(
        tile.id, tile.title, userChoseColor, layered,
    )
    val resolvedColor = resolvePhoneTileBackgroundArgb(
        tile.id, tile.title, tile.colorValue,
        visual.accent.takeIf { !tile.packageName.isNullOrBlank() },
        userChoseColor, layered, StartPersonalization.wallpaperAccentArgb,
    )
    val resolvedTile = if (resolvedColor != tile.colorValue)
        tile.copy(colorValue = resolvedColor) else tile
    // WP8.1 app icons are normalized by visible content bounds, not per-app
    // scale heuristics. Stock tiles retain their requested 1.5x glyphs.
    val classicIconScale = when {
        mode != LauncherUiMode.PHONE_8 -> 1f
        // The Phone vector's intrinsic layout size includes the factor;
        // applying graphicsLayer scaling as well would double-enlarge it.
        classicPrimaryAppTile && roleGlyph == "wp81_phone" -> 1f
        classicStockTile -> 1.5f
        else -> 1.22f
    }
    val wp81NativeGlyph = if (mode == LauncherUiMode.PHONE_8 &&
        classicPrimaryAppTile) {
        roleGlyph
    } else null
    val faceTile = if (wp81NativeGlyph != null)
        resolvedTile.copy(iconGlyph = wp81NativeGlyph) else resolvedTile
    val brandedBackground = if (allowClassicBrandColor &&
        !userChoseColor && tilePictureContent == null && layered)
        visual.richBackground else null

    fun currentMotion(): PhoneStartMotionFrame = if (exiting &&
        launchMotionMode == LauncherUiMode.MOBILE_10 ||
        !exiting && mode == LauncherUiMode.MOBILE_10) {
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
        mode = LauncherUiMode.PHONE_8, exiting = exiting, elapsedMillis = elapsedMillis(),
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
        return projectPlaneMatrix(
            widthPx, heightPx, density, leftCss, topCss,
            reusedMatrix = nativeProjection,
            sourceScratch = projectionFrom,
            destinationScratch = projectionTo,
            project = { xCss, yCss ->
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
            },
        )
    }

    WindowsTileFace(
        tile = faceTile,
        appIcon = icon,
        contentOverride = if (folderChildren.isNotEmpty()) ({
            Box(Modifier.fillMaxSize()) {
                PhoneFolderPreview(
                    folderChildren, repository,
                    Modifier.fillMaxSize().graphicsLayer {
                        // The miniature tile mosaic tucks inward as real child
                        // tiles unfold and reverses when the folder is tapped again.
                        val inset = folderOpenProgress.coerceIn(0f, 1f)
                        scaleX = 1f - .075f * inset
                        scaleY = 1f - .075f * inset
                        alpha = 1f - .16f * inset
                    },
                    mode = mode,
                )
                if (tile.size != TileSize.SMALL) Text(tile.title,
                    color = Color.White, fontSize = 11.sp, maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp))
            }
        }) else null,
        // The complete WP8.1 color policy is resolved above. Prevent the
        // icon-face compositor from silently painting white on top of it.
        allowAppDerivedBackground = false,
        logoModifier = if (wp81NativeGlyph == "wp81_phone") {
            Modifier.graphicsLayer {
                // Position only; keep native-density vector edges sharp.
                translationX = -0.19f * density
                translationY = 5.41f * density
            }
        } else if (classicIconScale != 1f) Modifier.graphicsLayer {
            scaleX = classicIconScale
            scaleY = classicIconScale
        } else Modifier,
        backgroundAlpha = tileOpacity,
        backgroundContent = if (tilePictureContent != null) {
            {
                tilePictureContent {
                    // Counter-transform wallpaper pixels ONLY, after the moving
                    // tile's clip. Icons, text and outer tile keep their
                    // existing launch/return choreography completely unchanged.
                    val motion = currentMotion()
                    val forward = if (
                        if (exiting) launchMotionMode == LauncherUiMode.PHONE_8
                        else mode == LauncherUiMode.PHONE_8
                    ) {
                        tileProjectionMatrix(
                            bounds.width.coerceAtLeast(1f),
                            bounds.height.coerceAtLeast(1f),
                            motion,
                        )
                    } else {
                        PlatformMatrix().apply {
                            val px = bounds.width * motion.pivotX
                            val py = bounds.height * .5f
                            setScale(motion.scale, motion.scale, px, py)
                            postTranslate(motion.translationXPx, motion.translationYPx)
                        }
                    }
                    PlatformMatrix().takeIf { forward.invert(it) }
                }
            }
        } else if (brandedBackground != null) {
            {
                androidx.compose.foundation.Image(
                    bitmap = brandedBackground,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = .18f)),
                        ),
                    ),
                )
            }
        } else null,
        // Native glyphs and labels remain attached to the tile face throughout its turn.
        innerModifier = Modifier,
        modifier = modifier
            .storyLaunchActor(tile.id, LaunchOrigin.START)
            .onGloballyPositioned {
                // boundsInWindow clips partially visible tiles, moving their apparent centre.
                // The zoom anchor must use the full, untransformed tile geometry.
                bounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
            }
            .then(if (if (exiting) launchMotionMode == LauncherUiMode.PHONE_8
                else mode == LauncherUiMode.PHONE_8) {
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
            // Desktop flip compositor owns the selected physical source.
            // Hide its original face so there is never a stationary duplicate.
            .then(if (hideOriginalForDesktopFlip) Modifier.graphicsLayer {
                alpha = 0f
            } else Modifier)
            .phoneToolkitTilePress(
                enabled = interactionEnabled && !exiting,
                onClick = { bounds -> onClick(faceTile, bounds) },
                onLongClick = onLongClick,
                onPressStart = onPressStart,
                tiltEnabled = tiltEnabled,
            ),
    )
}


/**
 * Real Windows Phone tile face reused by the Desktop launch compositor.
 *
 * Rendering a generic WindowsTileFace with the desktop cached app icon is
 * visually WRONG for phone skins: phone colors, normalized layered artwork,
 * Lumia-native dialer/messages glyphs, and stock icon scaling differ. Keep
 * this renderer sourced from exactly the same WP8.1 visual/color policy as
 * PhoneTile above. Only the source-face drawing is shared; timing remains the
 * authentic Desktop animation when that master choice is selected.
 */
@Composable
internal fun PhoneLaunchSourceFace(
    tile: TileModel,
    mode: LauncherUiMode,
    repository: AppsRepository,
    modifier: Modifier = Modifier,
    sourceBounds: Rect = Rect.Zero,
    sourceInitialOpacity: Float = 1f,
    sourcePictureMode: Boolean = false,
    wallpaperStyle: Int = 0,
    launchProgress: () -> Float = { 0f },
    sourceOrigin: LaunchOrigin = LaunchOrigin.START,
    preferredIcon: androidx.compose.ui.graphics.ImageBitmap? = null,
    foregroundOnly: Boolean = false,
    backgroundOnly: Boolean = false,
    detailsOnly: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val layered = LauncherFeatureStore.appIconStyle(context) == AppIconStyle.DISCO_LAYERED
    val visual = rememberWp81TileVisual(
        repository, tile.packageName,
        (mode == LauncherUiMode.PHONE_8 || sourceOrigin == LaunchOrigin.ALL_APPS) && layered,
        refreshInBackground = false,
    )
    val customColor = PhoneLayoutStore.hasCustomColor(context, mode, tile.id)
    val resolvedColor = resolvePhoneTileBackgroundArgb(
        tile.id, tile.title, tile.colorValue,
        visual.accent.takeIf { !tile.packageName.isNullOrBlank() },
        customColor, layered, StartPersonalization.wallpaperAccentArgb,
    )
    val nativeGlyph = remember(context, mode, tile.packageName, tile.title) {
        if (mode == LauncherUiMode.PHONE_8 && tile.id.startsWith("phone_app_"))
            readPhoneAppRoles(context).glyph(tile.packageName, tile.title) else null
    }
    val faceTile = tile.copy(
        colorValue = resolvedColor,
        // The Desktop live-tile template paints an opaque gradient within its
        // CONTENT, bypassing WindowsTileFace(backgroundAlpha=0). When that
        // template is used as the story's foreground it creates a second
        // small rectangle inside the expanding panel. The panel itself owns
        // every material pixel; the foreground only renders its glyph/title.
        tileType = if (foregroundOnly && tile.tileType == TileType.DESKTOP)
            TileType.APP else tile.tileType,
        iconGlyph = nativeGlyph ?: tile.iconGlyph.takeIf { it.isNotBlank() }
            ?: if (foregroundOnly && tile.tileType == TileType.DESKTOP) "desktop" else "",
    )
    val classicScale = when {
        mode != LauncherUiMode.PHONE_8 -> 1f
        nativeGlyph == "wp81_phone" -> 1f
        tile.id.startsWith("tile_") -> 1.5f
        else -> 1.22f
    }
    val richBackground = if (
        canUsePhoneBrandColor(tile.id, tile.title, customColor, layered) &&
        !customColor && layered
    ) visual.richBackground else null
    // Match the visible Start face at t=0 and settle on this tile's own
    // resolved solid accent at t=50%. The source wallpaper and solid color are
    // complementary layers, so they never appear as two competing tiles.
    val viewportWidth = context.resources.displayMetrics.widthPixels.toFloat()
    val viewportHeight = context.resources.displayMetrics.heightPixels.toFloat()

    if (backgroundOnly) {
        // A story morph paints the expanding ACCENT face in its main plane and
        // draws the original-size glyph separately. Only photo/rich artwork is
        // needed here; never draw a second glyph or the same solid color again.
        Box(modifier) {
            if (sourcePictureMode) {
                com.flivoro.tile8auncher.ui.components.PhoneTilePicture(
                    modifier = Modifier.fillMaxSize(),
                    wallpaperStyle = wallpaperStyle,
                    tileLeftPx = sourceBounds.left,
                    tileTopPx = sourceBounds.top,
                    gridWidthPx = viewportWidth,
                    gridHeightPx = viewportHeight,
                )
            } else if (richBackground != null) {
                androidx.compose.foundation.Image(
                    bitmap = richBackground,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }
        return
    }

    if (detailsOnly && sourceOrigin == LaunchOrigin.ALL_APPS) {
        // The real All Apps list lays its title BESIDE a leading icon plate,
        // not below a Start tile. Preserve that position while details fade;
        // the one permanent glyph is rendered by StoryLaunchOverlay instead.
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            Text(
                text = tile.title,
                color = Color.White,
                fontSize = if (mode == LauncherUiMode.PHONE_8) 24.sp else 18.sp,
                maxLines = 1,
                modifier = Modifier.padding(start =
                    if (mode == LauncherUiMode.PHONE_8) 72.dp else 60.dp),
            )
        }
        return
    }

    WindowsTileFace(
        tile = faceTile,
        appIcon = visual.icon ?: preferredIcon,
        modifier = modifier,
        allowAppDerivedBackground = false,
        // Background is painted exactly once below from the source opacity/photo
        // to the resolved solid color; an opaque base would defeat transparency.
        backgroundAlpha = 0f,
        innerModifier = Modifier,
        // Story owns exactly ONE icon instance for the whole timeline.
        // This transparent detail face carries labels/live data ONLY.
        // Hiding template logos prevents a second, misaligned glyph from
        // fading across the persistent centered icon at the midpoint.
        logoModifier = if (detailsOnly) Modifier.graphicsLayer { alpha = 0f }
        else if (nativeGlyph == "wp81_phone") Modifier.graphicsLayer {
            translationX = -.19f * density
            translationY = 5.41f * density
        } else if (classicScale != 1f) Modifier.graphicsLayer {
            scaleX = classicScale
            scaleY = classicScale
        } else Modifier,
        backgroundContent = {
            if (!foregroundOnly) {
            // Draw this above the solid base initially; both are transformed
            // by the one Desktop projective plane.
            if (sourcePictureMode) {
                com.flivoro.tile8auncher.ui.components.PhoneTilePicture(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = 1f - sourceColorArrival(launchProgress())
                    },
                    wallpaperStyle = wallpaperStyle,
                    tileLeftPx = sourceBounds.left,
                    tileTopPx = sourceBounds.top,
                    gridWidthPx = viewportWidth,
                    gridHeightPx = viewportHeight,
                )
            } else if (richBackground != null) {
                androidx.compose.foundation.Image(
                    bitmap = richBackground,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = 1f - sourceColorArrival(launchProgress())
                    },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            // Cover the transparent/photo source with the *resolved* solid
            // tile color, not a stale wallpaper or stock accent at t=0.
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val initialSolidOpacity = if (sourcePictureMode || richBackground != null)
                        0f else sourceInitialOpacity.coerceIn(0f, 1f)
                    alpha = initialSolidOpacity +
                        (1f - initialSolidOpacity) * sourceColorArrival(launchProgress())
                }.background(Color(resolvedColor)),
            )
            }
        },
    )
}

/** Smooth opacity transition, completing at half the launch duration. */
internal fun sourceColorArrival(progress: Float): Float {
    val x = (progress.coerceIn(0f, 1f) * 2f).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * The back/destination face of a Desktop-style flip retains the same Lumia
 * glyph or normalized icon as the phone source tile, not the desktop icon
 * bitmap loaded by MainActivity. No app lookup is initiated on every frame.
 */
@Composable
internal fun PhoneLaunchDestinationIcon(
    tile: TileModel,
    mode: LauncherUiMode,
    repository: AppsRepository,
    modifier: Modifier = Modifier,
    sourceOrigin: LaunchOrigin = LaunchOrigin.START,
    preferredIcon: androidx.compose.ui.graphics.ImageBitmap? = null,
    iconSize: androidx.compose.ui.unit.Dp = 78.dp,
) {
    val context = LocalContext.current
    val layered = LauncherFeatureStore.appIconStyle(context) == AppIconStyle.DISCO_LAYERED
    val artwork = rememberWp81TileVisual(
        repository, tile.packageName,
        (mode == LauncherUiMode.PHONE_8 || sourceOrigin == LaunchOrigin.ALL_APPS) && layered,
        refreshInBackground = false,
    )
    val stockGlyph = remember(context, mode, tile.packageName, tile.title) {
        if (mode == LauncherUiMode.PHONE_8 && tile.id.startsWith("phone_app_"))
            readPhoneAppRoles(context).glyph(tile.packageName, tile.title) else null
    }
    val glyph = stockGlyph ?: tile.iconGlyph
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            glyph.isNotEmpty() && glyph != "app" -> com.flivoro.tile8auncher.ui.components.MetroIcon(
                glyph, color = Color.White, size = iconSize,
            )
            artwork.icon != null || preferredIcon != null -> androidx.compose.foundation.Image(
                bitmap = (artwork.icon ?: preferredIcon)!!, contentDescription = tile.title,
                modifier = Modifier.size(iconSize),
            )
            else -> com.flivoro.tile8auncher.ui.components.MetroIcon(
                "app", color = Color.White, size = iconSize,
            )
        }
    }
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
