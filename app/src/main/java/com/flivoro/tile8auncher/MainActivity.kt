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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.apps.AllAppsScreen
import com.flivoro.tile8auncher.ui.components.FingerFollowingVerticalNavigation
import com.flivoro.tile8auncher.ui.components.FlipLaunchOverlay
import com.flivoro.tile8auncher.ui.components.WindowsAppView
import com.flivoro.tile8auncher.ui.components.WindowsWallpaper
import com.flivoro.tile8auncher.ui.dialogs.CustomizeTileDialog
import com.flivoro.tile8auncher.ui.dialogs.PinAppsDialog
import com.flivoro.tile8auncher.ui.dialogs.PowerDialog
import com.flivoro.tile8auncher.ui.start.StartScreen
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LauncherScreen {
    START,
    ALL_APPS
}

class MainActivity : ComponentActivity() {

    private lateinit var appsRepository: AppsRepository
    private var flipState by mutableStateOf(FlipAnimationState())
    private var activeInAppTile by mutableStateOf<TileModel?>(null)
    private var homeRequest by mutableIntStateOf(0)
    private var entranceRequest by mutableIntStateOf(0)
    private var pendingLaunchIntent: Intent? = null
    private var launchHandoffPending = false
    private var launcherForeground = false
    private var launcherResumed = false
    private var waitingForUserPresent = false
    private var entryHandledByUserPresent = false
    private var homeIntentPending = false

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT &&
                launcherForeground &&
                waitingForUserPresent
            ) {
                waitingForUserPresent = false
                entryHandledByUserPresent = true
                entranceRequest++
            }
        }
    }
    private var userPresentReceiverRegistered = false

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressLauncherTransitions()

        // Make system status and navigation bars 100% transparent edge-to-edge
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        appsRepository = AppsRepository(this)
        ContextCompat.registerReceiver(
            this,
            userPresentReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        userPresentReceiverRegistered = true

        setContent {
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                Tile8LauncherApp(
                    appsRepository = appsRepository,
                    flipState = flipState,
                    activeInAppTile = activeInAppTile,
                    homeRequest = homeRequest,
                    entranceRequest = entranceRequest,
                    onOpenInAppTile = { activeInAppTile = it },
                    onCloseInAppTile = {
                        activeInAppTile = null
                        flipState = FlipAnimationState(isRunning = false)
                    },
                    onTriggerFlip = { tile, bounds, origin ->
                        if (!flipState.isRunning) {
                            pendingLaunchIntent = resolveLaunchIntent(tile)
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
                    },
                    onLaunchTile = { tile -> handleTileLaunch(tile) },
                    onDismissFlip = {
                        // The overlay owns the transient animation state. The destination may
                        // already be visible underneath it when this callback arrives.
                        flipState = FlipAnimationState(isRunning = false)
                        pendingLaunchIntent = null
                    },
                    onOpenAppInfo = { packageName -> openAppInfo(packageName) },
                    onUninstallApp = { packageName -> uninstallApp(packageName) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        launcherForeground = true
    }

    override fun onStop() {
        launcherForeground = false
        launcherResumed = false
        waitingForUserPresent = false
        entryHandledByUserPresent = false
        super.onStop()
        // Wait until the launcher is fully covered before clearing the overlay. Clearing
        // from onPause would expose the scaled start screen during the app handoff.
        if (flipState.isRunning) {
            flipState = FlipAnimationState(isRunning = false)
        }
        pendingLaunchIntent = null
    }

    override fun onPause() {
        super.onPause()
        launcherResumed = false
        if (!launchHandoffPending && flipState.isRunning) {
            flipState = FlipAnimationState()
            pendingLaunchIntent = null
        }
    }

    override fun onResume() {
        super.onResume()
        suppressLauncherTransitions()
        val homeWasPending = homeIntentPending
        homeIntentPending = false
        val userPresentEntryHandled = entryHandledByUserPresent
        entryHandledByUserPresent = false
        launcherResumed = true
        val keyguardLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        waitingForUserPresent = keyguardLocked
        if (!keyguardLocked && !homeWasPending && !userPresentEntryHandled) {
            entranceRequest++
        }
        launchHandoffPending = false
        // Clear any animation left by a lifecycle interruption before drawing the launcher.
        if (flipState.isRunning) {
            flipState = FlipAnimationState(isRunning = false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        suppressLauncherTransitions()
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            activeInAppTile = null
            flipState = FlipAnimationState()
            pendingLaunchIntent = null
            homeIntentPending = !launcherResumed
            homeRequest++
        }
    }

    override fun onDestroy() {
        if (userPresentReceiverRegistered) {
            unregisterReceiver(userPresentReceiver)
            userPresentReceiverRegistered = false
        }
        super.onDestroy()
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
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityWithCustomAnim(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open App Info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityWithCustomAnim(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not request uninstall", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveLaunchIntent(tile: TileModel): Intent? {
        return try {
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
    }

    private fun handleTileLaunch(tile: TileModel) {
        val preparedIntent = pendingLaunchIntent
        pendingLaunchIntent = null
        if (preparedIntent == null) {
            activeInAppTile = tile
            flipState = FlipAnimationState()
            return
        }
        try {
            launchHandoffPending = true
            startActivityWithCustomAnim(preparedIntent)
            // Keep the completed launch face until onStop. A fixed fade timeout can
            // expose Start while a cold app is still creating its first window.
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
}

@Composable
fun Tile8LauncherApp(
    appsRepository: AppsRepository,
    flipState: FlipAnimationState,
    activeInAppTile: TileModel?,
    onOpenInAppTile: (TileModel) -> Unit,
    onCloseInAppTile: () -> Unit,
    onTriggerFlip: (tile: TileModel, bounds: Rect, origin: LaunchOrigin) -> Unit,
    onLaunchTile: (tile: TileModel) -> Unit,
    onDismissFlip: () -> Unit,
    onOpenAppInfo: (packageName: String) -> Unit,
    onUninstallApp: (packageName: String) -> Unit,
    homeRequest: Int = 0,
    entranceRequest: Int = 0,
) {
    var currentScreen by remember { mutableStateOf(LauncherScreen.START) }
    val drawerProgress = remember { mutableFloatStateOf(0f) }
    val startScroll = rememberLazyListState()
    val appsScroll = rememberLazyListState()
    var wallpaperParallaxEnabled by remember {
        mutableStateOf(appsRepository.getWallpaperParallaxEnabled())
    }
    var wallpaperStyle by remember { mutableIntStateOf(appsRepository.getWallpaperStyle()) }
    val tiles = remember { mutableStateListOf<TileModel>() }
    var selectedTileForCustomization by remember { mutableStateOf<TileModel?>(null) }
    var showPowerDialog by remember { mutableStateOf(false) }
    var showPinAppsDialog by remember { mutableStateOf(false) }
    var flipLaunchDispatched by remember(flipState) { mutableStateOf(false) }
    var localStartEntranceRequest by remember { mutableIntStateOf(0) }

    val startEntranceRequest = entranceRequest + homeRequest + localStartEntranceRequest

    fun navigateToAllApps() {
        currentScreen = LauncherScreen.ALL_APPS
    }

    fun navigateToStart() {
        if (currentScreen != LauncherScreen.START) {
            currentScreen = LauncherScreen.START
            localStartEntranceRequest++
        }
    }

    fun closeInAppTileAndRetriggerEntrance() {
        val wasOpen = activeInAppTile != null
        onCloseInAppTile()
        if (wasOpen) localStartEntranceRequest++
    }

    LaunchedEffect(homeRequest) { currentScreen = LauncherScreen.START }

    LaunchedEffect(Unit) {
        val pinned = withContext(Dispatchers.IO) { appsRepository.loadPinnedTiles() }
        tiles.clear()
        tiles.addAll(pinned)
    }

    val categorizedApps by produceState<List<AppSection>>(emptyList(), appsRepository) {
        value = withContext(Dispatchers.IO) { appsRepository.getCategorizedApps() }
    }

    // Start screen exit and entrance animation matching video (Release / Launch)
    val contentAlpha by animateFloatAsState(
        targetValue = if (flipState.isRunning || activeInAppTile != null) 0f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "StartScreenAlpha"
    )
    val contentScale by animateFloatAsState(
        targetValue = if (flipState.isRunning || activeInAppTile != null) 0.88f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "StartScreenScale"
    )

    // Back button handling
    BackHandler(enabled = flipState.isRunning || activeInAppTile != null || currentScreen == LauncherScreen.ALL_APPS) {
        if (flipState.isRunning && activeInAppTile != null) {
            closeInAppTileAndRetriggerEntrance()
        } else if (flipState.isRunning) {
            onDismissFlip()
        } else if (activeInAppTile != null) {
            closeInAppTileAndRetriggerEntrance()
        } else if (currentScreen == LauncherScreen.ALL_APPS) {
            navigateToStart()
        }
    }

    // Root Container with Static Windows 8.1 Purple Wallpaper
    Box(modifier = Modifier.fillMaxSize()) {
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

        // Start screen & All Apps drawer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val exiting = flipState.isRunning || activeInAppTile != null
                    this.alpha = if (exiting) contentAlpha else 1f
                    this.scaleX = if (exiting) contentScale else 1f
                    this.scaleY = if (exiting) contentScale else 1f
                }
        ) {
            FingerFollowingVerticalNavigation(
                showAllApps = currentScreen == LauncherScreen.ALL_APPS,
                onShowAllAppsChange = { showAllApps ->
                    currentScreen = if (showAllApps) LauncherScreen.ALL_APPS else LauncherScreen.START
                },
                resetRequest = homeRequest,
                progressState = drawerProgress,
                startContent = {
                    StartScreen(
                        listState = startScroll,
                        tiles = tiles,
                        launchingTileId = flipState.sourceTile?.id.takeIf { flipState.isRunning },
                        entranceRequest = startEntranceRequest,
                        appsRepository = appsRepository,
                        onTileClick = { tile, bounds ->
                            onTriggerFlip(tile, bounds, LaunchOrigin.START)
                        },
                        onTileLongClick = { tile ->
                            selectedTileForCustomization = tile
                        },
                        onPowerClick = { showPowerDialog = true },
                        onSearchClick = { navigateToAllApps() },
                        onAddAppsClick = { showPinAppsDialog = true },
                        onNavigateToAllApps = { navigateToAllApps() },
                    )
                },
                allAppsContent = {
                    AllAppsScreen(
                        listState = appsScroll,
                        sections = categorizedApps,
                        appsRepository = appsRepository,
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
                            val newTile = TileModel(
                                id = "app_${app.packageName}_${System.currentTimeMillis()}",
                                title = app.label,
                                packageName = app.packageName,
                                activityName = app.activityName,
                                colorValue = WindowsColors.ColorOptions.random(),
                                size = TileSize.MEDIUM,
                                order = tiles.size,
                            )
                            tiles.add(newTile)
                            appsRepository.savePinnedTiles(tiles.toList())
                        },
                        onUnpinApp = { pkg ->
                            tiles.removeAll { it.packageName == pkg }
                            appsRepository.savePinnedTiles(tiles.toList())
                        },
                        onOpenAppInfo = onOpenAppInfo,
                        onUninstallApp = onUninstallApp,
                        onNavigateToStart = { navigateToStart() },
                    )
                },
            )
        }

        // Active In-App Screen (Reading List, Money, Desktop, PC settings, Help+Tips)
        if (activeInAppTile != null) {
            WindowsAppView(
                onWallpaperParallaxChanged = { wallpaperParallaxEnabled = it },
                onWallpaperStyleChanged = { wallpaperStyle = it },
                tile = activeInAppTile,
                appsRepository = appsRepository,
                onTestFlip = { testTile, origin ->
                    closeInAppTileAndRetriggerEntrance()
                    onTriggerFlip(testTile, Rect(0f, 0f, 0f, 0f), origin)
                },
                onClose = { closeInAppTileAndRetriggerEntrance() },
            )
        }

        // 3D Flip App Opening Animation Overlay
        if (flipState.isRunning) {
            FlipLaunchOverlay(
                state = flipState,
                onAnimationEnd = {
                    if (flipState.isRunning && !flipLaunchDispatched) {
                        flipLaunchDispatched = true
                        flipState.sourceTile?.let { tile ->
                            onLaunchTile(tile)
                        }
                    }
                },
                destinationContent = if (flipState.hasInternalWindow) {
                    {
                        flipState.sourceTile?.let { tile ->
                            WindowsAppView(tile = tile, appsRepository = appsRepository,
                                onClose = { closeInAppTileAndRetriggerEntrance() },
                                onWallpaperStyleChanged = { wallpaperStyle = it },
                            )
                        }
                    }
                } else null,
            )
        }

        // Customize Tile Dialog (Resize / Color / Unpin)
        selectedTileForCustomization?.let { tile ->
            CustomizeTileDialog(
                tile = tile,
                onDismiss = { selectedTileForCustomization = null },
                onResize = { newSize ->
                    val index = tiles.indexOfFirst { it.id == tile.id }
                    if (index != -1) {
                        tiles[index] = tile.copy(size = newSize)
                        appsRepository.savePinnedTiles(tiles.toList())
                    }
                    selectedTileForCustomization = null
                },
                onColorChange = { newColor ->
                    val index = tiles.indexOfFirst { it.id == tile.id }
                    if (index != -1) {
                        tiles[index] = tile.copy(colorValue = newColor)
                        appsRepository.savePinnedTiles(tiles.toList())
                    }
                    selectedTileForCustomization = null
                },
                onUnpin = {
                    tiles.removeAll { it.id == tile.id }
                    appsRepository.savePinnedTiles(tiles.toList())
                    selectedTileForCustomization = null
                }
            )
        }

        // Pin Apps to Start Dialog (Accessible via "+" button on Start Screen)
        if (showPinAppsDialog) {
            val allInstalled by produceState<List<AppInfo>>(emptyList(), appsRepository) {
                value = withContext(Dispatchers.IO) { appsRepository.getInstalledApps() }
            }
            PinAppsDialog(
                installedApps = allInstalled,
                pinnedTiles = tiles,
                appsRepository = appsRepository,
                onDismiss = { showPinAppsDialog = false },
                onTogglePin = { app, isPinned ->
                    if (isPinned) {
                        tiles.removeAll { it.packageName == app.packageName }
                    } else {
                        val newTile = TileModel(
                            id = "app_${app.packageName}_${System.currentTimeMillis()}",
                            title = app.label,
                            packageName = app.packageName,
                            activityName = app.activityName,
                            colorValue = WindowsColors.ColorOptions.random(),
                            size = TileSize.MEDIUM,
                            order = tiles.size
                        )
                        tiles.add(newTile)
                    }
                    appsRepository.savePinnedTiles(tiles.toList())
                }
            )
        }

        // Windows 8.1 Power Dialog
        if (showPowerDialog) {
            PowerDialog(
                onDismiss = { showPowerDialog = false },
                onOpenLauncherSettings = {
                    showPowerDialog = false
                    val settingsTile = tiles.firstOrNull { it.tileType == TileType.SETTINGS }
                        ?: TileModel(
                            id = "tile_settings",
                            title = "PC settings",
                            colorValue = WindowsColors.SettingsPurple,
                            tileType = TileType.SETTINGS,
                            iconGlyph = "settings"
                        )
                    onOpenInAppTile(settingsTile)
                }
            )
        }
    }
}
