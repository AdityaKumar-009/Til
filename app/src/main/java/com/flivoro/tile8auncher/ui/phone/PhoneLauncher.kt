package com.flivoro.tile8auncher.ui.phone

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import com.flivoro.tile8auncher.ui.components.WindowsTileFace
import com.flivoro.tile8auncher.ui.components.WindowsWallpaper
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale
import kotlin.math.roundToInt

/** Phone Start coordinates are independent from Desktop's horizontal tile-group coordinates. */
internal data class PhoneTileSlot(val column: Int, val row: Int, val columns: Int, val rows: Int)

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
) {
    val context = LocalContext.current
    val isTen = mode == LauncherUiMode.MOBILE_10
    val accent = StartPersonalization.accentColor
    val catalog = remember(sections) { sections.flatMap(AppSection::apps).distinctBy(AppInfo::packageName) }
    val byPackage = remember(catalog) { catalog.associateBy(AppInfo::packageName) }
    val desktopById = remember(tiles) { tiles.associateBy(TileModel::id) }
    var order by remember(mode) { mutableStateOf(PhoneLayoutStore.order(context, mode, tiles)) }
    var editing by remember(mode) { mutableStateOf<String?>(null) }
    var showApps by remember(mode) { mutableStateOf(false) }
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
    val listPhase = PhoneMotionPhase.FORWARD_OUT
    // One clock owns tiles, wallpaper and completion. A wall-clock delay in MainActivity
    // can expire before Compose has even presented the first animation frame.
    val duration = if (isLaunching && showApps) {
        maxOf(PhoneMotionTimeline.totalMillis(mode, listPhase, 6),
            if (isTen) MobileStartMotion.EXIT_TOTAL_MS else 0)
    } else PhoneStartChoreography.totalMillis(mode, exiting = isLaunching)
    val motionClock = remember(mode, entranceRequest, homeRequest, isLaunching) { Animatable(0f) }
    val exitTileId = remember(isLaunching) { launchingTileId }
    val latestExitFinished by rememberUpdatedState(onExitFinished)
    LaunchedEffect(mode, entranceRequest, homeRequest, isLaunching) {
        snapshotFlow { paneBounds.width > 0f && surfaceBounds.height > 0f }.first { it }
        motionClock.animateTo(duration.toFloat(), tween(duration, easing = LinearEasing))
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
            showApps = false
            alphabetOpen = false
            editing = null
            actionCenterOpen = false
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
                alpha = MobileStartMotion.wallpaperAlpha(isLaunching, motionClock.value.roundToInt())
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
                        alpha = MobileStartMotion.wallpaperAlpha(isLaunching, motionClock.value.roundToInt())
                    },
                    horizontalArrangement = Arrangement.End) {
                    Text("⌄", color = Color.White, fontSize = 20.sp,
                        modifier = Modifier.clickable(enabled = interactionEnabled && !isLaunching) {
                            actionCenterOpen = !actionCenterOpen
                        })
                }
            }
            AnimatedContent(
                targetState = showApps,
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .onGloballyPositioned { paneBounds = it.boundsInWindow() }.then(gestureModifier),
                transitionSpec = {
                    // 60fps reference 1000197576.mp4, 134.90–135.17s:
                    // the entire Start/Apps panorama moves horizontally, with no
                    // per-row fade or separate turnstile. Two full-width panes translate.
                    val duration = if (isTen) 260 else 270
                    val panEase = androidx.compose.animation.core.Easing {
                        PhoneMotionTimeline.exponentialEaseOut6(it)
                    }
                    if (targetState) {
                        slideInHorizontally(tween(duration, easing = panEase)) { it } togetherWith
                            slideOutHorizontally(tween(duration, easing = panEase)) { -it }
                    } else {
                        slideInHorizontally(tween(duration, easing = panEase)) { -it } togetherWith
                            slideOutHorizontally(tween(duration, easing = panEase)) { it }
                    }
                },
                label = "Phone start apps pivot",
            ) { apps ->
                if (!apps) {
                    Column(Modifier.fillMaxSize().verticalScroll(startScroll, enabled = interactionEnabled && !isLaunching)) {
                        Spacer(Modifier.height(if (isTen) 8.dp else 18.dp))
                        PhoneStartGrid(
                            tiles = phoneTiles,
                            columns = columns,
                            scrollOffsetPx = startScroll.value,
                            elapsedMillis = { motionClock.value.roundToInt() },
                            surfaceBounds = surfaceBounds,
                            viewportHeightPx = paneBounds.height,
                            interactionEnabled = interactionEnabled,
                            launchingTileId = exitTileId,
                            isLaunching = isLaunching,
                            mode = mode,
                            tileOpacity = tileOpacity,
                            appsRepository = appsRepository,
                            onClick = { tile, bounds -> onLaunch(tile, bounds, LaunchOrigin.START) },
                            onLongClick = { editing = it },
                        )
                        Spacer(Modifier.height(30.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).graphicsLayer {
                                alpha = if (isTen) MobileStartMotion.wallpaperAlpha(
                                    isLaunching, motionClock.value.roundToInt(),
                                ) else if (isLaunching && motionClock.value >= duration) 0f else 1f
                            },
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text("→", color = Color.White, fontSize = 34.sp,
                                modifier = Modifier.clickable(enabled = interactionEnabled && !isLaunching) { showApps = true }.padding(10.dp))
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 14.dp)
                        .graphicsLayer {
                            alpha = if (isLaunching && motionClock.value >=
                                PhoneMotionTimeline.totalMillis(mode, listPhase, 6)) 0f else 1f
                        }) {
                        if (!isTen && !phone8SearchVisible) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
                                Text("⌕", color = Color.White, fontSize = 30.sp,
                                    modifier = Modifier.clickable { phone8SearchVisible = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
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
                        Spacer(Modifier.height(12.dp))
                        val filtered = remember(visibleApps, search) {
                            visibleApps.filter { it.label.contains(search, ignoreCase = true) }
                        }
                        val groups = remember(filtered) {
                            filtered.groupBy {
                                it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter)?.toString() ?: "#"
                            }.toSortedMap()
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
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        LazyColumn(state = appsState, modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = interactionEnabled && !isLaunching) {
                            groups.forEach { (letter, groupApps) ->
                                item(key = "letter_$letter") {
                                    Text(letter, color = accent, fontSize = 29.sp, fontWeight = FontWeight.Light,
                                        modifier = Modifier.clickable { alphabetOpen = true }
                                            .padding(vertical = 10.dp, horizontal = 2.dp))
                                }
                                itemsIndexed(groupApps, key = { _, app -> app.packageName }) { appIndex, app ->
                                    val icon = rememberAppIcon(appsRepository, app.packageName)
                                    var bounds by remember { mutableStateOf(Rect.Zero) }
                                    val ordinal = ((positions[letter] ?: 0) + appIndex + 1 -
                                        appsState.firstVisibleItemIndex).coerceIn(0, 6)
                                    // The app list is stationary within its horizontally
                                    // moving pane; only actual app launch feathers the rows.
                                    Row(
                                        Modifier.fillMaxWidth().height(60.dp)
                                            .graphicsLayer {
                                                // Draw-layer read avoids relaying every animation
                                                // tick through lazy-row recomposition.
                                                val rowMotion = if (isLaunching) PhoneMotionTimeline.sample(
                                                    mode, listPhase, motionClock.value.roundToInt(),
                                                    ordinal, selectedTile = exitTileId == "phone_app_${app.packageName}",
                                                ) else PhoneMotionFrame(1f, 0f, 0f, 1f, .5f)
                                                alpha = rowMotion.alpha
                                                rotationY = rowMotion.rotationY
                                                translationY = rowMotion.offsetYPx * density
                                                scaleX = rowMotion.scale
                                                scaleY = rowMotion.scale
                                                transformOrigin = TransformOrigin(rowMotion.pivotX, .5f)
                                                cameraDistance = maxOf(900f, 2f * bounds.width, 2f * bounds.height)
                                            }
                                            .onGloballyPositioned { bounds = it.boundsInWindow() }
                                            .combinedClickable(
                                                enabled = interactionEnabled && !isLaunching,
                                                onClick = { onLaunch(appTile(app), bounds, LaunchOrigin.ALL_APPS) },
                                                onLongClick = { editing = "app:${app.packageName}" },
                                            )
                                            .padding(vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.size(45.dp).background(accent), contentAlignment = Alignment.Center) {
                                            if (icon != null) androidx.compose.foundation.Image(
                                                bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                                            else Text(app.label.take(1), color = Color.White, fontSize = 22.sp)
                                        }
                                        Text(app.label, color = Color.White, fontSize = 18.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 14.dp))
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
                    modifier = Modifier.clickable(enabled = interactionEnabled && !isLaunching) { showApps = true }.padding(horizontal = 25.dp))
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
    viewportHeightPx: Float,
    interactionEnabled: Boolean,
    launchingTileId: String?,
    isLaunching: Boolean,
    mode: LauncherUiMode,
    tileOpacity: Float,
    appsRepository: AppsRepository,
    onClick: (TileModel, Rect) -> Unit,
    onLongClick: (String) -> Unit,
) {
    val slots = remember(tiles, columns) { packPhoneTiles(tiles.map(TileModel::size), columns) }
    val gap = 4.dp
    val side = 10.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - side * 2 - gap * (columns - 1)) / columns
        val cellStepPx = with(LocalDensity.current) { (cell + gap).toPx() }.coerceAtLeast(1f)
        val topInsetPx = with(LocalDensity.current) { (if (mode == LauncherUiMode.MOBILE_10) 8.dp else 18.dp).toPx() }
        val visibleTops = slots.mapNotNull { slot ->
            val top = topInsetPx + slot.row * cellStepPx - scrollOffsetPx
            if (top + slot.rows * cellStepPx > 0f && top < viewportHeightPx) top else null
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
                        screenRow = (rowFraction * 5f).roundToInt(),
                        visibleRowFraction = rowFraction,
                        surfaceBounds = surfaceBounds,
                        interactionEnabled = interactionEnabled,
                        column = pos.column,
                        columns = columns,
                        exiting = isLaunching,
                        elapsedMillis = elapsedMillis,
                        selectedTile = launchingTileId == tile.id,
                        tileOpacity = tileOpacity, repository = appsRepository,
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
    screenRow: Int,
    visibleRowFraction: Float,
    surfaceBounds: Rect,
    interactionEnabled: Boolean,
    column: Int,
    columns: Int,
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
    val icon = rememberAppIcon(repository, tile.packageName)
    WindowsTileFace(
        tile = tile,
        appIcon = icon,
        backgroundAlpha = tileOpacity,
        modifier = modifier
            .onGloballyPositioned {
                // boundsInWindow clips partially visible tiles, moving their apparent centre.
                // The zoom anchor must use the full, untransformed tile geometry.
                bounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
            }
            .graphicsLayer {
                val motion = if (mode == LauncherUiMode.MOBILE_10) MobileStartMotion.sample(
                    exiting, elapsedMillis(), visibleRowFraction,
                    bounds.center.x, bounds.center.y,
                    surfaceBounds.center.x, surfaceBounds.center.y, selectedTile,
                ) else PhoneStartChoreography.sample(
                    mode = mode, exiting = exiting, elapsedMillis = elapsedMillis(),
                    screenRow = screenRow, column = column, columns = columns,
                    selected = selectedTile,
                )
                alpha = motion.alpha
                rotationY = motion.rotationY
                val unitScale = if (mode == LauncherUiMode.MOBILE_10) 1f else density
                translationX = motion.translationXPx * unitScale
                translationY = motion.translationYPx * unitScale
                scaleX = motion.scale
                scaleY = motion.scale
                transformOrigin = TransformOrigin(motion.pivotX, .5f)
                cameraDistance = maxOf(900f, 2f * bounds.width, 2f * bounds.height)
            }
            .phoneToolkitTilePress(
                enabled = interactionEnabled && !exiting,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}
