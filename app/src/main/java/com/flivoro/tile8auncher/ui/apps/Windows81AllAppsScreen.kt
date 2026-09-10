package com.flivoro.tile8auncher.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.Windows81AppsSortMode
import com.flivoro.tile8auncher.data.Windows81ShellPreferences
import com.flivoro.tile8auncher.ui.animation.TileCoordinatesHolder
import com.flivoro.tile8auncher.ui.animation.Windows81Motion
import com.flivoro.tile8auncher.ui.animation.metroTilePress
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.elasticHorizontalScroll
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Windows 8.1 Apps view.
 *
 * A column is a finite vertical strip, NOT one alphabetical section. Section
 * headings continue immediately after the preceding section whenever a header
 * plus at least one app fit. This reproduces the real 8.1 Apps screenshots
 * where A/B/C/etc. can all appear in one visual column.
 */
@Composable
fun Windows81AllAppsScreen(
    apps: List<AppInfo>,
    appsRepository: AppsRepository,
    shellPreferences: Windows81ShellPreferences,
    sortMode: Windows81AppsSortMode,
    onSortModeChange: (Windows81AppsSortMode) -> Unit,
    onAppClick: (app: AppInfo, bounds: Rect) -> Unit,
    isAppPinned: (String) -> Boolean,
    onPinApp: (AppInfo) -> Unit,
    onUnpinApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onUninstallApp: (String) -> Unit,
    onNavigateToStart: () -> Unit,
    onAppSeen: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    var query by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var semanticZoom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val sortedSections = remember(apps, sortMode, query) {
        val filtered = if (query.isBlank()) apps else {
            val q = query.trim().lowercase(Locale.getDefault())
            apps.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
        }
        buildWindows81Sections(filtered, sortMode, shellPreferences)
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val narrow = maxWidth < 470.dp
                if (narrow) {
                    Column {
                        AppsTitleRow(sortMode, sortMenu, { sortMenu = it }, onSortModeChange)
                        Spacer(Modifier.height(10.dp))
                        AppsSearchField(query, { query = it }, Modifier.fillMaxWidth().height(38.dp))
                    }
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) {
                            AppsTitleRow(sortMode, sortMenu, { sortMenu = it }, onSortModeChange)
                        }
                        AppsSearchField(query, { query = it }, Modifier.width(230.dp).height(38.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                val metrics = remember(maxWidth, maxHeight) {
                    calculateAllAppsColumnMetrics(
                        availableWidthDp = maxWidth.value,
                        availableHeightDp = maxHeight.value,
                    )
                }
                val rowHeight = metrics.rowHeightDp.dp
                val appSections = remember(sortedSections) {
                    sortedSections.map { AppSection(letter = it.name, apps = it.apps) }
                }
                val columns = remember(appSections, metrics.rowsPerColumn) {
                    packAllAppsColumns(appSections, metrics.rowsPerColumn)
                }
                val zoomProgress by animateFloatAsState(
                    targetValue = if (semanticZoom) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = Windows81Motion.SemanticZoomDurationMillis,
                        easing = Windows81Motion.SemanticZoomEase,
                    ),
                    label = "Windows81AppsSemanticZoom",
                )

                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .elasticHorizontalScroll()
                        .graphicsLayer {
                            val scale = 1f - (1f - Windows81Motion.SemanticZoomFactor) * zoomProgress
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - zoomProgress
                        },
                    horizontalArrangement = Arrangement.spacedBy(metrics.columnGapDp.dp),
                ) {
                    items(columns, key = { it.key }) { column ->
                        AppsColumn(
                            column = column,
                            columnWidth = metrics.columnWidthDp.dp,
                            appsRepository = appsRepository,
                            shellPreferences = shellPreferences,
                            rowHeight = rowHeight,
                            onSectionClick = { semanticZoom = true },
                            onAppClick = { app, bounds ->
                                if (selectedApp != null) selectedApp = null
                                else {
                                    onAppSeen(app.packageName)
                                    onAppClick(app, bounds)
                                }
                            },
                            onLongClick = { selectedApp = it },
                        )
                    }
                }

                if (zoomProgress > 0.001f) {
                    AppsSemanticZoom(
                        sections = sortedSections,
                        enabled = semanticZoom,
                        progress = zoomProgress,
                        onSectionClick = { section ->
                            val target = columns.indexOfFirst { column ->
                                column.items.any { item ->
                                    item is AllAppsColumnItem.LetterHeader && item.letter == section
                                }
                            }.coerceAtLeast(0)
                            semanticZoom = false
                            scope.launch { listState.animateScrollToItem(target) }
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).border(1.dp, Color.White.copy(alpha = 0.6f)).clickable {
                        semanticZoom = !semanticZoom
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("−", color = Color.White, fontSize = 22.sp) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(46.dp).clickable(onClick = onNavigateToStart),
                    contentAlignment = Alignment.Center,
                ) {
                    MetroIcon("arrow_up", color = Color.White.copy(alpha = 0.92f), size = 40.dp)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(34.dp))
            }
        }

        AnimatedVisibility(
            visible = selectedApp != null,
            enter = slideInVertically(
                animationSpec = tween(Windows81Motion.EdgeUiDurationMillis, easing = Windows81Motion.Fluid),
                initialOffsetY = { it },
            ),
            exit = slideOutVertically(
                animationSpec = tween(Windows81Motion.EdgeUiDurationMillis, easing = Windows81Motion.Fluid),
                targetOffsetY = { it },
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            selectedApp?.let { app ->
                AppCommandBar(
                    app = app,
                    appsRepository = appsRepository,
                    isPinned = isAppPinned(app.packageName),
                    onPinToggle = {
                        if (isAppPinned(app.packageName)) onUnpinApp(app.packageName) else onPinApp(app)
                        selectedApp = null
                    },
                    onAppInfo = { onOpenAppInfo(app.packageName); selectedApp = null },
                    onUninstall = { onUninstallApp(app.packageName); selectedApp = null },
                    onCancel = { selectedApp = null },
                )
            }
        }
    }
}

@Composable
private fun AppsTitleRow(
    sortMode: Windows81AppsSortMode,
    sortMenuVisible: Boolean,
    onSortMenuVisible: (Boolean) -> Unit,
    onSortModeChange: (Windows81AppsSortMode) -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("Apps", style = WindowsTypography.displayLarge.copy(fontSize = 38.sp), color = Color.White)
        Spacer(Modifier.width(14.dp))
        Box {
            Text(
                text = "${sortMode.label}  ▾",
                style = WindowsTypography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.padding(bottom = 6.dp).clickable { onSortMenuVisible(true) },
            )
            DropdownMenu(
                expanded = sortMenuVisible,
                onDismissRequest = { onSortMenuVisible(false) },
                modifier = Modifier.background(Color(0xFF25102F)),
            ) {
                Windows81AppsSortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, color = Color.White, fontSize = 13.sp) },
                        onClick = { onSortModeChange(mode); onSortMenuVisible(false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppsSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.background(Color.White).border(1.dp, Color(0xFF777777)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFF222222), fontSize = 13.sp),
            cursorBrush = SolidColor(Color(0xFF222222)),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isBlank()) Text("Search", color = Color(0xFF777777), fontSize = 13.sp)
                inner()
            },
        )
        MetroIcon("search", color = Color(0xFF333333), size = 18.dp)
    }
}

