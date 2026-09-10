package com.flivoro.tile8auncher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.ui.animation.CharmsMotion
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val CharmsDecelerateEasing = Easing { CharmsMotion.decelerate(it) }

private val CharmsBlack = Color(0xFF111111)
private val CharmsWhite = Color(0xFFF2F2F2)
private val CharmsLightGrey = Color(0xFFD8D8D8)
private val CharmsMutedGrey = Color(0xFFBFB6D1)
private val CharmsPurple = Color(0xFF1C005D)
private val StartPurple = Color(0xFF32106B)
private val StartPurpleGlint = Color(0xFF7650A8)
private val SearchFieldText = Color(0xFF1C005D)

/**
 * Windows 8.1-style Charms surface.
 *
 * The established rail/pane timing constants remain unchanged. This pass corrects the interaction
 * hierarchy around them: edge commitment, rail-to-pane replacement, clock lifetime, native-looking
 * pane headers, and the Start-specific Settings quick controls seen in Windows 8.1 references.
 */
@Composable
fun WindowsCharmsOverlay(
    visible: Boolean,
    apps: List<AppInfo>,
    appsRepository: AppsRepository,
    onAppClick: (AppInfo, Rect) -> Unit,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAddApps: () -> Unit,
    onDevices: () -> Unit,
    onPower: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnAppClick = rememberUpdatedState(onAppClick)
    val latestOnDismiss = rememberUpdatedState(onDismiss)
    val latestOnStart = rememberUpdatedState(onStart)
    val latestOnSearch = rememberUpdatedState(onSearch)
    val latestOnSettings = rememberUpdatedState(onSettings)
    val latestOnAddApps = rememberUpdatedState(onAddApps)
    val latestOnDevices = rememberUpdatedState(onDevices)
    val latestOnPower = rememberUpdatedState(onPower)
    var panel by remember { mutableStateOf<CharmPanel?>(null) }
    var minuteTick by remember { mutableStateOf(currentEpochMinute()) }
    val initialFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(visible) {
        if (!visible) {
            panel = null
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            return@LaunchedEffect
        }

        minuteTick = currentEpochMinute()
        while (isActive) {
            val untilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(untilNextMinute.coerceIn(1_000L, 60_000L))
            minuteTick = currentEpochMinute()
        }
    }

    LaunchedEffect(visible, panel) {
        if (!visible) return@LaunchedEffect
        withFrameNanos { }
        runCatching {
            when (panel) {
                null -> initialFocusRequester.requestFocus()
                CharmPanel.Search -> searchFocusRequester.requestFocus()
                else -> Unit
            }
        }
    }

    BackHandler(enabled = visible) {
        if (panel != null) {
            panel = null
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        } else {
            latestOnDismiss.value()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (visible) Modifier.semantics { isTraversalGroup = true }
                else Modifier,
            ),
    ) {
        // Preserve the existing Android-adapted physical size instead of scaling desktop pixels
        // literally; the rail remains finger-target friendly on narrow phones.
        val railWidth = if (minOf(maxWidth, maxHeight) >= 600.dp) 100.dp else 86.dp
        val availableClockWidth = (maxWidth - railWidth - 32.dp).coerceAtLeast(0.dp)
        val clockWidth = minOf(208.dp, availableClockWidth)
        val panelWidth = minOf(350.dp, (maxWidth - 36.dp).coerceAtLeast(0.dp))

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(100)),
        ) {
            val source = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Dismiss charms"
                        role = Role.Button
                    }
                    .clickable(
                        interactionSource = source,
                        indication = null,
                        onClickLabel = "Dismiss charms",
                        role = Role.Button,
                    ) {
                        latestOnDismiss.value()
                    },
            )
        }

        // Windows shows the time/date slab with the rail. Once a concrete charm pane replaces
        // the rail, the slab disappears with it rather than floating over the new pane.
        if (clockWidth > 0.dp) {
            AnimatedVisibility(
                visible = visible && panel == null,
                modifier = Modifier.align(Alignment.BottomStart),
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(
                        CharmsMotion.ClockDurationMillis,
                        easing = CharmsDecelerateEasing,
                    ),
                ) + fadeIn(animationSpec = tween(180)),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(
                        CharmsMotion.ClockDurationMillis,
                        easing = CharmsDecelerateEasing,
                    ),
                ) + fadeOut(animationSpec = tween(120)),
            ) {
                CharmsClockSlab(
                    minuteTick = minuteTick,
                    modifier = Modifier
                        .padding(start = 16.dp, bottom = 16.dp)
                        .width(clockWidth),
                )
            }
        }

        AnimatedVisibility(
            visible = visible && panel == null,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(
                    CharmsMotion.RailDurationMillis,
                    easing = CharmsDecelerateEasing,
                ),
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(
                    CharmsMotion.RailDurationMillis,
                    easing = CharmsDecelerateEasing,
                ),
            ) + fadeOut(animationSpec = tween(120)),
        ) {
            CharmsRail(
                width = railWidth,
                maxHeight = maxHeight,
                initialFocusRequester = initialFocusRequester,
                onSearch = { panel = CharmPanel.Search },
                onShare = { panel = CharmPanel.Share },
                onStart = {
                    latestOnStart.value()
                    latestOnDismiss.value()
                },
                onDevices = { panel = CharmPanel.Devices },
                onSettingsPanel = { panel = CharmPanel.Settings },
                modifier = Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .animateEnterExit(
                        enter = fadeIn(
                            animationSpec = tween(
                                220,
                                delayMillis = CharmsMotion.RailContentDelayMillis,
                            ),
                        ) + slideInHorizontally(
                            initialOffsetX = { it / 8 },
                            animationSpec = tween(
                                220,
                                delayMillis = CharmsMotion.RailContentDelayMillis,
                                easing = CharmsDecelerateEasing,
                            ),
                        ),
                        exit = fadeOut(animationSpec = tween(100)),
                    ),
            )
        }

        if (panelWidth > 0.dp) {
            AnimatedVisibility(
                visible = visible && panel != null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(panelWidth)
                    .fillMaxHeight(),
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(
                        CharmsMotion.PanelDurationMillis,
                        delayMillis = CharmsMotion.RailContentDelayMillis,
                        easing = CharmsDecelerateEasing,
                    ),
                ) + fadeIn(animationSpec = tween(160, delayMillis = 20)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(
                        CharmsMotion.PanelDurationMillis,
                        easing = CharmsDecelerateEasing,
                    ),
                ) + fadeOut(animationSpec = tween(110)),
            ) {
                panel?.let { currentPanel ->
                    CharmsPanelPane(
                        panel = currentPanel,
                        apps = apps,
                        appsRepository = appsRepository,
                        searchFocusRequester = searchFocusRequester,
                        onAppClick = { app, bounds ->
                            latestOnAppClick.value(app, bounds)
                            panel = null
                            latestOnDismiss.value()
                        },
                        onSearch = {
                            panel = null
                            latestOnSearch.value()
                            latestOnDismiss.value()
                        },
                        onSettings = {
                            panel = null
                            latestOnSettings.value()
                            latestOnDismiss.value()
                        },
                        onAddApps = {
                            panel = null
                            latestOnAddApps.value()
                            latestOnDismiss.value()
                        },
                        onDevices = {
                            panel = null
                            latestOnDevices.value()
                            latestOnDismiss.value()
                        },
                        onPower = {
                            panel = null
                            latestOnPower.value()
                            latestOnDismiss.value()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Right-edge swipe recognizer matching the Windows 8.1 interaction contract.
 *
 * A tiny diagonal twitch no longer opens Charms. The gesture must begin at the right edge and make
 * a deliberate inward, horizontally dominant pull. A vertical takeover or outward reversal cancels
 * tracking while leaving the existing Start/All Apps scroll gestures untouched.
 */
fun Modifier.charmsEdgeGesture(
    enabled: Boolean,
    onOpen: () -> Unit,
): Modifier = composed {
    val latestOnOpen = rememberUpdatedState(onOpen)
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 28.dp.toPx() }
    val commitDistancePx = with(density) { 34.dp.toPx() }

    if (!enabled) {
        this
    } else {
        pointerInput(enabled, edgeWidthPx, commitDistancePx) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val startedAtEdge = CharmsMotion.isWithinRightEdge(
                    startX = down.position.x,
                    viewportWidth = size.width.toFloat(),
                    edgeWidth = edgeWidthPx,
                )
                if (!startedAtEdge) return@awaitEachGesture

                var tracking = true
                var opened = false
                var totalX = 0f
                var totalY = 0f

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    totalX += change.position.x - change.previousPosition.x
                    totalY += change.position.y - change.previousPosition.y

                    if (tracking && !opened) {
                        when {
                            CharmsMotion.shouldCommitEdgeSwipe(
                                deltaX = totalX,
                                deltaY = totalY,
                                touchSlop = viewConfiguration.touchSlop,
                                commitDistance = commitDistancePx,
                            ) -> {
                                latestOnOpen.value()
                                opened = true
                                change.consume()
                            }

                            CharmsMotion.shouldCancelEdgeSwipe(
                                deltaX = totalX,
                                deltaY = totalY,
                                touchSlop = viewConfiguration.touchSlop,
                            ) -> tracking = false
                        }
                    } else if (opened) {
                        change.consume()
                    }
                }
            }
        }
    }
}

