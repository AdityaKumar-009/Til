package com.flivoro.tile8auncher.ui.apps

import android.app.Activity
import android.app.KeyguardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.PackageCatalogUpdates
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.features.AppSortMode
import com.flivoro.tile8auncher.features.EnhancedAppInfo
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.UniversalSearchResult
import com.flivoro.tile8auncher.features.hasUsageAccess
import com.flivoro.tile8auncher.features.launchUniversalResult
import com.flivoro.tile8auncher.features.loadEnhancedApps
import com.flivoro.tile8auncher.features.sectionLabel
import com.flivoro.tile8auncher.features.sortEnhancedApps
import com.flivoro.tile8auncher.features.universalNonAppResults
import com.flivoro.tile8auncher.features.usageAccessSettingsIntent
import com.flivoro.tile8auncher.ui.animation.TileCoordinatesHolder
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.elasticHorizontalScroll
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Windows 8.1 Apps view: upward Start navigation, horizontal finite-height app columns and the
 * original Name / Date installed / Most used / Category sort choices. Search expands the surface
 * with Android contacts/settings/calculator/web actions without changing the parent page motion.
 */
@Composable
fun AllAppsScreen(
    sections: List<AppSection>,
    appsRepository: AppsRepository,
    onAppClick: (app: AppInfo, bounds: Rect) -> Unit,
    isAppPinned: (packageName: String) -> Boolean,
    onPinApp: (app: AppInfo) -> Unit,
    onUnpinApp: (packageName: String) -> Unit,
    onOpenAppInfo: (packageName: String) -> Unit,
    onUninstallApp: (packageName: String) -> Unit,
    onNavigateToStart: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    searchFocusRequest: Int = 0,
    searchFocusEnabled: Boolean = true,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedAppForAction by remember { mutableStateOf<AppInfo?>(null) }
    var sortMode by remember { mutableStateOf(AppSortMode.NAME) }
    var showSortChoices by remember { mutableStateOf(false) }
    var privateUnlocked by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var handledSearchRequest by remember { mutableIntStateOf(0) }
    val packageCatalogRevision = PackageCatalogUpdates.revision

    val privateUnlockLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        privateUnlocked = result.resultCode == Activity.RESULT_OK
    }

    val liveSections by produceState(
        initialValue = sections,
        sections,
        packageCatalogRevision,
    ) {
        value = if (packageCatalogRevision == 0) sections else withContext(Dispatchers.IO) {
            AppsRepository(context.applicationContext).getCategorizedApps()
        }
    }

    val allApps = remember(liveSections) { liveSections.flatMap(AppSection::apps).distinctBy(AppInfo::packageName) }
    val enhancedApps by produceState<List<EnhancedAppInfo>>(
        initialValue = emptyList(),
        allApps,
        packageCatalogRevision,
    ) {
        value = withContext(Dispatchers.IO) {
            loadEnhancedApps(context.applicationContext, allApps)
        }
    }

    LaunchedEffect(searchFocusRequest, searchFocusEnabled) {
        if (searchFocusEnabled && searchFocusRequest > handledSearchRequest) {
            searchFocus.requestFocus()
            keyboard?.show()
            handledSearchRequest = searchFocusRequest
        }
    }

    val hiddenPackages = LauncherFeatureStore.hiddenPackages(context)
    val privatePackages = LauncherFeatureStore.privatePackages(context)
    val visibleEnhanced = remember(enhancedApps, hiddenPackages, privatePackages, privateUnlocked) {
        enhancedApps.filter { item ->
            item.app.packageName !in hiddenPackages &&
                (privateUnlocked || item.app.packageName !in privatePackages)
        }
    }
    val sorted = remember(visibleEnhanced, sortMode) { sortEnhancedApps(visibleEnhanced, sortMode) }
    val isNewByPackage = remember(sorted) { sorted.associate { it.app.packageName to it.isNew } }

    val sortedSections = remember(sorted, sortMode) {
        val grouped = linkedMapOf<String, MutableList<AppInfo>>()
        sorted.forEach { item ->
            grouped.getOrPut(sectionLabel(item, sortMode)) { mutableListOf() }.add(item.app)
        }
        grouped.map { (label, apps) -> AppSection(label, apps) }
    }

    val normalizedQuery = searchQuery.trim().lowercase(Locale.getDefault())
    val matchingApps = remember(sorted, normalizedQuery) {
        if (normalizedQuery.isBlank()) emptyList() else sorted
            .filter { it.app.label.lowercase(Locale.getDefault()).contains(normalizedQuery) }
            .map(EnhancedAppInfo::app)
    }
    val universalResults = remember(searchQuery, privateUnlocked) {
        if (searchQuery.isBlank()) emptyList() else universalNonAppResults(context, searchQuery)
    }

    fun unlockPrivateApps() {
        if (privateUnlocked) {
            privateUnlocked = false
            return
        }
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) {
            privateUnlocked = true
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "Private apps",
            "Confirm your device credential to reveal private apps.",
        )
        if (intent != null) privateUnlockLauncher.launch(intent) else privateUnlocked = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isNarrow = maxWidth < 460.dp
                val titleSize = when {
                    maxWidth < 300.dp -> 32.sp
                    maxWidth < 380.dp -> 36.sp
                    else -> 38.sp
                }

                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "Apps",
                            style = WindowsTypography.displayLarge.copy(fontSize = titleSize),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${sortMode.label} ▾",
                            style = WindowsTypography.bodyMedium.copy(fontSize = 14.sp),
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clickable { showSortChoices = !showSortChoices }
                                .padding(vertical = 5.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        if (privatePackages.isNotEmpty()) {
                            Text(
                                text = if (privateUnlocked) "Hide private" else "Private",
                                color = Color.White.copy(alpha = .82f),
                                style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                                modifier = Modifier.clickable { unlockPrivateApps() }.padding(6.dp),
                            )
                        }
                        if (!isNarrow) {
                            SearchField(
                                focusRequester = searchFocus,
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                modifier = Modifier.width(220.dp).height(36.dp),
                            )
                        }
                    }
                    if (isNarrow) {
                        Spacer(Modifier.height(12.dp))
                        SearchField(
                            focusRequester = searchFocus,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                        )
                    }

                    AnimatedVisibility(
                        visible = showSortChoices,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .background(Color(0xE8180424))
                                .border(1.dp, Color(0x55FFFFFF))
                                .padding(5.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            AppSortMode.entries.forEach { mode ->
                                Text(
                                    text = mode.label.removePrefix("by "),
                                    color = Color.White,
                                    style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
                                    modifier = Modifier
                                        .background(
                                            if (mode == sortMode) Color(0xFF6B4AA5) else Color.Transparent,
                                        )
                                        .clickable {
                                            sortMode = mode
                                            showSortChoices = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }

                    if (sortMode == AppSortMode.MOST_USED && !hasUsageAccess(context)) {
                        Text(
                            text = "Enable usage access for accurate Most used sorting",
                            color = Color.White.copy(alpha = .72f),
                            style = WindowsTypography.labelSmall.copy(fontSize = 10.sp),
                            modifier = Modifier
                                .clickable { runCatching { context.startActivity(usageAccessSettingsIntent()) } }
                                .padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (searchQuery.isNotBlank()) {
                    UniversalSearchPanel(
                        matchingApps = matchingApps,
                        universalResults = universalResults,
                        appsRepository = appsRepository,
                        isNewByPackage = isNewByPackage,
                        onAppClick = { app, bounds -> onAppClick(app, bounds) },
                        onAppLongClick = { selectedAppForAction = it },
                        onUniversalClick = { launchUniversalResult(context, it) },
                    )
                } else {
                    val metrics = calculateAllAppsColumnMetrics(
                        availableWidthDp = maxWidth.value,
                        availableHeightDp = maxHeight.value,
                    )
                    val columns = remember(sortedSections, metrics.rowsPerColumn) {
                        packAllAppsColumns(
                            sections = sortedSections,
                            rowsPerColumn = metrics.rowsPerColumn,
                        )
                    }
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxSize().elasticHorizontalScroll().clipToBounds(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.columnGapDp.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        items(
                            items = columns,
                            key = { column -> column.key },
                            contentType = { "all-apps-column" },
                        ) { column ->
                            AllAppsColumn(
                                column = column,
                                appsRepository = appsRepository,
                                columnWidth = metrics.columnWidthDp.dp,
                                rowHeight = metrics.rowHeightDp.dp,
                                isNewByPackage = isNewByPackage,
                                onAppClick = { app, bounds ->
                                    if (selectedAppForAction != null) selectedAppForAction = null
                                    else onAppClick(app, bounds)
                                },
                                onLongClick = { selectedAppForAction = it },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-9).dp)
                        .size(48.dp)
                        .clickable { onNavigateToStart() }
                        .semantics { contentDescription = "Return to Start" },
                    contentAlignment = Alignment.Center,
                ) {
                    MetroIcon(
                        glyph = "arrow_up",
                        color = Color.White.copy(alpha = 0.9f),
                        size = 30.dp,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedAppForAction != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val currentApp = selectedAppForAction
            if (currentApp != null) {
                val isPinned = isAppPinned(currentApp.packageName)
                val icon = rememberAppIcon(appsRepository, currentApp.packageName)
                Surface(
                    color = Color(0xF0180424),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x66FFFFFF)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).background(WindowsColors.Purple.toTileColor()),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (icon != null) Image(icon, currentApp.label, Modifier.size(28.dp))
                                else MetroIcon(glyph = "app", color = Color.White, size = 24.dp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = currentApp.label,
                                style = WindowsTypography.titleMedium.copy(fontSize = 15.sp),
                                color = Color.White,
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetroActionButton(
                                glyph = if (isPinned) "unpin" else "pin",
                                label = if (isPinned) "Unpin" else "Pin to Start",
                                onClick = {
                                    if (isPinned) {
                                        onUnpinApp(currentApp.packageName)
                                        Toast.makeText(context, "Unpinned ${currentApp.label} from Start", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onPinApp(currentApp)
                                        Toast.makeText(context, "Pinned ${currentApp.label} to Start", Toast.LENGTH_SHORT).show()
                                    }
                                    selectedAppForAction = null
                                },
                            )
                            MetroActionButton(
                                glyph = "settings",
                                label = "App info",
                                onClick = {
                                    onOpenAppInfo(currentApp.packageName)
                                    selectedAppForAction = null
                                },
                            )
                            MetroActionButton(
                                glyph = "power",
                                label = "Uninstall",
                                onClick = {
                                    onUninstallApp(currentApp.packageName)
                                    selectedAppForAction = null
                                },
                            )
                            MetroActionButton(
                                glyph = "arrow_down",
                                label = "Cancel",
                                onClick = { selectedAppForAction = null },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UniversalSearchPanel(
    matchingApps: List<AppInfo>,
    universalResults: List<UniversalSearchResult>,
    appsRepository: AppsRepository,
    isNewByPackage: Map<String, Boolean>,
    onAppClick: (AppInfo, Rect) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onUniversalClick: (UniversalSearchResult) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (matchingApps.isNotEmpty()) {
            item {
                Text(
                    "Apps",
                    color = WindowsColors.Magenta.toTileColor(),
                    style = WindowsTypography.titleMedium.copy(fontSize = 17.sp),
                    modifier = Modifier.padding(vertical = 7.dp),
                )
            }
            items(matchingApps, key = { "search-app:${it.packageName}" }) { app ->
                AppListItem(
                    app = app,
                    appsRepository = appsRepository,
                    rowHeight = 52.dp,
                    isNew = isNewByPackage[app.packageName] == true,
                    onClick = { onAppClick(app, it) },
                    onLongClick = { onAppLongClick(app) },
                )
            }
        }
        if (universalResults.isNotEmpty()) {
            item {
                Text(
                    "Results",
                    color = WindowsColors.Magenta.toTileColor(),
                    style = WindowsTypography.titleMedium.copy(fontSize = 17.sp),
                    modifier = Modifier.padding(top = 10.dp, bottom = 7.dp),
                )
            }
            items(universalResults, key = { "universal:${it::class.simpleName}:${it.title}:${it.subtitle}" }) { result ->
                UniversalResultRow(result = result, onClick = { onUniversalClick(result) })
            }
        }
    }
}

@Composable
private fun UniversalResultRow(result: UniversalSearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(Color(0xFF6B4AA5)),
            contentAlignment = Alignment.Center,
        ) {
            val glyph = when (result) {
                is UniversalSearchResult.Contact -> "people"
                is UniversalSearchResult.SystemSetting -> "settings"
                is UniversalSearchResult.Calculation -> "app"
                is UniversalSearchResult.Web -> "search"
            }
            MetroIcon(glyph = glyph, color = Color.White, size = 22.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                color = Color.White,
                style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                result.subtitle,
                color = Color.White.copy(alpha = .62f),
                style = WindowsTypography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchField(
    focusRequester: FocusRequester,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.background(Color(0x33000000)).border(1.dp, Color(0x66FFFFFF)).padding(horizontal = 8.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            modifier = Modifier.weight(1f).focusRequester(focusRequester).semantics { contentDescription = "Search apps, contacts and settings" },
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Search", style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp, color = Color.White.copy(alpha = .5f)))
                }
                inner()
            },
        )
        MetroIcon(glyph = "search", color = Color.White.copy(alpha = .7f), size = 18.dp)
    }
}

@Composable
private fun AllAppsColumn(
    column: AllAppsColumnModel,
    appsRepository: AppsRepository,
    columnWidth: Dp,
    rowHeight: Dp,
    isNewByPackage: Map<String, Boolean>,
    onAppClick: (app: AppInfo, bounds: Rect) -> Unit,
    onLongClick: (app: AppInfo) -> Unit,
) {
    Column(
        modifier = Modifier.width(columnWidth).height(rowHeight * column.items.size),
        verticalArrangement = Arrangement.Top,
    ) {
        column.items.forEach { item ->
            key(item.key) {
                when (item) {
                    is AllAppsColumnItem.LetterHeader -> LetterHeader(item.letter, rowHeight)
                    is AllAppsColumnItem.App -> AppListItem(
                        app = item.app,
                        appsRepository = appsRepository,
                        rowHeight = rowHeight,
                        isNew = isNewByPackage[item.app.packageName] == true,
                        onClick = { onAppClick(item.app, it) },
                        onLongClick = { onLongClick(item.app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LetterHeader(letter: String, rowHeight: Dp) {
    Box(
        modifier = Modifier.fillMaxWidth().height(rowHeight).padding(top = 10.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = letter,
            style = WindowsTypography.headlineMedium.copy(fontSize = 24.sp, fontWeight = FontWeight.Normal),
            color = WindowsColors.Magenta.toTileColor(),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(
    app: AppInfo,
    appsRepository: AppsRepository,
    rowHeight: Dp,
    isNew: Boolean,
    onClick: (bounds: Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    val iconCoordinates = remember { TileCoordinatesHolder() }
    val icon = rememberAppIcon(appsRepository, app.packageName)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .combinedClickable(
                onClick = {
                    val bounds = iconCoordinates.coordinates?.takeIf { it.isAttached }?.boundsInWindow() ?: Rect.Zero
                    onClick(bounds)
                },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ALL_APPS_ICON_BACKGROUND_DP.dp)
                .onGloballyPositioned { iconCoordinates.coordinates = it }
                .background(WindowsColors.Purple.toTileColor()),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Image(icon, app.label, Modifier.size(ALL_APPS_ICON_DP.dp))
            else MetroIcon(glyph = "app", color = Color.White, size = ALL_APPS_ICON_DP.dp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = app.label,
            style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isNew) {
            Text(
                text = "NEW",
                color = WindowsColors.Magenta.toTileColor(),
                style = WindowsTypography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 5.dp, end = 2.dp),
            )
        }
    }
}

@Composable
private fun MetroActionButton(glyph: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.size(42.dp).border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MetroIcon(glyph = glyph, color = Color.White, size = 20.dp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = WindowsTypography.labelSmall.copy(fontSize = 10.sp),
            color = Color.White,
            maxLines = 1,
            softWrap = false,
        )
    }
}
