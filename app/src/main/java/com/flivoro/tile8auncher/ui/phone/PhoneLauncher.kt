package com.flivoro.tile8auncher.ui.phone

import android.content.Context
import android.content.Intent
import android.graphics.Matrix as PlatformMatrix
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.loadEnhancedApps
import com.flivoro.tile8auncher.features.recentlyInstalledApps as selectRecentlyInstalledApps
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import com.flivoro.tile8auncher.ui.components.WindowsTileFace
import com.flivoro.tile8auncher.ui.components.WindowsWallpaper
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
            cameraDistanceCssPx = PhoneStartChoreography.CLASSIC_TILE_PERSPECTIVE_CSS_PX,
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
    private fun key(mode: LauncherUiMode, suffix: String) = "phone_${mode.name}_$suffix"

    fun order(context: Context, mode: LauncherUiMode, defaults: List<TileModel>): List<String> {
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

    fun size(context: Context, mode: LauncherUiMode, tile: TileModel): TileSize {
        val name = prefs(context).getString(key(mode, "size_${tile.id}"), tile.size.name)
        val chosen = TileSize.entries.firstOrNull { it.name == name } ?: tile.size
        return if (chosen == TileSize.LARGE) TileSize.WIDE else chosen
    }

    fun saveSize(context: Context, mode: LauncherUiMode, tileId: String, size: TileSize) {
        prefs(context).edit { putString(key(mode, "size_$tileId"), size.name) }
    }

    fun color(context: Context, mode: LauncherUiMode, tile: TileModel): Long =
        prefs(context).getLong(key(mode, "color_${tile.id}"), tile.colorValue)

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
    resumeUsesBackMotion: Boolean = false,
) {
    val context = LocalContext.current
    val isTen = mode == LauncherUiMode.MOBILE_10
    val densityScale = LocalDensity.current.density
    val accent = StartPersonalization.accentColor
    val catalog = remember(sections) { sections.flatMap(AppSection::apps).distinctBy(AppInfo::packageName) }
    val byPackage = remember(catalog) { catalog.associateBy(AppInfo::packageName) }
    val desktopById = remember(tiles) { tiles.associateBy(TileModel::id) }
    var order by remember(mode) { mutableStateOf(PhoneLayoutStore.order(context, mode, tiles)) }
    var editing by remember(mode) { mutableStateOf<String?>(null) }
    var showApps by remember(mode) { mutableStateOf(false) }
    var suppressReturnPaneTransition by remember(mode) { mutableStateOf(false) }
    var alphabetOpen by remember(mode) { mutableStateOf(false) }
    var actionCenterOpen by remember(mode) { mutableStateOf(false) }
    var search by remember(mode) { mutableStateOf("") }
    var phone8SearchVisible by remember(mode) { mutableStateOf(false) }
    var phoneRevision by remember(mode) { mutableStateOf(0) }
    val densityRevision = LauncherFeatureRuntime.launcherModeRevision
    val columns = remember(mode, densityRevision) { LauncherFeatureStore.phoneSmallColumns(context, mode) }
    val tileOpacity = remember(mode, densityRevision) {
        if (isTen) LauncherFeatureStore.phoneTileOpacity(context) else 1f
    }
    val appsState = rememberLazyListState()
    val startScroll = rememberScrollState()
    var surfaceBounds by remember(mode) { mutableStateOf(Rect.Zero) }
    var paneBounds by remember(mode) { mutableStateOf(Rect.Zero) }
    var homeBannerBounds by remember(mode) { mutableStateOf(Rect.Zero) }
    // Disco reads window.innerHeight (the whole CSS viewport), not just
    // the Start/Apps content pane below status and navigation surfaces.
    val viewportHeightCssPx = (surfaceBounds.height / densityScale)
        .takeIf { it > 0f } ?: 850f
    val isClassicPhone = mode == LauncherUiMode.PHONE_8
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
    val appListContentLeftCssPx = if (isClassicPhone) 81f else 18f
    val appListSearchLeftCssPx = 25f
    val appListSearchSizeCssPx = 42f
    val appListContentWidthCssPx =
        (surfaceBounds.width / densityScale - if (isClassicPhone) 100f else 32f)
            .coerceAtLeast(1f)
    val listPhase = PhoneMotionPhase.FORWARD_OUT
    // The native reference's ~1s tile reveal is a CASCADE, not a uniformly
    // slowed 3D turn. Keep Disco's individual 500/350ms face/glyph easing and
    // use a longer native-observed tile-to-tile stagger for forward Start entry.
    // App exit, Back, desktop and W10M retain their independent durations.
    // One clock owns tiles, wallpaper and completion. A wall-clock delay in MainActivity
    // can expire before Compose has even presented the first animation frame.
    val duration = if (isLaunching && showApps && mode == LauncherUiMode.PHONE_8) {
        PhoneStartChoreography.totalMillis(
            mode, exiting = true, viewportHeightCssPx = viewportHeightCssPx,
        )
    } else if (isLaunching && showApps) {
        maxOf(PhoneMotionTimeline.totalMillis(mode, listPhase, 6),
            if (isTen) MobileStartMotion.EXIT_TOTAL_MS else 0)
    } else if (isClassicPhone && !isLaunching && !resumeUsesBackMotion) {
        PhoneStartChoreography.classicNativeForwardTotalMillis(viewportHeightCssPx)
    } else PhoneStartChoreography.totalMillis(
        mode, exiting = isLaunching,
        viewportHeightCssPx = viewportHeightCssPx,
    )
    val motionClock = remember(mode, entranceRequest, homeRequest, isLaunching, resumeUsesBackMotion) {
        Animatable(0f)
    }
    // This is a lifecycle phase, not a condition tied to the currently
    // selected panorama page. A completed Home return must NOT replay its
    // second-page 3D projection when the user swipes Apps -> Start later.
    var classicForwardEntranceActive by remember(
        mode, entranceRequest, homeRequest, isLaunching, resumeUsesBackMotion,
    ) {
        mutableStateOf(isClassicPhone && !isLaunching && !resumeUsesBackMotion)
    }
    // Every layer shares one display-synchronized timeline. Relative tile
    // cascade delay is calibrated inside the classic choreography.
    fun sourceMotionMillis(): Int = motionClock.value.roundToInt()
    val exitTileId = remember(isLaunching) { launchingTileId }
    val latestExitFinished by rememberUpdatedState(onExitFinished)
    val latestDuration by rememberUpdatedState(duration)
    LaunchedEffect(mode, entranceRequest, homeRequest, isLaunching, resumeUsesBackMotion) {
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
    )

    fun tileFor(id: String): TileModel? {
        val source = desktopById[id] ?: if (id.startsWith("app:")) {
            byPackage[id.removePrefix("app:")]?.let(::appTile)
        } else null
        return source?.copy(
            size = PhoneLayoutStore.size(context, mode, source),
            colorValue = PhoneLayoutStore.color(context, mode, source),
        )
    }

    val phoneTiles = remember(order, tiles, catalog, phoneRevision) { order.mapNotNull { id -> tileFor(id) } }

    fun saveOrder(next: List<String>) {
        order = next.distinct()
        PhoneLayoutStore.saveOrder(context, mode, order)
    }

    LaunchedEffect(homeRequest) {
        if (homeRequest > 0) {
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

    LaunchedEffect(entranceRequest, resumeUsesBackMotion) {
        if (entranceRequest > 0 && resumeUsesBackMotion &&
            mode == LauncherUiMode.PHONE_8 && showApps
        ) {
            // DiscoLauncher always resets the horizontal panorama to Start on
            // activity resume. The Back path turns Start tiles in, but does not
            // run the forward All Apps page turn used by the Home button path.
            Snapshot.withMutableSnapshot {
                suppressReturnPaneTransition = true
                showApps = false
                alphabetOpen = false
                editing = null
                actionCenterOpen = false
            }
            delay(duration.toLong())
            suppressReturnPaneTransition = false
        }
    }

    BackHandler(interactionEnabled && (showApps || alphabetOpen || actionCenterOpen || editing != null)) {
        when {
            editing != null -> editing = null
            alphabetOpen -> alphabetOpen = false
            actionCenterOpen -> actionCenterOpen = false
            else -> showApps = false
        }
    }

    var horizontalDistance by remember { mutableStateOf(0f) }
    val gestureModifier = Modifier.pointerInput(mode, showApps, interactionEnabled, isLaunching) {
        if (!interactionEnabled || isLaunching) return@pointerInput
        detectHorizontalDragGestures(
            onDragStart = { horizontalDistance = 0f },
            onHorizontalDrag = { change, amount ->
                horizontalDistance += amount
                change.consume()
            },
            onDragEnd = {
                if (horizontalDistance < -70f) showApps = true
                if (horizontalDistance > 70f) showApps = false
                horizontalDistance = 0f
            },
            onDragCancel = { horizontalDistance = 0f },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)
        .onGloballyPositioned { surfaceBounds = it.boundsInWindow() }) {
        if (isTen) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = MobileStartMotion.wallpaperAlpha(isLaunching, sourceMotionMillis())
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
                        alpha = MobileStartMotion.wallpaperAlpha(isLaunching, sourceMotionMillis())
                    },
                    horizontalArrangement = Arrangement.End) {
                    Text("⌄", color = Color.White, fontSize = 20.sp,
                        modifier = Modifier.clickable(enabled = interactionEnabled && !isLaunching) {
                            actionCenterOpen = !actionCenterOpen
                        })
                }
            }
            // Disco's #main-home-slider holds both slide pages in one panorama.
            // AnimatedContent was destroying the All Apps page whenever Start was selected,
            // so its independent 3D return was missing after launching from Start.
            // Keep both pages mounted and move the panorama with one shared X translation.
            val panoramaProgress by animateFloatAsState(
                targetValue = if (showApps && !suppressReturnPaneTransition) 1f else 0f,
                animationSpec = if (suppressReturnPaneTransition && isClassicPhone) snap()
                    else tween(
                        durationMillis = if (isTen) 260 else 270,
                        easing = androidx.compose.animation.core.Easing {
                            PhoneMotionTimeline.exponentialEaseOut6(it)
                        },
                    ),
                label = "Phone panorama position",
            )
            Box(
                Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .onGloballyPositioned { paneBounds = it.boundsInWindow() }
                    .then(gestureModifier),
            ) {
                // Start is in front of the right-hand page during Disco's Home entrance.
                Box(
                    Modifier.fillMaxSize().zIndex(1f).graphicsLayer {
                        translationX = -paneBounds.width * panoramaProgress
                    },
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(startScroll, enabled = interactionEnabled && !isLaunching)) {
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
                            launchingTileId = exitTileId,
                            isLaunching = isLaunching,
                            resumeUsesBackMotion = resumeUsesBackMotion,
                            mode = mode,
                            tileOpacity = tileOpacity,
                            appsRepository = appsRepository,
                            onClick = { tile, bounds -> onLaunch(tile, bounds, LaunchOrigin.START) },
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
                                resumeUsesBackMotion = resumeUsesBackMotion,
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
                                    alpha = if (isTen) MobileStartMotion.wallpaperAlpha(
                                        isLaunching, sourceMotionMillis(),
                                    ) else if (isLaunching && motionClock.value >= duration) 0f else 1f
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
                        suppressReturnPaneTransition && resumeUsesBackMotion
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer {
                            val listExitEnd = if (mode == LauncherUiMode.PHONE_8) duration
                                else PhoneMotionTimeline.totalMillis(mode, listPhase, 6)
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
                                alpha = if (isLaunching && motionClock.value >= listExitEnd) 0f else 1f
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
                                start = if (isClassicPhone) 81.dp else 18.dp,
                                end = if (isClassicPhone) 19.dp else 14.dp,
                                top = if (isClassicPhone) 21.dp else 0.dp,
                                bottom = if (isClassicPhone) 21.dp else 0.dp,
                            ),
                        ) {
                        if (isTen || phone8SearchVisible) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = search, onValueChange = { search = it },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 17.sp),
                                singleLine = true,
                                modifier = Modifier.weight(1f).background(Color(0xFF333333)).padding(10.dp),
                                decorationBox = { inner ->
                                    Box {
                                        if (search.isBlank()) Text("⌕  Search apps",
                                            color = Color.LightGray, fontSize = 17.sp)
                                        inner()
                                    }
                                },
                            )
                            Text("⚙", color = Color.White, fontSize = 26.sp,
                                modifier = Modifier.clickable(onClick = onOpenSettings).padding(9.dp))
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
                        val firstVisibleListIndex = visibleListIndices.minOrNull()
                            ?: appsState.firstVisibleItemIndex
                        val lastVisibleListIndex = visibleListIndices.maxOrNull()
                            ?: firstVisibleListIndex
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        LazyColumn(state = appsState, modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = interactionEnabled && !isLaunching) {
                            groups.forEach { (letter, groupApps) ->
                                item(key = "letter_$letter") {
                                    var headerBounds by remember(letter) { mutableStateOf(Rect.Zero) }
                                    fun headerMotion(): PhoneStartMotionFrame =
                                        PhoneStartChoreography.sampleAppListExit(
                                            elapsedMillis = sourceMotionMillis(),
                                            animationIndex = PhoneStartChoreography.appListAnimationIndex(
                                                positions[letter] ?: 0,
                                                firstVisibleListIndex, lastVisibleListIndex,
                                            ),
                                            viewportHeightCssPx = viewportHeightCssPx,
                                            viewportWidthCssPx = surfaceBounds.width / densityScale,
                                            letter = true,
                                            tileLeftCssPx = appListContentLeftCssPx,
                                            tileWidthCssPx = appListContentWidthCssPx,
                                        )
                                    val letterModifier = Modifier.fillMaxWidth()
                                        .then(if (isClassicPhone) Modifier.height(64.dp) else Modifier)
                                        .then(if (mode == LauncherUiMode.PHONE_8) {
                                            Modifier.onGloballyPositioned { headerBounds = it.boundsInWindow() }
                                                .graphicsLayer { alpha = if (isLaunching) headerMotion().alpha else 1f }
                                                .drawWithContent {
                                                    if (!isLaunching || headerBounds.width <= 0f) {
                                                        drawContent()
                                                    } else {
                                                        val canvas = drawContext.canvas.nativeCanvas
                                                        val saveCount = canvas.save()
                                                        canvas.concat(appListElementProjectionMatrix(
                                                            size.width, size.height, densityScale,
                                                            headerBounds, paneBounds, headerMotion(),
                                                        ))
                                                        drawContent()
                                                        canvas.restoreToCount(saveCount)
                                                    }
                                                }
                                        } else Modifier).clickable(
                                            enabled = letter == "#" ||
                                                (letter.length == 1 && letter[0].isLetter()),
                                        ) { alphabetOpen = true }
                                    if (isClassicPhone && letter.length == 1) {
                                        Row(letterModifier, verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier.padding(vertical = 6.dp).size(52.dp)
                                                    .border(3.dp, accent),
                                                contentAlignment = Alignment.TopStart,
                                            ) {
                                                Text(letter, color = accent, fontSize = 30.sp,
                                                    fontWeight = FontWeight.Light,
                                                    modifier = Modifier.padding(start = 8.dp, top = 5.dp))
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
                                    val listIndex = (positions[letter] ?: 0) + appIndex + 1
                                    val animationIndex = PhoneStartChoreography.appListAnimationIndex(
                                        listIndex, firstVisibleListIndex, lastVisibleListIndex,
                                    )
                                    val ordinal = (listIndex - appsState.firstVisibleItemIndex).coerceIn(0, 6)
                                    fun appRowMotion(): PhoneStartMotionFrame =
                                        PhoneStartChoreography.sampleAppListExit(
                                            elapsedMillis = sourceMotionMillis(),
                                            animationIndex = animationIndex,
                                            viewportHeightCssPx = viewportHeightCssPx,
                                            viewportWidthCssPx = surfaceBounds.width / densityScale,
                                            selected = exitTileId == "phone_app_${app.packageName}",
                                            tileLeftCssPx = appListContentLeftCssPx,
                                            tileWidthCssPx = appListContentWidthCssPx,
                                        )
                                    // The app list is stationary within its horizontally
                                    // moving pane; only actual app launch feathers the rows.
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .height(if (isClassicPhone) 64.dp else 60.dp)
                                            .onGloballyPositioned { bounds = it.boundsInWindow() }
                                            .then(if (mode == LauncherUiMode.PHONE_8) {
                                                Modifier.graphicsLayer {
                                                    alpha = if (isLaunching) appRowMotion().alpha else 1f
                                                }.drawWithContent {
                                                    if (!isLaunching || bounds.width <= 0f) {
                                                        drawContent()
                                                    } else {
                                                        val canvas = drawContext.canvas.nativeCanvas
                                                        val saveCount = canvas.save()
                                                        canvas.concat(appListElementProjectionMatrix(
                                                            size.width, size.height, densityScale,
                                                            bounds, paneBounds, appRowMotion(),
                                                        ))
                                                        drawContent()
                                                        canvas.restoreToCount(saveCount)
                                                    }
                                                }
                                            } else Modifier.graphicsLayer {
                                                val rowMotion = if (isLaunching) PhoneMotionTimeline.sample(
                                                    mode, listPhase, sourceMotionMillis(),
                                                    ordinal,
                                                    selectedTile = exitTileId == "phone_app_${app.packageName}",
                                                ) else PhoneMotionFrame(1f, 0f, 0f, 1f, .5f)
                                                alpha = rowMotion.alpha
                                                rotationY = rowMotion.rotationY
                                                translationY = rowMotion.offsetYPx * densityScale
                                                scaleX = rowMotion.scale
                                                scaleY = rowMotion.scale
                                                transformOrigin = TransformOrigin(rowMotion.pivotX, .5f)
                                                cameraDistance = maxOf(900f, 2f * bounds.width, 2f * bounds.height)
                                            })
                                            .combinedClickable(
                                                enabled = interactionEnabled && !isLaunching,
                                                onClick = { onLaunch(appTile(app), bounds, LaunchOrigin.ALL_APPS) },
                                                onLongClick = { editing = "app:${app.packageName}" },
                                            )
                                            .padding(vertical = if (isClassicPhone) 0.dp else 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier
                                            .then(if (isClassicPhone) Modifier.padding(5.dp) else Modifier)
                                            .size(if (isClassicPhone) 52.dp else 45.dp)
                                            .background(accent), contentAlignment = Alignment.Center) {
                                            if (icon != null) androidx.compose.foundation.Image(
                                                bitmap = icon, contentDescription = null,
                                                modifier = Modifier.size(if (isClassicPhone) 34.dp else 28.dp))
                                            else Text(app.label.take(1), color = Color.White,
                                                fontSize = if (isClassicPhone) 28.sp else 22.sp)
                                        }
                                        Text(app.label, color = Color.White,
                                            fontSize = if (isClassicPhone) 30.sp else 18.sp,
                                            fontWeight = if (isClassicPhone) FontWeight.Light else FontWeight.Normal,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                                .padding(
                                                    start = if (isClassicPhone) 12.dp else 14.dp,
                                                    top = if (isClassicPhone) 12.dp else 0.dp,
                                                    bottom = if (isClassicPhone) 12.dp else 0.dp,
                                                ))
                                    }
                                }
                            }
                        }
                        if (alphabetOpen) {
                            Dialog(onDismissRequest = { alphabetOpen = false }) {
                                Column(Modifier.fillMaxWidth().background(Color.Black).padding(14.dp)) {
                                    listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","#")
                                        .chunked(6).forEach { letters ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                letters.forEach { l ->
                                                    Text(l, color = if (l in positions) accent else Color.DarkGray,
                                                        fontSize = 25.sp,
                                                        modifier = Modifier.clickable(enabled = l in positions) {
                                                            alphabetOpen = false
                                                            scope.launch { appsState.animateScrollToItem(positions.getValue(l)) }
                                                        }.padding(8.dp))
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
                            PhoneStartChoreography.sampleAppListExit(
                                elapsedMillis = sourceMotionMillis(),
                                animationIndex = 1f,
                                viewportHeightCssPx = viewportHeightCssPx,
                                viewportWidthCssPx = surfaceBounds.width / densityScale,
                                tileLeftCssPx = appListSearchLeftCssPx,
                                tileWidthCssPx = appListSearchSizeCssPx,
                            )
                        Box(
                            Modifier.offset(x = appListSearchLeftCssPx.dp, y = 26.dp)
                                .size(appListSearchSizeCssPx.dp)
                                .onGloballyPositioned { searchIconBounds = it.boundsInWindow() }
                                .graphicsLayer {
                                    alpha = appSearchIconAlpha.value *
                                        (if (isLaunching) searchIconMotion().alpha else 1f)
                                }
                                .drawWithContent {
                                    if (!isLaunching || searchIconBounds.width <= 0f) {
                                        drawContent()
                                    } else {
                                        val canvas = drawContext.canvas.nativeCanvas
                                        val saveCount = canvas.save()
                                        canvas.concat(appListElementProjectionMatrix(
                                            size.width, size.height, densityScale,
                                            searchIconBounds, paneBounds, searchIconMotion(),
                                        ))
                                        drawContent()
                                        canvas.restoreToCount(saveCount)
                                    }
                                }
                                .border(3.dp, Color.White, CircleShape)
                                .clickable(enabled = interactionEnabled && !isLaunching) {
                                    phone8SearchVisible = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⌕", color = Color.White, fontSize = 20.sp,
                                modifier = Modifier.offset(x = 13.dp, y = 10.dp))
                        }
                    }
                }
            }
            }
            Row(Modifier.fillMaxWidth().height(48.dp).background(Color.Black),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 36.sp, color = Color.White,
                    modifier = Modifier.clickable(enabled = interactionEnabled && !isLaunching) {
                        when {
                            alphabetOpen -> alphabetOpen = false
                            showApps -> showApps = false
                            editing != null -> editing = null
                        }
                    }.padding(horizontal = 25.dp))
                Text("⊞", fontSize = 29.sp, color = Color.White,
                    modifier = Modifier.clickable(enabled = interactionEnabled && !isLaunching) { showApps = false; actionCenterOpen = false }.padding(horizontal = 25.dp))
                Text("⌕", fontSize = 28.sp, color = Color.White,
                    modifier = Modifier.testTag("phone-bottom-search")
                        .clickable(enabled = interactionEnabled && !isLaunching) { showApps = true }
                        .padding(horizontal = 25.dp))
            }
        }

        if (actionCenterOpen && isTen) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()
                .background(Color(0xFF212121)).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text("ACTION CENTER  ⌄",
                    color = Color.White, fontSize = 23.sp,
                    modifier = Modifier.clickable { actionCenterOpen = false })
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Wi-Fi", "Bluetooth", "Settings").forEach { title ->
                        Text(title, color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.weight(1f).background(accent).clickable {
                                actionCenterOpen = false
                                if (title == "Settings") onOpenSettings()
                                else runCatching {
                                    context.startActivity(Intent(
                                        if (title == "Wi-Fi") Settings.ACTION_WIFI_SETTINGS
                                        else Settings.ACTION_BLUETOOTH_SETTINGS
                                    ))
                                }
                            }.padding(vertical = 16.dp, horizontal = 5.dp))
                    }
                }
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

    editing?.let { id ->
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
    appsRepository: AppsRepository,
    onClick: (TileModel, Rect) -> Unit,
    onLongClick: (String) -> Unit,
) {
    val slots = remember(tiles, columns) { packPhoneTiles(tiles.map(TileModel::size), columns) }
    // Classic phone tiles use a tighter spacing than Til's desktop Metro grid.
    // Keep the desktop and Windows 10 Mobile grid geometry unchanged.
    val gap = if (mode == LauncherUiMode.PHONE_8) 3.dp else 4.dp
    val side = 10.dp
    val density = LocalDensity.current.density
    // Keep clipping/visibility tied to the pane height, but use the full
    // source window height to calculate Disco's CSS stagger scale.
    val viewportHeightCssPx = (surfaceBounds.height / density)
        .takeIf { it > 0f } ?: viewportHeightPx / density
    val viewportWidthCssPx = surfaceBounds.width / density
    BoxWithConstraints(Modifier.fillMaxWidth()) {
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
        val visibleTops = slots.mapNotNull { slot ->
            val top = topInsetPx + slot.row * cellStepPx - scrollOffsetPx
            val slotHeight = slot.rows * cellStepPx
            if (top + slotHeight > 0f && top < viewportHeightPx) top else null
        }
        val firstVisibleTop = visibleTops.minOrNull() ?: 0f
        val lastVisibleTop = visibleTops.maxOrNull() ?: firstVisibleTop
        val heightRows = slots.maxOfOrNull { it.row + it.rows } ?: 1
        Box(Modifier.fillMaxWidth().height((cell + gap) * heightRows)) {
            tiles.forEachIndexed { index, tile ->
                val pos = slots[index]
                val rowFraction = MobileStartMotion.rowFraction(
                    topInsetPx + pos.row * cellStepPx - scrollOffsetPx,
                    firstVisibleTop, lastVisibleTop,
                )
                key(tile.id) {
                    PhoneTile(
                        tile = tile, mode = mode,
                        visibleRowFraction = rowFraction,
                        animationIndex = animationOrder[index]?.let { rank ->
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
                        tileOpacity = tileOpacity, repository = appsRepository,
                        resumeUsesBackMotion = resumeUsesBackMotion,
                        modifier = Modifier
                            .offset(x = side + (cell + gap) * pos.column,
                                    y = (cell + gap) * pos.row)
                            .size(width = cell * pos.columns + gap * (pos.columns - 1),
                                  height = cell * pos.rows + gap * (pos.rows - 1)),
                        onClick = { rect -> onClick(tile, rect) },
                        onLongClick = { onLongClick(tile.id) },
                    )
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
            exiting, elapsedMillis(), visibleRowFraction,
            bounds.center.x, bounds.center.y,
            surfaceBounds.center.x, surfaceBounds.center.y, selectedTile,
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
                cameraDistanceCssPx = PhoneStartChoreography.CLASSIC_TILE_PERSPECTIVE_CSS_PX,
            )
        }
    }

    WindowsTileFace(
        tile = tile,
        appIcon = icon,
        backgroundAlpha = tileOpacity,
        innerModifier = if (mode == LauncherUiMode.PHONE_8 && !exiting && !resumeUsesBackMotion) {
            Modifier.drawWithContent {
                val motion = currentMotion()
                val inner = PhoneStartChoreography.sampleInnerEntry(
                    elapsedMillis(), animationIndex, viewportHeightCssPx, density,
                    entryStaggerMultiplier =
                        PhoneStartChoreography.NATIVE_FORWARD_STAGGER_MULTIPLIER,
                )
                val outer = tileProjectionMatrix(size.width, size.height, motion)
                val combined = tileProjectionMatrix(size.width, size.height, motion, inner)
                val correction = nestedProjectionCorrection(outer, combined, size.width, size.height)
                if (correction == null) {
                    drawContent()
                } else {
                    val canvas = drawContext.canvas.nativeCanvas
                    val saveCount = canvas.save()
                    canvas.concat(correction)
                    drawContent()
                    canvas.restoreToCount(saveCount)
                }
            }
        } else Modifier,
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
