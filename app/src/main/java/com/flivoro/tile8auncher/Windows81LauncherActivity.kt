package com.flivoro.tile8auncher

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.data.Windows81ShellPreferences
import com.flivoro.tile8auncher.notifications.LauncherNotificationService
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.apps.Windows81AllAppsScreen
import com.flivoro.tile8auncher.ui.components.FingerFollowingVerticalNavigation
import com.flivoro.tile8auncher.ui.components.FlipLaunchOverlay
import com.flivoro.tile8auncher.ui.components.Windows81CharmPane
import com.flivoro.tile8auncher.ui.components.Windows81CharmsEdgeDetector
import com.flivoro.tile8auncher.ui.components.Windows81CharmsOverlay
import com.flivoro.tile8auncher.ui.components.WindowsAppView
import com.flivoro.tile8auncher.ui.components.WindowsWallpaper
import com.flivoro.tile8auncher.ui.dialogs.PowerDialog
import com.flivoro.tile8auncher.ui.start.Windows81StartCustomizationBar
import com.flivoro.tile8auncher.ui.start.Windows81StartScreen
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Windows81ShellSurface { START, ALL_APPS }

/**
 * Windows 8.1 fidelity shell. It deliberately reuses the existing FlipAnimationState,
 * FlipLaunchOverlay, LaunchTiming and motion classes without modifying their geometry.
 */
