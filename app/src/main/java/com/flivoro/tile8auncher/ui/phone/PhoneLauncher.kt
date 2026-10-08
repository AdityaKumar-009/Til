package com.flivoro.tile8auncher.ui.phone

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import com.flivoro.tile8auncher.ui.components.WindowsTileFace
import com.flivoro.tile8auncher.ui.components.WindowsWallpaper
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale

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
        return if (mode == LauncherUiMode.PHONE_8 && chosen == TileSize.LARGE) TileSize.WIDE else chosen
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

@Composable
fun PhoneLauncherSurface(
    mode: LauncherUiMode,
    tiles: List<TileModel>,
    sections: List<AppSection>,
    appsRepository: AppsRepository,
    homeRequest: Int,
    wallpaperStyle: Int,
    onLaunch: (TileModel, Rect) -> Unit,
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
    var phoneRevision by remember(mode) { mutableStateOf(0) }
    val densityRevision = LauncherFeatureRuntime.launcherModeRevision
    val columns = remember(mode, densityRevision) { LauncherFeatureStore.phoneSmallColumns(context, mode) }
    val tileOpacity = remember(mode, densityRevision) {
        if (isTen) LauncherFeatureStore.phoneTileOpacity(context) else 1f
    }
    val appsState = rememberLazyListState()
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

    BackHandler(showApps || alphabetOpen || actionCenterOpen || editing != null) {
        when {
            editing != null -> editing = null
            alphabetOpen -> alphabetOpen = false
            actionCenterOpen -> actionCenterOpen = false
            else -> showApps = false
        }
    }

    var horizontalDistance by remember { mutableStateOf(0f) }
    val gestureModifier = Modifier.pointerInput(mode, showApps) {
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isTen) {
            WindowsWallpaper(wallpaperStyle = wallpaperStyle, enabled = false, scrollOffsetPx = { 0f })
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(32.dp).padding(start = 18.dp, end = 18.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (isTen) "▰  Wi-Fi  ▴  ▾" else "▰  ▴  ▾",
                    color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.clickable(enabled = isTen) { actionCenterOpen = !actionCenterOpen })
            }
            AnimatedContent(
                targetState = showApps,
                modifier = Modifier.weight(1f).fillMaxWidth().then(gestureModifier),
                transitionSpec = {
                    val duration = if (isTen) 230 else 330
                    if (targetState) {
                        (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it } +
                            fadeIn(tween(duration / 2))) togetherWith
                            (slideOutHorizontally(tween(duration)) { -it / 5 } + fadeOut(tween(duration / 2)))
                    } else {
                        (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it / 4 } +
                            fadeIn(tween(duration / 2))) togetherWith
                            (slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration / 2)))
                    }
                },
                label = "Phone start apps pivot",
            ) { apps ->
                if (!apps) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(if (isTen) 8.dp else 18.dp))
                        PhoneStartGrid(
                            tiles = phoneTiles,
                            columns = columns,
                            mode = mode,
                            tileOpacity = tileOpacity,
                            appsRepository = appsRepository,
                            onClick = onLaunch,
                            onLongClick = { editing = it },
                        )
                        Spacer(Modifier.height(30.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text("→", color = Color.White, fontSize = 34.sp,
                                modifier = Modifier.clickable { showApps = true }.padding(10.dp))
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 14.dp)) {
                        Text(if (isTen) "All apps" else "apps",
                            fontSize = if (isTen) 30.sp else 45.sp,
                            fontWeight = FontWeight.Light, color = Color.White,
                            modifier = Modifier.padding(top = if (isTen) 16.dp else 26.dp, bottom = 12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                        LazyColumn(state = appsState, modifier = Modifier.fillMaxSize()) {
                            groups.forEach { (letter, groupApps) ->
                                item(key = "letter_$letter") {
                                    Text(letter, color = accent, fontSize = 29.sp, fontWeight = FontWeight.Light,
                                        modifier = Modifier.clickable { alphabetOpen = true }
                                            .padding(vertical = 10.dp, horizontal = 2.dp))
                                }
                                items(groupApps, key = { it.packageName }) { app ->
                                    val icon = rememberAppIcon(appsRepository, app.packageName)
                                    var bounds by remember { mutableStateOf(Rect.Zero) }
                                    Row(
                                        Modifier.fillMaxWidth().height(60.dp)
                                            .onGloballyPositioned { bounds = it.boundsInWindow() }
                                            .clickable { onLaunch(appTile(app), bounds) }
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
                                        Text("⋯", color = Color.LightGray, fontSize = 25.sp,
                                            modifier = Modifier.clickable { editing = "app:${app.packageName}" }
                                                .padding(horizontal = 9.dp))
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
                    modifier = Modifier.clickable { if (showApps) showApps = false else onOpenSettings() }.padding(horizontal = 25.dp))
                Text("⊞", fontSize = 29.sp, color = Color.White,
                    modifier = Modifier.clickable { showApps = false; actionCenterOpen = false }.padding(horizontal = 25.dp))
                Text("⌕", fontSize = 28.sp, color = Color.White,
                    modifier = Modifier.clickable { showApps = true }.padding(horizontal = 25.dp))
            }
        }

        if (actionCenterOpen && isTen) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()
                .background(Color(0xFF212121)).padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(if (isTen) "Expand  ⌄" else "action center",
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
                Text("Android notifications are available in the system notification shade.",
                    color = Color.LightGray, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
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
                                TileSize.entries.filter { mode != LauncherUiMode.PHONE_8 || it != TileSize.LARGE }
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
        val heightRows = slots.maxOfOrNull { it.row + it.rows } ?: 1
        Box(Modifier.fillMaxWidth().height((cell + gap) * heightRows)) {
            tiles.forEachIndexed { index, tile ->
                val pos = slots[index]
                key(tile.id) {
                    PhoneTile(
                        tile = tile, mode = mode, index = index, tileOpacity = tileOpacity, repository = appsRepository,
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
    index: Int,
    tileOpacity: Float,
    repository: AppsRepository,
    modifier: Modifier,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    val progress = remember(tile.id, mode) { Animatable(0f) }
    var pressed by remember(tile.id) { mutableStateOf(false) }
    var bounds by remember(tile.id) { mutableStateOf(Rect.Zero) }
    LaunchedEffect(tile.id, mode) {
        delay((index.coerceAtMost(18) * if (mode == LauncherUiMode.PHONE_8) 24 else 13).toLong())
        progress.animateTo(1f, tween(if (mode == LauncherUiMode.PHONE_8) 390 else 280,
            easing = FastOutSlowInEasing))
    }
    val icon = rememberAppIcon(repository, tile.packageName)
    WindowsTileFace(
        tile = tile,
        appIcon = icon,
        backgroundAlpha = tileOpacity,
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .graphicsLayer {
                alpha = progress.value
                transformOrigin = TransformOrigin(0f, .5f)
                rotationY = if (mode == LauncherUiMode.PHONE_8) -72f * (1f - progress.value) else 0f
                translationY = if (mode == LauncherUiMode.MOBILE_10) 45f * (1f - progress.value) else 0f
                scaleX = if (pressed) .955f else 1f
                scaleY = if (pressed) .955f else 1f
                cameraDistance = 16f * density
            }
            .pointerInput(tile.id) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick(bounds) },
                    onLongPress = { onLongClick() },
                )
            },
    )
}