@Composable
private fun CharmsRail(
    width: Dp,
    maxHeight: Dp,
    initialFocusRequester: FocusRequester,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onStart: () -> Unit,
    onDevices: () -> Unit,
    onSettingsPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val actionHeight = 64.dp
    val contentHeight = actionHeight * 5 + 8.dp * 4
    val topSpace = if (maxHeight > contentHeight + 24.dp) {
        (maxHeight - contentHeight) / 2
    } else {
        12.dp
    }

    Box(
        modifier = modifier
            .background(CharmsBlack)
            .semantics {
                paneTitle = "Windows charms"
                isTraversalGroup = true
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(topSpace))
            CharmAction(
                label = "Search",
                glyph = CharmGlyph.Search,
                onClick = onSearch,
                initialFocusRequester = initialFocusRequester,
            )
            Spacer(Modifier.height(8.dp))
            CharmAction(label = "Share", glyph = CharmGlyph.Share, onClick = onShare)
            Spacer(Modifier.height(8.dp))
            CharmAction(label = "Start", glyph = CharmGlyph.Start, onClick = onStart)
            Spacer(Modifier.height(8.dp))
            CharmAction(label = "Devices", glyph = CharmGlyph.Devices, onClick = onDevices)
            Spacer(Modifier.height(8.dp))
            CharmAction(label = "Settings", glyph = CharmGlyph.Settings, onClick = onSettingsPanel)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CharmAction(
    label: String,
    glyph: CharmGlyph,
    onClick: () -> Unit,
    initialFocusRequester: FocusRequester? = null,
) {
    val glint = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focusModifier = if (initialFocusRequester != null) {
        Modifier.focusRequester(initialFocusRequester)
    } else {
        Modifier
    }

    if (glyph == CharmGlyph.Start) {
        LaunchedEffect(pressed) {
            if (pressed) glint.snapTo(1f)
            glint.animateTo(0f, animationSpec = tween(180))
        }
    }

    Box(
        modifier = focusModifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                val scale = if (pressed) 0.95f else 1f
                scaleX = scale
                scaleY = scale
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Canvas(Modifier.size(29.dp)) {
                drawCharmGlyph(glyph, if (glyph == CharmGlyph.Start) glint.value else 0f)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                color = CharmsLightGrey,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CharmsPanelPane(
    panel: CharmPanel,
    apps: List<AppInfo>,
    appsRepository: AppsRepository,
    searchFocusRequester: FocusRequester,
    onAppClick: (AppInfo, Rect) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAddApps: () -> Unit,
    onDevices: () -> Unit,
    onPower: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharmsPurple)
            .statusBarsPadding()
            .imePadding()
            .semantics {
                paneTitle = when (panel) {
                    CharmPanel.Search -> "Search"
                    CharmPanel.Share -> "Share"
                    CharmPanel.Devices -> "Devices"
                    CharmPanel.Settings -> "Settings"
                }
                isTraversalGroup = true
            },
    ) {
        when (panel) {
            CharmPanel.Search -> SearchPane(
                apps = apps,
                appsRepository = appsRepository,
                searchFocusRequester = searchFocusRequester,
                onAppClick = onAppClick,
                onSearch = onSearch,
            )
            CharmPanel.Share -> SharePane()
            CharmPanel.Devices -> DevicesPane(onDevices = onDevices)
            CharmPanel.Settings -> SettingsPane(
                onSettings = onSettings,
                onAddApps = onAddApps,
                onDevices = onDevices,
                onPower = onPower,
            )
        }
    }
}

@Composable
private fun SearchPane(
    apps: List<AppInfo>,
    appsRepository: AppsRepository,
    searchFocusRequester: FocusRequester,
    onAppClick: (AppInfo, Rect) -> Unit,
    onSearch: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(apps, query) {
        val term = query.trim().lowercase(Locale.getDefault())
        apps
            .filter { app ->
                term.isBlank() ||
                    app.label.lowercase(Locale.getDefault()).contains(term) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(term)
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
    ) {
        PanelHeader(title = "Search")
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Everywhere  ▾",
            color = CharmsLightGrey,
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.White),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(searchFocusRequester)
                    .semantics { contentDescription = "Search apps" }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                textStyle = TextStyle(
                    color = SearchFieldText,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 17.sp,
                ),
                cursorBrush = SolidColor(SearchFieldText),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search",
                                color = SearchFieldText.copy(alpha = 0.55f),
                                style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(48.dp)
                    .background(StartPurple)
                    .clickable { onSearch() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
                    drawCharmGlyph(CharmGlyph.Search, 0f)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = if (query.isBlank()) "No installed apps" else "No apps found",
                    color = CharmsLightGrey,
                    style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(
                    items = filteredApps,
                    key = { app -> "${app.packageName}:${app.activityName}" },
                ) { app ->
                    val holder = remember(app.packageName, app.activityName) { LayoutCoordinatesHolder() }
                    SearchResultRow(
                        app = app,
                        icon = rememberAppIcon(appsRepository, app.packageName),
                        modifier = Modifier.onGloballyPositioned { holder.coordinates = it },
                        onClick = {
                            onAppClick(app, holder.coordinates?.boundsInWindow() ?: Rect.Zero)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsRow(label = "See all results", onClick = onSearch)
    }
}

private class LayoutCoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

@Composable
private fun SearchResultRow(
    app: AppInfo,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .graphicsLayer {
                val scale = if (pressed) 0.98f else 1f
                scaleX = scale
                scaleY = scale
            }
            .semantics(mergeDescendants = true) {
                contentDescription = app.label
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Open ${app.label}",
                role = Role.Button,
                onClick = onClick,
            )
            .then(modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            MetroIcon(glyph = "app", color = CharmsWhite, size = 30.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = app.label,
            color = CharmsWhite,
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SharePane() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
    ) {
        PanelHeader(title = "Share")
        Spacer(Modifier.height(34.dp))
        Text(
            text = "Nothing to share from Start",
            color = CharmsWhite,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Share becomes available when the foreground app exposes shareable content.",
            color = CharmsLightGrey,
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
        )
    }
}

@Composable
private fun DevicesPane(onDevices: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
    ) {
        PanelHeader(title = "Devices")
        Spacer(Modifier.height(28.dp))
        SettingsRow(label = "Play", onClick = onDevices)
        SettingsRow(label = "Print", onClick = onDevices)
        SettingsRow(label = "Project", onClick = onDevices)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Choose a device for playback, printing, or projection.",
            color = CharmsLightGrey,
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
        )
    }
}

@Composable
private fun SettingsPane(
    onSettings: () -> Unit,
    onAddApps: () -> Unit,
    onDevices: () -> Unit,
    onPower: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
    ) {
        PanelHeader(title = "Settings")
        Spacer(Modifier.height(18.dp))

        Text(
            text = "Start",
            color = CharmsMutedGrey,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
        Spacer(Modifier.height(4.dp))
        SettingsRow(label = "Personalize", onClick = onSettings)
        SettingsRow(label = "Tiles", onClick = onAddApps)
        SettingsRow(label = "Help", onClick = onSettings)

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.16f)),
        )
        Spacer(Modifier.height(18.dp))

        // Windows 8.1 Settings charm quick block: two rows of three controls.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QuickSetting(label = "Network", glyph = QuickGlyph.Network, onClick = onDevices)
            QuickSetting(label = "Volume", glyph = QuickGlyph.Volume, onClick = onSettings)
            QuickSetting(label = "Brightness", glyph = QuickGlyph.Brightness, onClick = onSettings)
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QuickSetting(label = "Notifications", glyph = QuickGlyph.Notifications, onClick = onSettings)
            QuickSetting(label = "Power", glyph = QuickGlyph.Power, onClick = onPower)
            QuickSetting(label = "Keyboard", glyph = QuickGlyph.Keyboard, onClick = onSettings)
        }

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.16f)),
        )
        Spacer(Modifier.height(10.dp))
        SettingsRow(label = "Change PC settings", onClick = onSettings)
    }
}