private data class Windows81AppSection(val name: String, val apps: List<AppInfo>)

private fun buildWindows81Sections(
    apps: List<AppInfo>,
    mode: Windows81AppsSortMode,
    prefs: Windows81ShellPreferences,
): List<Windows81AppSection> = when (mode) {
    Windows81AppsSortMode.NAME -> apps
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
        .groupBy {
            it.label.trim().firstOrNull()?.uppercaseChar()?.takeIf { c -> c in 'A'..'Z' }?.toString() ?: "#"
        }
        .entries
        .sortedWith(compareBy<Map.Entry<String, List<AppInfo>>> { it.key == "#" }.thenBy { it.key })
        .map { Windows81AppSection(it.key, it.value) }

    Windows81AppsSortMode.DATE_INSTALLED -> apps
        .sortedByDescending { it.firstInstallTime }
        .groupBy { installDateBucket(it.firstInstallTime) }
        .entries
        .sortedWith(compareBy { DATE_BUCKET_ORDER.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } })
        .map { Windows81AppSection(it.key, it.value) }

    Windows81AppsSortMode.MOST_USED -> {
        val sorted = apps.sortedWith(
            compareByDescending<AppInfo> { prefs.launchCount(it.packageName) }
                .thenByDescending { prefs.lastLaunch(it.packageName) }
                .thenBy { it.label.lowercase(Locale.getDefault()) },
        )
        val used = sorted.filter { prefs.launchCount(it.packageName) > 0 }
        val never = sorted.filter { prefs.launchCount(it.packageName) == 0 }
        buildList {
            if (used.isNotEmpty()) add(Windows81AppSection("Most used", used))
            if (never.isNotEmpty()) add(Windows81AppSection("Other", never))
        }
    }

    Windows81AppsSortMode.CATEGORY -> apps
        .groupBy { prefs.categoryLabel(it.packageName) }
        .toSortedMap()
        .map { (name, value) ->
            Windows81AppSection(name, value.sortedBy { it.label.lowercase(Locale.getDefault()) })
        }
}