class Windows81LauncherActivity : ComponentActivity() {
    private lateinit var appsRepository: AppsRepository
    private lateinit var shellPreferences: Windows81ShellPreferences
    private var flipState by mutableStateOf(FlipAnimationState())
    private var activeInAppTile by mutableStateOf<TileModel?>(null)
    private var homeRequest by mutableIntStateOf(0)
    private var homeSurfaceResetRequest by mutableIntStateOf(0)
    private var homeLaunchReverseRequest by mutableIntStateOf(0)
    private var entranceRequest by mutableIntStateOf(0)
    private var packageRevision by mutableIntStateOf(0)
    private var pendingLaunchIntent: Intent? = null
    private var launchHandoffPending = false
    private var homeReversePending = false
    private var launcherForeground = false
    private var launcherResumed = false
    private var waitingForUserPresent = false
    private var entryHandledByUserPresent = false
    private var homeIntentPending = false
    private var userPresentReceiverRegistered = false
    private var packageReceiverRegistered = false

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT && launcherForeground && waitingForUserPresent) {
                waitingForUserPresent = false
                entryHandledByUserPresent = true
                entranceRequest++
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            packageRevision++
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressLauncherTransitions()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        applyImmersiveShellBars()

        appsRepository = AppsRepository(this)
        shellPreferences = Windows81ShellPreferences(this)

        ContextCompat.registerReceiver(
            this,
            userPresentReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        userPresentReceiverRegistered = true

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            packageFilter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        packageReceiverRegistered = true

        setContent {
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                Windows81ShellApp(
                    appsRepository = appsRepository,
                    shellPreferences = shellPreferences,
                    flipState = flipState,
                    activeInAppTile = activeInAppTile,
                    homeRequest = homeRequest,
                    homeSurfaceResetRequest = homeSurfaceResetRequest,
                    externalLaunchReverseRequest = homeLaunchReverseRequest,
                    entranceRequest = entranceRequest,
                    packageRevision = packageRevision,
                    onOpenInAppTile = { activeInAppTile = it },
                    onCloseInAppTile = {
                        activeInAppTile = null
                        flipState = FlipAnimationState(isRunning = false)
                    },
                    onTriggerFlip = { tile, bounds, origin ->
                        if (!flipState.isRunning) triggerExistingFlip(tile, bounds, origin)
                    },
                    onLaunchTile = { handleTileLaunch(it) },
                    onDismissFlip = {
                        flipState = FlipAnimationState(isRunning = false)
                        pendingLaunchIntent = null
                    },
                    onExternalLaunchReverseComplete = { completeHomeLaunchReverse() },
                    onOpenAppInfo = { openAppInfo(it) },
                    onUninstallApp = { uninstallApp(it) },
                    onOpenDevices = { openDeviceSettings() },
                    onOpenNotificationSettings = { openNotificationSettings() },
                )
            }
        }
    }

    private fun triggerExistingFlip(tile: TileModel, bounds: Rect, origin: LaunchOrigin) {
        val resolved = resolveLaunchIntent(tile)
        val removedExternalApp = tile.packageName != null && resolved == null && tile.tileType !in INTERNAL_TILE_TYPES
        if (removedExternalApp) {
            Toast.makeText(this, "${tile.title} is no longer installed", Toast.LENGTH_SHORT).show()
            packageRevision++
            return
        }

        homeReversePending = false
        pendingLaunchIntent = resolved
        val icon = tile.packageName?.let { appsRepository.getAppIcon(it) }
        flipState = FlipAnimationState(
            isRunning = true,
            sourceTile = tile,
            sourceBounds = bounds,
            appIcon = icon,
            accentColor = tile.colorValue.toTileColor(),
            animationMode = appsRepository.getFlipAnimationMode(),
            timing = appsRepository.getLaunchTiming(allApps = origin == LaunchOrigin.ALL_APPS),
            hasInternalWindow = pendingLaunchIntent == null,
            origin = origin,
        )
    }

    override fun onStart() {
        super.onStart()
        launcherForeground = true
        applyImmersiveShellBars()
    }

    override fun onStop() {
        launcherForeground = false
        launcherResumed = false
        waitingForUserPresent = false
        entryHandledByUserPresent = false
        homeReversePending = false
        super.onStop()
        if (flipState.isRunning) flipState = FlipAnimationState(isRunning = false)
        pendingLaunchIntent = null
    }

    override fun onPause() {
        // Do not tear down an in-flight flip here. HOME can transiently alter the
        // lifecycle on some OEM launchers before the HOME intent reaches onNewIntent.
        // A real external handoff reaches onStop(), which remains the cleanup point.
        launcherResumed = false
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        suppressLauncherTransitions()
        applyImmersiveShellBars()
        val homeWasPending = homeIntentPending
        homeIntentPending = false
        val userPresentEntryHandled = entryHandledByUserPresent
        entryHandledByUserPresent = false
        launcherResumed = true
        val keyguardLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        waitingForUserPresent = keyguardLocked
        if (!keyguardLocked && !homeWasPending && !userPresentEntryHandled) entranceRequest++
        launchHandoffPending = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveShellBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        suppressLauncherTransitions()
        applyImmersiveShellBars()
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // While the launcher still owns the flip, HOME is an interruption,
            // not an instruction to snap the animation away. Reverse the exact
            // current launch timeline; the target app must never receive handoff.
            if (flipState.isRunning && !launchHandoffPending) {
                if (!homeReversePending) {
                    homeReversePending = true
                    homeLaunchReverseRequest++
                }
                return
            }

            activeInAppTile = null
            flipState = FlipAnimationState()
            pendingLaunchIntent = null
            homeIntentPending = !launcherResumed
            homeRequest++
        }
    }

    override fun onDestroy() {
        if (userPresentReceiverRegistered) unregisterReceiver(userPresentReceiver)
        if (packageReceiverRegistered) unregisterReceiver(packageReceiver)
        userPresentReceiverRegistered = false
        packageReceiverRegistered = false
        super.onDestroy()
    }

    private fun completeHomeLaunchReverse() {
        if (!homeReversePending) return
        homeReversePending = false
        launchHandoffPending = false
        activeInAppTile = null
        pendingLaunchIntent = null
        flipState = FlipAnimationState()
        homeIntentPending = false

        // HOME semantics still land on Start. This reset is intentionally kept
        // separate from homeRequest so a completed reverse does not replay the
        // Start entrance animation after the source frame has already returned.
        homeSurfaceResetRequest++
        applyImmersiveShellBars()
    }

    private fun applyImmersiveShellBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun suppressLauncherTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.no_anim, R.anim.no_anim)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.no_anim, R.anim.no_anim)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.no_anim, R.anim.no_anim)
        }
    }

    private fun openAppInfo(packageName: String) {
        runCatching {
            startActivityWithCustomAnim(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }.onFailure { Toast.makeText(this, "Could not open App info", Toast.LENGTH_SHORT).show() }
    }

    private fun uninstallApp(packageName: String) {
        runCatching {
            startActivityWithCustomAnim(Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
            })
        }.onFailure { Toast.makeText(this, "Could not request uninstall", Toast.LENGTH_SHORT).show() }
    }

    private fun openNotificationSettings() {
        runCatching { startActivityWithCustomAnim(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure { startActivityWithCustomAnim(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun openDeviceSettings() {
        runCatching { startActivityWithCustomAnim(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .onFailure { startActivityWithCustomAnim(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun resolveLaunchIntent(tile: TileModel): Intent? = try {
        val intent = when (tile.tileType) {
            TileType.READING_LIST, TileType.MONEY, TileType.DESKTOP, TileType.SETTINGS -> null
            TileType.INTERNET_EXPLORER -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com"))
            TileType.STORE -> packageManager.getLaunchIntentForPackage("com.android.vending")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms"))
            else -> tile.packageName?.let { packageManager.getLaunchIntentForPackage(it) }
        }
        intent?.takeIf { it.resolveActivity(packageManager) != null }
    } catch (_: Exception) {
        null
    }

    private fun handleTileLaunch(tile: TileModel) {
        // HOME may have won the race on the final rendered flip frame before the
        // Compose reverse effect started. Never hand off while that request exists.
        if (homeReversePending) return

        val preparedIntent = pendingLaunchIntent
        pendingLaunchIntent = null
        if (preparedIntent == null) {
            activeInAppTile = tile
            flipState = FlipAnimationState()
            return
        }
        try {
            launchHandoffPending = true
            tile.packageName?.let(shellPreferences::recordLaunch)
            startActivityWithCustomAnim(preparedIntent)
        } catch (_: Exception) {
            launchHandoffPending = false
            activeInAppTile = tile
            flipState = FlipAnimationState()
        }
    }

    private fun startActivityWithCustomAnim(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val options = ActivityOptions.makeCustomAnimation(this, R.anim.no_anim, R.anim.no_anim)
        startActivity(intent, options.toBundle())
        suppressLauncherTransitions()
    }

    private companion object {
        val INTERNAL_TILE_TYPES = setOf(TileType.READING_LIST, TileType.MONEY, TileType.DESKTOP, TileType.SETTINGS)
    }
}

@Composable
private fun Windows81ShellApp(
    appsRepository: AppsRepository,
    shellPreferences: Windows81ShellPreferences,
    flipState: FlipAnimationState,
    activeInAppTile: TileModel?,
    onOpenInAppTile: (TileModel) -> Unit,
    onCloseInAppTile: () -> Unit,
    onTriggerFlip: (TileModel, Rect, LaunchOrigin) -> Unit,
    onLaunchTile: (TileModel) -> Unit,
    onDismissFlip: () -> Unit,
    onExternalLaunchReverseComplete: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onUninstallApp: (String) -> Unit,
    onOpenDevices: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    homeRequest: Int,
    homeSurfaceResetRequest: Int,
    externalLaunchReverseRequest: Int,
    entranceRequest: Int,
    packageRevision: Int,
) {
    var currentSurface by remember { mutableStateOf(Windows81ShellSurface.START) }
    val drawerProgress = remember { mutableFloatStateOf(0f) }
    val startScroll = rememberLazyListState()
    val appsScroll = rememberLazyListState()
    var wallpaperParallaxEnabled by remember { mutableStateOf(appsRepository.getWallpaperParallaxEnabled()) }
    var wallpaperStyle by remember { mutableIntStateOf(appsRepository.getWallpaperStyle()) }
    val tiles = remember { mutableStateListOf<TileModel>() }
    val selectedTileIds = remember { mutableStateListOf<String>() }
    var namingGroups by remember { mutableStateOf(false) }
    var liveDisabledIds by remember { mutableStateOf(shellPreferences.getLiveTileDisabledIds()) }
    var sortMode by remember { mutableStateOf(shellPreferences.getAppsSortMode()) }
    var showPowerDialog by remember { mutableStateOf(false) }
    var charmsVisible by remember { mutableStateOf(false) }
    var charmsPane by remember { mutableStateOf(Windows81CharmPane.MAIN) }
    var flipLaunchDispatched by remember(flipState) { mutableStateOf(false) }
    var localStartEntranceRequest by remember { mutableIntStateOf(0) }
    var seenRevision by remember { mutableIntStateOf(0) }
    val notifications by LauncherNotificationService.notifications.collectAsState()

    val apps by produceState<List<AppInfo>>(emptyList(), appsRepository, packageRevision) {
        val loaded = withContext(Dispatchers.IO) { appsRepository.getInstalledApps() }
        shellPreferences.initializeSeenAppsIfNeeded(loaded)
        value = loaded
    }
    val displayedApps = remember(apps, seenRevision) { apps.toList() }
    val startEntranceRequest = entranceRequest + homeRequest + localStartEntranceRequest
    val surfaceResetRequest = homeRequest + homeSurfaceResetRequest

    fun saveTiles() = appsRepository.savePinnedTiles(tiles.toList())
    fun navigateToAllApps() { currentSurface = Windows81ShellSurface.ALL_APPS }
    fun navigateToStart() {
        if (currentSurface != Windows81ShellSurface.START) {
            currentSurface = Windows81ShellSurface.START
            localStartEntranceRequest++
        }
    }
    fun closeInAppTileAndRetriggerEntrance() {
        val wasOpen = activeInAppTile != null
        onCloseInAppTile()
        if (wasOpen) localStartEntranceRequest++
    }
    fun showCharms(pane: Windows81CharmPane = Windows81CharmPane.MAIN) {
        charmsPane = pane
        charmsVisible = true
    }

    LaunchedEffect(Unit) {
        val pinned = withContext(Dispatchers.IO) { appsRepository.loadPinnedTiles() }
        tiles.clear()
        tiles.addAll(pinned)
    }
    LaunchedEffect(surfaceResetRequest) {
        currentSurface = Windows81ShellSurface.START
        selectedTileIds.clear()
        namingGroups = false
        charmsVisible = false
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (flipState.isRunning || activeInAppTile != null) 0f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "Windows81ShellAlpha",
    )
    val contentScale by animateFloatAsState(
        targetValue = if (flipState.isRunning || activeInAppTile != null) 0.88f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "Windows81ShellScale",
    )

    BackHandler(
        enabled = charmsVisible || selectedTileIds.isNotEmpty() || namingGroups || flipState.isRunning ||
            activeInAppTile != null || currentSurface == Windows81ShellSurface.ALL_APPS,
    ) {
        when {
            charmsVisible -> charmsVisible = false
            namingGroups -> namingGroups = false
            selectedTileIds.isNotEmpty() -> selectedTileIds.clear()
            flipState.isRunning && activeInAppTile != null -> closeInAppTileAndRetriggerEntrance()
            flipState.isRunning -> onDismissFlip()
            activeInAppTile != null -> closeInAppTileAndRetriggerEntrance()
            currentSurface == Windows81ShellSurface.ALL_APPS -> navigateToStart()
        }
    }

    Box(Modifier.fillMaxSize()) {
        WindowsWallpaper(
            wallpaperStyle = wallpaperStyle,
            enabled = wallpaperParallaxEnabled,
            scrollOffsetPx = {
                fun offset(state: androidx.compose.foundation.lazy.LazyListState): Float {
                    val info = state.layoutInfo
                    val itemWidth = info.visibleItemsInfo.firstOrNull()?.size ?: 0
                    return (state.firstVisibleItemIndex.toFloat() * (itemWidth + info.mainAxisItemSpacing) +
                        state.firstVisibleItemScrollOffset).coerceAtLeast(0f)
                }
                val p = drawerProgress.floatValue
                offset(startScroll) * (1f - p) + offset(appsScroll) * p
            },
        )

        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val exiting = flipState.isRunning || activeInAppTile != null
                alpha = if (exiting) contentAlpha else 1f
                scaleX = if (exiting) contentScale else 1f
                scaleY = if (exiting) contentScale else 1f
            },
        ) {
            FingerFollowingVerticalNavigation(
                showAllApps = currentSurface == Windows81ShellSurface.ALL_APPS,
                onShowAllAppsChange = {
                    currentSurface = if (it) Windows81ShellSurface.ALL_APPS else Windows81ShellSurface.START
                },
                resetRequest = surfaceResetRequest,
                progressState = drawerProgress,
                startContent = {
                    Windows81StartScreen(
                        listState = startScroll,
                        tiles = tiles,
                        appsRepository = appsRepository,
                        selectedTileIds = selectedTileIds.toSet(),
                        namingGroups = namingGroups,
                        liveTileDisabledIds = liveDisabledIds,
                        notifications = notifications,
                        launchingTileId = flipState.sourceTile?.id.takeIf { flipState.isRunning },
                        entranceRequest = startEntranceRequest,
                        newAppCount = shellPreferences.newAppCount(displayedApps),
                        onTileClick = { tile, bounds -> onTriggerFlip(tile, bounds, LaunchOrigin.START) },
                        onTileLongClick = { tile ->
                            if (tile.id in selectedTileIds) selectedTileIds.remove(tile.id)
                            else selectedTileIds.add(tile.id)
                        },
                        onToggleSelection = { tile ->
                            if (tile.id in selectedTileIds) selectedTileIds.remove(tile.id)
                            else selectedTileIds.add(tile.id)
                        },
                        onMoveTile = { tileId, direction ->
                            val from = tiles.indexOfFirst { it.id == tileId }
                            if (from >= 0) {
                                val to = (from + direction).coerceIn(0, tiles.lastIndex)
                                if (to != from) {
                                    val moved = tiles.removeAt(from)
                                    tiles.add(to, moved)
                                    saveTiles()
                                }
                            }
                        },
                        onGroupNameChange = { old, proposed ->
                            val normalized = proposed.trim().ifEmpty { "Start" }
                            val indices = tiles.indices.filter { tiles[it].groupName == old }
                            indices.forEach { index -> tiles[index] = tiles[index].copy(groupName = normalized) }
                            if (indices.isNotEmpty()) saveTiles()
                        },
                        onPowerClick = { showPowerDialog = true },
                        onSearchClick = { showCharms(Windows81CharmPane.SEARCH) },
                        onNavigateToAllApps = { navigateToAllApps() },
                    )
                },
                allAppsContent = {
                    Windows81AllAppsScreen(
                        listState = appsScroll,
                        apps = displayedApps,
                        appsRepository = appsRepository,
                        shellPreferences = shellPreferences,
                        sortMode = sortMode,
                        onSortModeChange = {
                            sortMode = it
                            shellPreferences.setAppsSortMode(it)
                        },
                        onAppClick = { app, bounds ->
                            val tile = TileModel(
                                id = "app_${app.packageName}",
                                title = app.label,
                                packageName = app.packageName,
                                activityName = app.activityName,
                                colorValue = WindowsColors.Purple,
                                size = TileSize.MEDIUM,
                            )
                            onTriggerFlip(tile, bounds, LaunchOrigin.ALL_APPS)
                        },
                        isAppPinned = { pkg -> tiles.any { it.packageName == pkg } },
                        onPinApp = { app ->
                            if (tiles.none { it.packageName == app.packageName }) {
                                tiles.add(
                                    TileModel(
                                        id = "app_${app.packageName}_${System.currentTimeMillis()}",
                                        title = app.label,
                                        packageName = app.packageName,
                                        activityName = app.activityName,
                                        colorValue = WindowsColors.ColorOptions.random(),
                                        size = TileSize.MEDIUM,
                                        order = tiles.size,
                                    ),
                                )
                                saveTiles()
                            }
                        },
                        onUnpinApp = { pkg ->
                            tiles.removeAll { it.packageName == pkg }
                            saveTiles()
                        },
                        onOpenAppInfo = onOpenAppInfo,
                        onUninstallApp = onUninstallApp,
                        onNavigateToStart = { navigateToStart() },
                        onAppSeen = {
                            shellPreferences.markAppSeen(it)
                            seenRevision++
                        },
                    )
                },
            )
        }

        val selectedTiles = tiles.filter { it.id in selectedTileIds }
        Windows81StartCustomizationBar(
            selectedTiles = selectedTiles,
            namingGroups = namingGroups,
            liveDisabledIds = liveDisabledIds,
            onUnpin = {
                val selected = selectedTileIds.toSet()
                tiles.removeAll { it.id in selected }
                selectedTileIds.clear()
                saveTiles()
            },
            onResize = { size ->
                val selected = selectedTileIds.toSet()
                tiles.indices.forEach { index ->
                    if (tiles[index].id in selected) tiles[index] = tiles[index].copy(size = size)
                }
                saveTiles()
            },
            onToggleLive = {
                val ids = selectedTileIds.toSet()
                val enable = ids.isNotEmpty() && ids.all { it in liveDisabledIds }
                shellPreferences.setLiveTileEnabled(ids, enable)
                liveDisabledIds = shellPreferences.getLiveTileDisabledIds()
            },
            onToggleNamingGroups = { namingGroups = !namingGroups },
            onClearSelection = { selectedTileIds.clear() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (activeInAppTile != null) {
            WindowsAppView(
                tile = activeInAppTile,
                appsRepository = appsRepository,
                onWallpaperParallaxChanged = { wallpaperParallaxEnabled = it },
                onWallpaperStyleChanged = { wallpaperStyle = it },
                onTestFlip = { tile, origin ->
                    closeInAppTileAndRetriggerEntrance()
                    onTriggerFlip(tile, Rect.Zero, origin)
                },
                onClose = { closeInAppTileAndRetriggerEntrance() },
            )
        }

        Windows81CharmsEdgeDetector(
            enabled = !flipState.isRunning,
            onReveal = { showCharms() },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        Windows81CharmsOverlay(
            visible = charmsVisible && !flipState.isRunning,
            requestedPane = charmsPane,
            apps = displayedApps,
            appsRepository = appsRepository,
            onDismiss = { charmsVisible = false },
            onSearchApp = { app ->
                charmsVisible = false
                shellPreferences.markAppSeen(app.packageName)
                seenRevision++
                onTriggerFlip(
                    TileModel(
                        id = "search_${app.packageName}",
                        title = app.label,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        colorValue = WindowsColors.Purple,
                    ),
                    Rect.Zero,
                    LaunchOrigin.ALL_APPS,
                )
            },
            onStart = { navigateToStart() },
            onOpenPcSettings = {
                charmsVisible = false
                onOpenInAppTile(
                    tiles.firstOrNull { it.tileType == TileType.SETTINGS }
                        ?: TileModel(
                            id = "tile_settings",
                            title = "PC settings",
                            colorValue = WindowsColors.SettingsPurple,
                            tileType = TileType.SETTINGS,
                            iconGlyph = "settings",
                        ),
                )
            },
            onOpenNotificationSettings = {
                charmsVisible = false
                onOpenNotificationSettings()
            },
            onOpenDevices = {
                charmsVisible = false
                onOpenDevices()
            },
            onOpenHelp = {
                charmsVisible = false
                onOpenInAppTile(
                    tiles.firstOrNull { it.id == "tile_help" }
                        ?: TileModel(
                            id = "tile_help",
                            title = "Help+Tips",
                            colorValue = WindowsColors.HelpOrange,
                            tileType = TileType.APP,
                            iconGlyph = "help",
                        ),
                )
            },
        )

        if (flipState.isRunning) {
            FlipLaunchOverlay(
                state = flipState,
                externalReverseRequest = externalLaunchReverseRequest,
                onExternalReverseComplete = onExternalLaunchReverseComplete,
                onAnimationEnd = {
                    if (flipState.isRunning && !flipLaunchDispatched) {
                        flipLaunchDispatched = true
                        flipState.sourceTile?.let(onLaunchTile)
                    }
                },
                destinationContent = if (flipState.hasInternalWindow) {
                    {
                        flipState.sourceTile?.let { tile ->
                            WindowsAppView(
                                tile = tile,
                                appsRepository = appsRepository,
                                onClose = { closeInAppTileAndRetriggerEntrance() },
                                onWallpaperStyleChanged = { wallpaperStyle = it },
                            )
                        }
                    }
                } else null,
            )
        }

        if (showPowerDialog) {
            PowerDialog(
                onDismiss = { showPowerDialog = false },
                onOpenLauncherSettings = {
                    showPowerDialog = false
                    onOpenInAppTile(
                        tiles.firstOrNull { it.tileType == TileType.SETTINGS }
                            ?: TileModel(
                                id = "tile_settings",
                                title = "PC settings",
                                colorValue = WindowsColors.SettingsPurple,
                                tileType = TileType.SETTINGS,
                                iconGlyph = "settings",
                            ),
                    )
                },
            )
        }
    }
}
