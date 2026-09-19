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
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.features.LiveTileRuntime
import com.flivoro.tile8auncher.ui.animation.FlipAnimationDirection
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.animation.FlipReverseReason
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.animation.StartEntranceKind
import com.flivoro.tile8auncher.ui.animation.StartEntranceMotion
import com.flivoro.tile8auncher.ui.apps.AllAppsScreen
import com.flivoro.tile8auncher.ui.components.FingerFollowingVerticalNavigation
import com.flivoro.tile8auncher.ui.components.FlipLaunchOverlay
import com.flivoro.tile8auncher.ui.components.WindowsAppView
import com.flivoro.tile8auncher.ui.components.WindowsWallpaper
import com.flivoro.tile8auncher.ui.components.Windows81LockScreen
import com.flivoro.tile8auncher.ui.components.WindowsCharmsOverlay
import com.flivoro.tile8auncher.ui.lockscreen.WindowsLockScreenPreferences
import com.flivoro.tile8auncher.ui.lockscreen.WindowsLockScreenRuntime
import com.flivoro.tile8auncher.ui.components.charmsEdgeGesture
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
    private var entranceKind by mutableStateOf(StartEntranceKind.STARTUP)
    private var entranceReady by mutableStateOf(false)
    private var startupEntrancePending = true
    private var pendingLaunchIntent: Intent? = null
    private var launchHandoffPending = false
    private var launcherForeground = false
    private var launcherResumed = false
    private var waitingForUserPresent = false
    private var userPresentObserved = false
    private var homeIntentPending = false
    private var unlockReleaseGeneration = 0

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Keep the already-existing launcher surface alive but submit only its wallpaper.
                    // Incrementing the generation cancels every pending unlock release callback.
                    unlockReleaseGeneration++
                    userPresentObserved = false
                    startupEntrancePending = true
                    waitingForUserPresent = true
                    entranceReady = false
                    activeInAppTile = null
                    pendingLaunchIntent = null
                    if (flipState.isRunning) flipState = FlipAnimationState()
                }

                Intent.ACTION_USER_PRESENT -> {
                    // USER_PRESENT and Activity resume/keyguard state have no stable ordering across
                    // OEMs. Remember the event even if the launcher is not resumed yet; onResume
                    // will finish the same gate instead of losing this notification.
                    userPresentObserved = true
                    scheduleUnlockReleaseIfReady()
                }
            }
        }
    }
    private var userPresentReceiverRegistered = false

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressLauncherTransitions()

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
            IntentFilter(Intent.ACTION_USER_PRESENT).apply { addAction(Intent.ACTION_SCREEN_OFF) },
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
                    entranceKind = entranceKind,
                    entranceReady = entranceReady,
                    onOpenInAppTile = { activeInAppTile = it },
                    onCloseInAppTile = {
                        activeInAppTile = null
                        flipState = FlipAnimationState(isRunning = false)
                    },
                    onTriggerFlip = { tile, bounds, origin ->
                        if (entranceReady && !flipState.isRunning) {
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
                    onRequestFlipReverse = { reason -> requestFlipReverse(reason) },
                    onLaunchTile = { tile -> handleTileLaunch(tile) },
                    onDismissFlip = {
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
        if (startupEntrancePending && userPresentObserved) scheduleUnlockReleaseIfReady()
    }

    override fun onStop() {
        launcherForeground = false
        launcherResumed = false
        super.onStop()
        if (flipState.isRunning) {
            flipState = FlipAnimationState(isRunning = false)
        }
        pendingLaunchIntent = null
    }

    override fun onPause() {
        super.onPause()
        launcherResumed = false

        // Some OEMs deliver ACTION_SCREEN_OFF after onPause. Pre-arm the same background-only gate
        // here when the display/keyguard already says the device is leaving the interactive state.
        if (deviceRequiresEntranceGate()) {
            unlockReleaseGeneration++
            startupEntrancePending = true
            waitingForUserPresent = true
            entranceReady = false
        }

        if (!launchHandoffPending && flipState.isRunning &&
            flipState.direction != FlipAnimationDirection.REVERSE
        ) {
            flipState = FlipAnimationState()
            pendingLaunchIntent = null
        }
    }

    override fun onResume() {
        super.onResume()
        LiveTileRuntime.requestReconnect(this)
        suppressLauncherTransitions()
        val homeWasPending = homeIntentPending
        homeIntentPending = false
        launcherResumed = true

        val gatedBySystem = deviceRequiresEntranceGate()
        waitingForUserPresent = gatedBySystem
        if (gatedBySystem) {
            startupEntrancePending = true
            entranceReady = false
            if (userPresentObserved) scheduleUnlockReleaseIfReady()
        } else if (startupEntrancePending) {
            finishStartupEntranceGate()
        } else {
            entranceReady = true
            if (!homeWasPending) {
                entranceKind = StartEntranceKind.RETURN
                entranceRequest++
            }
        }

        launchHandoffPending = false
        if (flipState.isRunning && flipState.direction != FlipAnimationDirection.REVERSE) {
            flipState = FlipAnimationState(isRunning = false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        suppressLauncherTransitions()
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (flipState.isRunning) {
                requestFlipReverse(FlipReverseReason.HOME)
                return
            }

            pendingLaunchIntent = null
            val returningFromOutside = !launcherResumed
            homeIntentPending = returningFromOutside
            if (returningFromOutside && entranceReady) {
                entranceKind = StartEntranceKind.RETURN
                entranceRequest++
            }
            homeRequest++
        }
    }

    override fun onDestroy() {
        unlockReleaseGeneration++
        if (userPresentReceiverRegistered) {
            unregisterReceiver(userPresentReceiver)
            userPresentReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun scheduleUnlockReleaseIfReady() {
        if (!startupEntrancePending) return
        val generation = unlockReleaseGeneration
        if (!launcherResumed) return

        window.decorView.postOnAnimation(object : Runnable {
            override fun run() {
                if (generation != unlockReleaseGeneration || !startupEntrancePending) return
                if (!launcherResumed) return
                if (deviceRequiresEntranceGate()) {
                    // USER_PRESENT can precede the final keyguard state transition by several
                    // frames. Retry on the same window rather than leaving entranceReady=false.
                    window.decorView.postOnAnimation(this)
                    return
                }
                finishStartupEntranceGate()
            }
        })
    }

    private fun finishStartupEntranceGate() {
        if (!startupEntrancePending) {
            entranceReady = true
            return
        }
        waitingForUserPresent = false
        userPresentObserved = false
        startupEntrancePending = false
        entranceReady = true
        entranceKind = StartEntranceKind.STARTUP
        entranceRequest++
    }

    private fun deviceRequiresEntranceGate(): Boolean {
        val keyguardLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive != false
        return keyguardLocked || !interactive
    }

    private fun requestFlipReverse(reason: FlipReverseReason) {
        if (!flipState.isRunning) return
        pendingLaunchIntent = null
        launchHandoffPending = false
        when {
            flipState.direction == FlipAnimationDirection.FORWARD -> {
                flipState = flipState.copy(
                    direction = FlipAnimationDirection.REVERSE,
                    reverseReason = reason,
                )
            }
            reason == FlipReverseReason.HOME && flipState.reverseReason != FlipReverseReason.HOME -> {
                flipState = flipState.copy(reverseReason = FlipReverseReason.HOME)
            }
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
    onRequestFlipReverse: (FlipReverseReason) -> Unit,
    onLaunchTile: (tile: TileModel) -> Unit,
    onDismissFlip: () -> Unit,
    onOpenAppInfo: (packageName: String) -> Unit,
    onUninstallApp: (packageName: String) -> Unit,
    homeRequest: Int = 0,
    entranceRequest: Int = 0,
    entranceKind: StartEntranceKind = StartEntranceKind.RETURN,
    entranceReady: Boolean = true,
) {
    var currentScreen by remember { mutableStateOf(LauncherScreen.START) }
    val drawerProgress = remember { mutableFloatStateOf(0f) }
    val appsFullyVisible by remember { derivedStateOf { drawerProgress.floatValue >= 0.999f } }
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
    var flipLaunchDispatched by remember(flipState.sourceTile?.id, flipState.sourceBounds) {
        mutableStateOf(false)
    }
    val flipProgress = remember(flipState.sourceTile?.id, flipState.sourceBounds) { Animatable(0f) }
    val wallpaperEntrance = remember { Animatable(if (entranceReady) 1f else 0f) }
    var localStartEntranceRequest by remember { mutableIntStateOf(0) }
    var startEntranceKind by remember(entranceRequest, homeRequest) { mutableStateOf(entranceKind) }
    var showCharms by remember { mutableStateOf(false) }
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    var drawerResetRequest by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val startEntranceRequest = entranceRequest + localStartEntranceRequest

    LaunchedEffect(startEntranceRequest, entranceReady, startEntranceKind) {
        wallpaperEntrance.stop()
        if (!entranceReady) {
            wallpaperEntrance.snapTo(0f)
        } else {
            wallpaperEntrance.snapTo(0f)
            wallpaperEntrance.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = StartEntranceMotion.durationMillis(startEntranceKind),
                    easing = LinearEasing,
                ),
            )
        }
    }
    LaunchedEffect(flipState.isRunning) {
        if (flipState.isRunning) wallpaperEntrance.stop()
    }

    // Screen-off is a real UI mode, not merely alpha=0. Close transient launcher chrome and put
    // the vertical navigator back on Start while the wallpaper remains the only rendered layer.
    LaunchedEffect(entranceReady) {
        if (!entranceReady) {
            showCharms = false
            selectedTileForCustomization = null
            showPinAppsDialog = false
            showPowerDialog = false
            currentScreen = LauncherScreen.START
            drawerResetRequest++
        }
    }

    fun navigateToAllApps() {
        if (entranceReady) currentScreen = LauncherScreen.ALL_APPS
    }

    fun searchApps() {
        if (!entranceReady) return
        showCharms = false
        if (activeInAppTile != null) onCloseInAppTile()
        navigateToAllApps()
        searchFocusRequest++
    }

    fun openLauncherSettings() {
        if (!entranceReady) return
        showCharms = false
        val settingsTile = tiles.firstOrNull { it.tileType == TileType.SETTINGS }
            ?: TileModel(id = "tile_settings", title = "PC settings",
                colorValue = WindowsColors.SettingsPurple, tileType = TileType.SETTINGS,
                iconGlyph = "settings")
        onOpenInAppTile(settingsTile)
    }

    fun openDeviceSettings() {
        if (!entranceReady) return
        showCharms = false
        val intent = Intent(android.provider.Settings.ACTION_CAST_SETTINGS)
        val fallback = Intent(android.provider.Settings.ACTION_SETTINGS)
        try {
            val destination = if (intent.resolveActivity(context.packageManager) != null) intent else fallback
            destination.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            context.startActivity(destination, ActivityOptions.makeCustomAnimation(
                context, R.anim.no_anim, R.anim.no_anim).toBundle())
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "Device settings are unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    fun returnToStart() {
        val wasAwayFromStart = currentScreen != LauncherScreen.START || activeInAppTile != null
        showCharms = false
        selectedTileForCustomization = null
        showPinAppsDialog = false
        showPowerDialog = false
        if (activeInAppTile != null) onCloseInAppTile()
        if (currentScreen != LauncherScreen.START) {
            currentScreen = LauncherScreen.START
            drawerResetRequest++
        }
        if (wasAwayFromStart && entranceReady) {
            startEntranceKind = StartEntranceKind.RETURN
            localStartEntranceRequest++
        }
    }

    fun navigateToStart() {
        if (currentScreen != LauncherScreen.START) {
            currentScreen = LauncherScreen.START
        }
    }

    fun closeInAppTileAndRetriggerEntrance() {
        val wasOpen = activeInAppTile != null
        onCloseInAppTile()
        if (wasOpen && entranceReady) {
            startEntranceKind = StartEntranceKind.RETURN
            localStartEntranceRequest++
        }
    }

    LaunchedEffect(homeRequest) {
        if (homeRequest > 0) returnToStart()
    }
    LaunchedEffect(flipState.isRunning) {
        if (flipState.isRunning) showCharms = false
    }

    LaunchedEffect(Unit) {
        val pinned = withContext(Dispatchers.IO) { appsRepository.loadPinnedTiles() }
        tiles.clear()
        tiles.addAll(pinned)
    }

    val categorizedApps by produceState<List<AppSection>>(emptyList(), appsRepository) {
        value = withContext(Dispatchers.IO) { appsRepository.getCategorizedApps() }
    }

    val activeContentRetreat by animateFloatAsState(
        targetValue = if (activeInAppTile != null) 1f else 0f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "ActiveContentRetreat",
    )

    BackHandler(enabled = entranceReady && !showCharms &&
        (flipState.isRunning || activeInAppTile != null || currentScreen == LauncherScreen.ALL_APPS)) {
        if (flipState.isRunning) {
            onRequestFlipReverse(FlipReverseReason.BACK)
        } else if (activeInAppTile != null) {
            closeInAppTileAndRetriggerEntrance()
        } else if (currentScreen == LauncherScreen.ALL_APPS) {
            navigateToStart()
        }
    }

    val charmsAvailable = entranceReady && !flipState.isRunning && !showPowerDialog &&
        !showPinAppsDialog && selectedTileForCustomization == null
    Box(modifier = Modifier.fillMaxSize()
        .charmsEdgeGesture(enabled = charmsAvailable && !showCharms) { showCharms = true }
        .semantics {
            if (charmsAvailable && !showCharms) {
                customActions = listOf(CustomAccessibilityAction("Open charms") {
                    showCharms = true
                    true
                })
            }
        }) {
        WindowsWallpaper(
            wallpaperStyle = wallpaperStyle,
            enabled = wallpaperParallaxEnabled || wallpaperEntrance.value < 0.9999f,
            scrollOffsetPx = {
                fun offset(state: androidx.compose.foundation.lazy.LazyListState): Float {
                    val info = state.layoutInfo
                    val itemWidth = info.visibleItemsInfo.firstOrNull()?.size ?: 0
                    return (state.firstVisibleItemIndex.toFloat() * (itemWidth + info.mainAxisItemSpacing) +
                        state.firstVisibleItemScrollOffset).coerceAtLeast(0f)
                }
                val p = drawerProgress.floatValue
                val userScroll = offset(startScroll) * (1f - p) + offset(appsScroll) * p
                val displayMetrics = context.resources.displayMetrics
                val viewportWidthPx = displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                val viewportHeightPx = displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
                val entranceOffsetPx = if (currentScreen == LauncherScreen.START && activeInAppTile == null) {
                    StartEntranceMotion.backgroundEntranceOffsetPx(
                        progress = wallpaperEntrance.value,
                        kind = startEntranceKind,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx,
                    )
                } else {
                    0f
                }
                userScroll - entranceOffsetPx
            },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showCharms) Modifier.clearAndSetSemantics {} else Modifier)
                .pointerInput(entranceReady) {
                    if (!entranceReady) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    val retreat = if (flipState.isRunning) {
                        val fullDuration = flipState.timing.durationMillis.coerceIn(100, 2000).toFloat()
                        val elapsedMillis = flipProgress.value.coerceIn(0f, 1f) * fullDuration
                        FastOutSlowInEasing.transform((elapsedMillis / 140f).coerceIn(0f, 1f))
                    } else {
                        activeContentRetreat
                    }
                    val sleepGate = if (entranceReady) 1f else 0f
                    this.alpha = sleepGate * (1f - retreat)
                    this.scaleX = 1f - 0.12f * retreat
                    this.scaleY = 1f - 0.12f * retreat
                }
        ) {
            FingerFollowingVerticalNavigation(
                showAllApps = currentScreen == LauncherScreen.ALL_APPS,
                onShowAllAppsChange = { showAllApps ->
                    if (showAllApps) navigateToAllApps() else navigateToStart()
                },
                enabled = entranceReady && !showCharms && !flipState.isRunning && activeInAppTile == null,
                resetRequest = homeRequest + drawerResetRequest,
                progressState = drawerProgress,
                startContent = {
                    StartScreen(
                        listState = startScroll,
                        tiles = tiles,
                        launchingTileId = flipState.sourceTile?.id.takeIf { flipState.isRunning },
                        entranceRequest = startEntranceRequest,
                        entranceKind = startEntranceKind,
                        entranceEnabled = entranceReady && currentScreen == LauncherScreen.START &&
                            !flipState.isRunning && activeInAppTile == null,
                        prehideForEntrance = !entranceReady,
                        interactionEnabled = entranceReady && !showCharms && !flipState.isRunning && activeInAppTile == null,
                        appsRepository = appsRepository,
                        onTileClick = { tile, bounds ->
                            if (entranceReady) onTriggerFlip(tile, bounds, LaunchOrigin.START)
                        },
                        onTileLongClick = { tile ->
                            if (entranceReady) selectedTileForCustomization = tile
                        },
                        onTilesChanged = { updatedTiles ->
                            if (entranceReady) {
                                tiles.clear()
                                tiles.addAll(updatedTiles)
                                appsRepository.savePinnedTiles(updatedTiles)
                            }
                        },
                        onPowerClick = { if (entranceReady) showPowerDialog = true },
                        onSearchClick = { searchApps() },
                        onCharmsClick = { if (entranceReady) showCharms = true },
                        onAddAppsClick = { if (entranceReady) showPinAppsDialog = true },
                        onNavigateToAllApps = { navigateToAllApps() },
                    )
                },
                allAppsContent = {
                    AllAppsScreen(
                        listState = appsScroll,
                        searchFocusRequest = searchFocusRequest,
                        searchFocusEnabled = entranceReady && appsFullyVisible && !showCharms && activeInAppTile == null,
                        sections = categorizedApps,
                        appsRepository = appsRepository,
                        onAppClick = { app, bounds ->
                            if (entranceReady) {
                                val tile = TileModel(
                                    id = "app_${app.packageName}",
                                    title = app.label,
                                    packageName = app.packageName,
                                    activityName = app.activityName,
                                    colorValue = WindowsColors.Purple,
                                    size = TileSize.MEDIUM,
                                )
                                onTriggerFlip(tile, bounds, LaunchOrigin.ALL_APPS)
                            }
                        },
                        isAppPinned = { pkg -> tiles.any { it.packageName == pkg } },
                        onPinApp = { app ->
                            if (entranceReady) {
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
                            }
                        },
                        onUnpinApp = { pkg ->
                            if (entranceReady) {
                                tiles.removeAll { it.packageName == pkg }
                                appsRepository.savePinnedTiles(tiles.toList())
                            }
                        },
                        onOpenAppInfo = { pkg -> if (entranceReady) onOpenAppInfo(pkg) },
                        onUninstallApp = { pkg -> if (entranceReady) onUninstallApp(pkg) },
                        onNavigateToStart = { navigateToStart() },
                    )
                },
            )
        }

        if (entranceReady && activeInAppTile != null) {
            Box(Modifier.fillMaxSize().then(if (showCharms) Modifier.clearAndSetSemantics {} else Modifier)) {
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
        }

        WindowsCharmsOverlay(
            visible = entranceReady && showCharms,
            apps = remember(categorizedApps) { categorizedApps.flatMap { it.apps } },
            appsRepository = appsRepository,
            onAppClick = { app, bounds ->
                if (entranceReady) {
                    showCharms = false
                    if (activeInAppTile != null) onCloseInAppTile()
                    onTriggerFlip(TileModel(
                        id = "app_${app.packageName}", title = app.label,
                        packageName = app.packageName, activityName = app.activityName,
                        colorValue = WindowsColors.Purple, size = TileSize.MEDIUM,
                    ), bounds, LaunchOrigin.ALL_APPS)
                }
            },
            onDismiss = { showCharms = false },
            onStart = { returnToStart() },
            onSearch = { searchApps() },
            onSettings = { openLauncherSettings() },
            onAddApps = { if (entranceReady) { showCharms = false; showPinAppsDialog = true } },
            onDevices = { openDeviceSettings() },
            onPower = { if (entranceReady) { showCharms = false; showPowerDialog = true } },
        )

        if (entranceReady && flipState.isRunning) {
            FlipLaunchOverlay(
                state = flipState,
                progress = flipProgress,
                onAnimationEnd = {
                    if (flipState.isRunning &&
                        flipState.direction == FlipAnimationDirection.FORWARD &&
                        !flipLaunchDispatched
                    ) {
                        flipLaunchDispatched = true
                        flipState.sourceTile?.let { tile ->
                            onLaunchTile(tile)
                        }
                    }
                },
                onReverseAnimationEnd = { reason ->
                    onDismissFlip()
                    if (reason == FlipReverseReason.HOME) {
                        returnToStart()
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

        if (entranceReady) {
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
        }

        if (entranceReady && showPinAppsDialog) {
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

        if (entranceReady && showPowerDialog) {
            PowerDialog(
                onDismiss = { showPowerDialog = false },
                onOpenLauncherSettings = {
                    showPowerDialog = false
                    openLauncherSettings()
                }
            )
        }

        // Keep the emulated Windows lock surface in the same full-screen Compose window as Start.
        // It is the final child so no Start/wallpaper content can draw over its clipped rectangle.
        if (entranceReady &&
            WindowsLockScreenRuntime.pending &&
            WindowsLockScreenPreferences.isEnabled(context)
        ) {
            Windows81LockScreen(
                cameraGestureEnabled = WindowsLockScreenPreferences.isCameraGestureEnabled(context),
                onDismiss = { WindowsLockScreenRuntime.dismiss() },
            )
        }
    }
}