private fun installDateBucket(time: Long): String {
    if (time <= 0L) return "Earlier"
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { timeInMillis = time }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(now, date)) return "Today"

    val startOfWeek = (now.clone() as Calendar).apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (!date.before(startOfWeek)) return "This week"
    val lastWeek = (startOfWeek.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
    if (!date.before(lastWeek)) return "Last week"
    if (date.get(Calendar.YEAR) == now.get(Calendar.YEAR) && date.get(Calendar.MONTH) == now.get(Calendar.MONTH)) {
        return "Earlier this month"
    }
    val lastMonth = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    if (date.get(Calendar.YEAR) == lastMonth.get(Calendar.YEAR) && date.get(Calendar.MONTH) == lastMonth.get(Calendar.MONTH)) {
        return "Last month"
    }
    return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(time))
}

private val DATE_BUCKET_ORDER = listOf("Today", "This week", "Last week", "Earlier this month", "Last month")

@Composable
private fun AppsColumn(
    column: AllAppsColumnModel,
    columnWidth: Dp,
    appsRepository: AppsRepository,
    shellPreferences: Windows81ShellPreferences,
    rowHeight: Dp,
    onSectionClick: (String) -> Unit,
    onAppClick: (AppInfo, Rect) -> Unit,
    onLongClick: (AppInfo) -> Unit,
) {
    Column(Modifier.width(columnWidth)) {
        column.items.forEach { item ->
            key(item.key) {
                when (item) {
                    is AllAppsColumnItem.LetterHeader -> Box(
                        Modifier.fillMaxWidth().height(rowHeight).clickable { onSectionClick(item.letter) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            item.letter,
                            style = WindowsTypography.headlineMedium.copy(
                                fontSize = if (item.letter.length <= 3) 24.sp else 16.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            color = WindowsColors.Magenta.toTileColor(),
                            maxLines = 2,
                        )
                    }

                    is AllAppsColumnItem.App -> AppRow(
                        app = item.app,
                        appsRepository = appsRepository,
                        isNew = shellPreferences.isAppNew(item.app.packageName),
                        rowHeight = rowHeight,
                        onClick = { onAppClick(item.app, it) },
                        onLongClick = { onLongClick(item.app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    appsRepository: AppsRepository,
    isNew: Boolean,
    rowHeight: Dp,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    val iconCoordinates = remember { TileCoordinatesHolder() }
    val icon = rememberAppIcon(appsRepository, app.packageName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .metroTilePress(
                onClick = {
                    onClick(
                        iconCoordinates.coordinates
                            ?.takeIf { coordinates -> coordinates.isAttached }
                            ?.boundsInWindow()
                            ?: Rect.Zero,
                    )
                },
                onLongClick = onLongClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(ALL_APPS_ICON_BACKGROUND_DP.dp)
                .onGloballyPositioned { iconCoordinates.coordinates = it }
                .background(WindowsColors.Purple.toTileColor()),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(ALL_APPS_ICON_DP.dp),
                )
            } else {
                MetroIcon("app", color = Color.White, size = 34.dp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            app.label,
            color = Color.White,
            style = WindowsTypography.bodyMedium.copy(fontSize = 14.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isNew) {
            Text(
                "NEW",
                color = Color(0xFFFFA5D8),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
    }
}

@Composable
private fun AppsSemanticZoom(
    sections: List<Windows81AppSection>,
    enabled: Boolean,
    progress: Float,
    onSectionClick: (String) -> Unit,
) {
    val incomingStartScale = 1f / Windows81Motion.SemanticZoomFactor
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress
                val scale = incomingStartScale - (incomingStartScale - 1f) * progress
                scaleX = scale
                scaleY = scale
            }
            .background(Color(0xEB2A0A3A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.map { it.name }.distinct().chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { section ->
                        Box(
                            Modifier.size(58.dp).background(Color(0xFF5A1780)).clickable(enabled = enabled) {
                                onSectionClick(section)
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (section.length <= 3) section else section.take(4),
                                color = Color.White,
                                fontSize = if (section.length <= 3) 20.sp else 10.sp,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCommandBar(
    app: AppInfo,
    appsRepository: AppsRepository,
    isPinned: Boolean,
    onPinToggle: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val icon = rememberAppIcon(appsRepository, app.packageName)
    Surface(color = Color(0xF3180B20), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x55FFFFFF))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(WindowsColors.Purple.toTileColor()),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) Image(icon, contentDescription = app.label, modifier = Modifier.size(28.dp))
                else MetroIcon("app", color = Color.White, size = 24.dp)
            }
            Spacer(Modifier.width(10.dp))
            Text(app.label, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            AppsCommand(
                if (isPinned) "⊖" else "⊕",
                if (isPinned) "Unpin from Start" else "Pin to Start",
                onPinToggle,
            )
            AppsCommand("ⓘ", "App info", onAppInfo)
            AppsCommand("×", "Uninstall", onUninstall)
            AppsCommand("⌄", "Cancel", onCancel)
        }
    }
}

@Composable
private fun AppsCommand(icon: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 7.dp),
    ) {
        Box(Modifier.size(32.dp).border(2.dp, Color.White), contentAlignment = Alignment.Center) {
            Text(icon, color = Color.White, fontSize = 15.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White, fontSize = 8.sp, maxLines = 1)
    }
}