@Composable
private fun PanelHeader(title: String) {
    // The Windows 8.1 pane header is plain typography. Hardware/system Back returns to the
    // five-charms rail; no Android-style visible back affordance is injected into the pane.
    Text(
        text = title,
        color = CharmsWhite,
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    onClick: () -> Unit,
) {
    FlatPanelButton(
        label = label,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            color = CharmsWhite,
            textAlign = TextAlign.Start,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun QuickSetting(
    label: String,
    glyph: QuickGlyph,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .width(88.dp)
            .graphicsLayer {
                val scale = if (pressed) 0.96f else 1f
                scaleX = scale
                scaleY = scale
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(33.dp)) {
            drawQuickGlyph(glyph)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = CharmsWhite,
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FlatPanelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = if (pressed) 0.98f else 1f
                scaleX = scale
                scaleY = scale
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun CharmsClockSlab(
    minuteTick: Long,
    modifier: Modifier = Modifier,
) {
    val locale = remember { Locale.getDefault() }
    val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("h:mm a", locale) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE, MMMM d", locale) }
    val now = remember(minuteTick, locale) { LocalDateTime.now() }
    val time = now.format(timeFormatter)
    val date = now.format(dateFormatter)

    Box(
        modifier = modifier
            .background(CharmsBlack)
            .semantics(mergeDescendants = true) {
                contentDescription = "Local time $time, $date"
            },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = time,
                color = CharmsWhite,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Light,
                ),
                maxLines = 1,
            )
            Text(
                text = date,
                color = CharmsLightGrey,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private enum class CharmPanel { Search, Share, Devices, Settings }
private enum class CharmGlyph { Search, Share, Start, Devices, Settings }
private enum class QuickGlyph { Network, Volume, Brightness, Notifications, Power, Keyboard }

private fun currentEpochMinute(): Long = System.currentTimeMillis() / 60_000L

private fun DrawScope.drawCharmGlyph(glyph: CharmGlyph, glint: Float) {
    val unit = min(size.width, size.height) / 28f
    val left = (size.width - 28f * unit) / 2f
    val top = (size.height - 28f * unit) / 2f
    fun point(x: Float, y: Float) = Offset(left + x * unit, top + y * unit)
    fun rect(leftX: Float, topY: Float, rightX: Float, bottomY: Float) =
        Rect(left + leftX * unit, top + topY * unit, left + rightX * unit, top + bottomY * unit)
    val stroke = 1.8f * unit

    when (glyph) {
        CharmGlyph.Search -> {
            drawCircle(
                color = CharmsWhite,
                radius = 7.2f * unit,
                center = point(10.5f, 10.5f),
                style = Stroke(width = stroke),
            )
            drawLine(
                color = CharmsWhite,
                start = point(15.8f, 15.8f),
                end = point(23.5f, 23.5f),
                strokeWidth = stroke,
                cap = StrokeCap.Square,
            )
        }

        CharmGlyph.Share -> {
            val arcs = listOf(
                rect(3.5f, 3.5f, 25f, 25f),
                rect(7.5f, 7.5f, 21f, 21f),
                rect(11.5f, 11.5f, 17f, 17f),
            )
            arcs.forEach { bounds ->
                drawArc(
                    color = CharmsWhite,
                    startAngle = -58f,
                    sweepAngle = 116f,
                    useCenter = false,
                    topLeft = Offset(bounds.left, bounds.top),
                    size = Size(bounds.width, bounds.height),
                    style = Stroke(width = stroke),
                )
            }
        }

        CharmGlyph.Start -> {
            fun pane(x1: Float, y1: Float, x2: Float, y2: Float, color: Color) {
                val path = Path().apply {
                    moveTo(point(x1, y1).x, point(x1, y1).y)
                    lineTo(point(x2, y1).x, point(x2, y1).y)
                    lineTo(point(x2, y2).x, point(x2, y2).y)
                    lineTo(point(x1, y2).x, point(x1, y2).y)
                    close()
                }
                drawPath(path, color)
            }
            pane(3f, 4.2f, 13f, 13.7f, StartPurple.copy(alpha = 0.95f))
            pane(15f, 3.1f, 25f, 13.7f, StartPurple.copy(alpha = 0.78f))
            pane(3f, 16f, 13f, 25.2f, StartPurple.copy(alpha = 0.78f))
            pane(15f, 16f, 24.4f, 24.2f, StartPurple)
            if (glint > 0f) {
                drawLine(
                    color = StartPurpleGlint.copy(alpha = 0.95f * glint),
                    start = point(4f, 23.8f),
                    end = point(23.3f, 4.4f),
                    strokeWidth = 1.1f * unit,
                    cap = StrokeCap.Square,
                )
            }
        }

        CharmGlyph.Devices -> {
            drawRect(
                color = CharmsWhite,
                topLeft = point(3f, 5f),
                size = Size(18f * unit, 13f * unit),
                style = Stroke(width = stroke),
            )
            drawLine(CharmsWhite, point(9f, 21f), point(16f, 21f), stroke, StrokeCap.Square)
            drawLine(CharmsWhite, point(12.5f, 18f), point(12.5f, 21f), stroke, StrokeCap.Square)
            drawRect(
                color = CharmsWhite,
                topLeft = point(9f, 9f),
                size = Size(16f * unit, 13f * unit),
                style = Stroke(width = stroke),
            )
        }

        CharmGlyph.Settings -> {
            val center = point(14f, 14f)
            val gear = Path()
            for (i in 0 until 16) {
                val angle = -PI / 2.0 + i * PI / 8.0
                val radius = if (i % 2 == 0) 11.5f else 8.8f
                val x = center.x + cos(angle).toFloat() * radius * unit
                val y = center.y + sin(angle).toFloat() * radius * unit
                if (i == 0) gear.moveTo(x, y) else gear.lineTo(x, y)
            }
            gear.close()
            drawPath(gear, color = CharmsWhite, style = Stroke(width = stroke, cap = StrokeCap.Square))
            drawCircle(
                color = CharmsWhite,
                radius = 3.2f * unit,
                center = center,
                style = Stroke(width = stroke),
            )
        }
    }
}

private fun DrawScope.drawQuickGlyph(glyph: QuickGlyph) {
    val w = size.width
    val h = size.height
    val stroke = (w * 0.075f).coerceAtLeast(1.5f)
    val color = CharmsWhite
    when (glyph) {
        QuickGlyph.Network -> {
            val barW = w * 0.12f
            for (i in 0 until 4) {
                val barH = h * (0.18f + i * 0.14f)
                drawRect(
                    color = color,
                    topLeft = Offset(w * (0.18f + i * 0.17f), h * 0.78f - barH),
                    size = Size(barW, barH),
                )
            }
        }

        QuickGlyph.Volume -> {
            val speaker = Path().apply {
                moveTo(w * 0.18f, h * 0.42f)
                lineTo(w * 0.36f, h * 0.42f)
                lineTo(w * 0.55f, h * 0.25f)
                lineTo(w * 0.55f, h * 0.75f)
                lineTo(w * 0.36f, h * 0.58f)
                lineTo(w * 0.18f, h * 0.58f)
                close()
            }
            drawPath(speaker, color)
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.47f, h * 0.24f),
                size = Size(w * 0.36f, h * 0.52f),
                style = Stroke(width = stroke),
            )
        }

        QuickGlyph.Brightness -> {
            val c = Offset(w * 0.5f, h * 0.5f)
            drawCircle(color, radius = w * 0.15f, center = c)
            for (i in 0 until 8) {
                val angle = (i * PI / 4.0).toFloat()
                drawLine(
                    color = color,
                    start = Offset(c.x + cos(angle) * w * 0.25f, c.y + sin(angle) * w * 0.25f),
                    end = Offset(c.x + cos(angle) * w * 0.38f, c.y + sin(angle) * w * 0.38f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
            }
        }

        QuickGlyph.Notifications -> {
            drawRect(
                color = color,
                topLeft = Offset(w * 0.18f, h * 0.30f),
                size = Size(w * 0.64f, h * 0.40f),
                style = Stroke(width = stroke),
            )
            drawLine(color, Offset(w * 0.28f, h * 0.42f), Offset(w * 0.72f, h * 0.42f), stroke)
            drawLine(color, Offset(w * 0.28f, h * 0.56f), Offset(w * 0.62f, h * 0.56f), stroke)
        }

        QuickGlyph.Power -> {
            drawArc(
                color = color,
                startAngle = -48f,
                sweepAngle = 276f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.18f),
                size = Size(w * 0.64f, h * 0.64f),
                style = Stroke(width = stroke * 1.15f),
            )
            drawLine(
                color = color,
                start = Offset(w * 0.5f, h * 0.12f),
                end = Offset(w * 0.5f, h * 0.47f),
                strokeWidth = stroke * 1.15f,
                cap = StrokeCap.Square,
            )
        }

        QuickGlyph.Keyboard -> {
            drawRect(
                color = color,
                topLeft = Offset(w * 0.12f, h * 0.29f),
                size = Size(w * 0.76f, h * 0.46f),
                style = Stroke(width = stroke),
            )
            val keyW = w * 0.10f
            val keyH = h * 0.07f
            for (row in 0 until 3) {
                for (column in 0 until 5) {
                    drawRect(
                        color = color,
                        topLeft = Offset(w * 0.20f + column * w * 0.13f, h * 0.37f + row * h * 0.11f),
                        size = Size(keyW, keyH),
                    )
                }
            }
        }
    }
}
