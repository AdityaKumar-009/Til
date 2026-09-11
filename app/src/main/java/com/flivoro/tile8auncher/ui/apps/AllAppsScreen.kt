package com.flivoro.tile8auncher.ui.apps

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import com.flivoro.tile8auncher.ui.components.elasticHorizontalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.PackageCatalogUpdates
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.ui.animation.TileCoordinatesHolder
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The All Apps surface is a finite-height strip. Every lazy item is a complete
 * alphabetical column, which keeps Compose's scroll work proportional to the
 * columns currently entering the viewport rather than to every app row.
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
    val context = LocalContext.current
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var handledSearchRequest by remember { mutableIntStateOf(0) }
    val packageCatalogRevision = PackageCatalogUpdates.revision

    // Parent-provided sections remain the zero-cost normal path. Only a real Android package
    // change triggers a fresh PackageManager scan; the new repository is lightweight and does
    // not eagerly decode icons. This preserves the startup cache optimization while keeping
    // All Apps immediately correct after install/uninstall/enable/disable/update events.
    val liveSections by produceState(
        initialValue = sections,
        sections,
        packageCatalogRevision,
    ) {
        value = if (packageCatalogRevision == 0) {
            sections
        } else {
            withContext(Dispatchers.IO) {
                AppsRepository(context.applicationContext).getCategorizedApps()
            }
        }
    }

    LaunchedEffect(searchFocusRequest, searchFocusEnabled) {
        if (searchFocusEnabled && searchFocusRequest > handledSearchRequest) {
            searchFocus.requestFocus()
            keyboard?.show()
            handledSearchRequest = searchFocusRequest
        }
    }

    val filteredSections = remember(liveSections, searchQuery) {
        if (searchQuery.isBlank()) {
            liveSections
        } else {
            val query = searchQuery.trim().lowercase(Locale.getDefault())
            liveSections.mapNotNull { section ->
                val matchingApps = section.apps.filter { app ->
                    app.label.lowercase(Locale.getDefault()).contains(query)
                }
                matchingApps.takeIf { it.isNotEmpty() }?.let { section.copy(apps = it) }
            }
        }
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

                if (isNarrow) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "by name ▾",
                                style = WindowsTypography.bodyMedium.copy(fontSize = 14.sp),
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 5.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        SearchField(
                            focusRequester = searchFocus,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
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
                                text = "by name ▾",
                                style = WindowsTypography.bodyMedium.copy(fontSize = 14.sp),
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 5.dp),
                            )
                        }

                        SearchField(
                            focusRequester = searchFocus,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier
                                .width(220.dp)
                                .height(36.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val metrics = calculateAllAppsColumnMetrics(
                    availableWidthDp = maxWidth.value,
                    availableHeightDp = maxHeight.value,
                )
                val columns = remember(filteredSections, metrics.rowsPerColumn) {
                    packAllAppsColumns(
                        sections = filteredSections,
                        rowsPerColumn = metrics.rowsPerColumn,
                    )
                }

                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .elasticHorizontalScroll()
                        .clipToBounds(),
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
                            onAppClick = { app, bounds ->
                                if (selectedAppForAction != null) {
                                    selectedAppForAction = null
                                } else {
                                    onAppClick(app, bounds)
                                }
                            },
                            onLongClick = { app -> selectedAppForAction = app },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mirror Start's lower-left navigation affordance exactly: same 48 dp target,
            // -9 dp optical offset, 30 dp glyph and bottom inset. Only direction changes.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
            ) {
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

        // Windows 8.1 bottom action bar, shown after a long press.
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x66FFFFFF)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(WindowsColors.Purple.toTileColor()),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (icon != null) {
                                    Image(
                                        bitmap = icon,
                                        contentDescription = currentApp.label,
                                        modifier = Modifier.size(28.dp),
                                    )
                                } else {
                                    MetroIcon(glyph = "app", color = Color.White, size = 24.dp)
                                }
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
                                        Toast.makeText(
                                            context,
                                            "Unpinned ${currentApp.label} from Start",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        onPinApp(currentApp)
                                        Toast.makeText(
                                            context,
                                            "Pinned ${currentApp.label} to Start",
                                            Toast.LENGTH_SHORT,
                                        ).show()
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
private fun SearchField(
    focusRequester: FocusRequester,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color(0x33000000))
            .border(1.dp, Color(0x66FFFFFF))
            .padding(horizontal = 8.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            modifier = Modifier.weight(1f).focusRequester(focusRequester)
                .semantics { contentDescription = "Search apps" },
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search",
                        style = WindowsTypography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.5f),
                        ),
                    )
                }
                innerTextField()
            },
        )
        MetroIcon(
            glyph = "search",
            color = Color.White.copy(alpha = 0.7f),
            size = 18.dp,
        )
    }
}

@Composable
private fun AllAppsColumn(
    column: AllAppsColumnModel,
    appsRepository: AppsRepository,
    columnWidth: Dp,
    rowHeight: Dp,
    onAppClick: (app: AppInfo, bounds: Rect) -> Unit,
    onLongClick: (app: AppInfo) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(columnWidth)
            .height(rowHeight * column.items.size),
        verticalArrangement = Arrangement.Top,
    ) {
        column.items.forEach { item ->
            key(item.key) {
                when (item) {
                    is AllAppsColumnItem.LetterHeader -> {
                        LetterHeader(letter = item.letter, rowHeight = rowHeight)
                    }

                    is AllAppsColumnItem.App -> {
                        AppListItem(
                            app = item.app,
                            appsRepository = appsRepository,
                            rowHeight = rowHeight,
                            onClick = { bounds -> onAppClick(item.app, bounds) },
                            onLongClick = { onLongClick(item.app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterHeader(
    letter: String,
    rowHeight: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(top = 10.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = letter,
            style = WindowsTypography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
            ),
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
                    val bounds = iconCoordinates.coordinates
                        ?.takeIf { it.isAttached }
                        ?.boundsInWindow()
                        ?: Rect.Zero
                    onClick(bounds)
                },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 2.dp),
    ) {
        // Keep the launch source tied to the icon square. The label remains
        // clickable, but the flip starts from the same visual source as Start.
        Box(
            modifier = Modifier
                .size(ALL_APPS_ICON_BACKGROUND_DP.dp)
                .onGloballyPositioned { coordinates ->
                    iconCoordinates.coordinates = coordinates
                }
                .background(WindowsColors.Purple.toTileColor()),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(ALL_APPS_ICON_DP.dp),
                )
            } else {
                MetroIcon(glyph = "app", color = Color.White, size = ALL_APPS_ICON_DP.dp)
            }
        }

        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = app.label,
            style = WindowsTypography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetroActionButton(
    glyph: String,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(2.dp, Color.White, CircleShape),
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
