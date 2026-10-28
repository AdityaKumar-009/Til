package com.flivoro.tile8auncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.prepareLauncher
import com.flivoro.tile8auncher.data.PreparedLauncher
import com.flivoro.tile8auncher.data.SharedStartWorkspace
import com.flivoro.tile8auncher.data.warmLauncherArtwork
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.data.boundTileLaunchIntent
import com.flivoro.tile8auncher.data.defaultTilePickerBaseIntent
import com.flivoro.tile8auncher.data.defaultTileSemanticIntent
import com.flivoro.tile8auncher.data.isStockDefaultTileId
import com.flivoro.tile8auncher.data.resolveDefaultTileApp
import com.flivoro.tile8auncher.features.AppIconStyle
import com.flivoro.tile8auncher.features.LiveTileRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.canRequestAppUninstall
import com.flivoro.tile8auncher.features.userConfirmedAppUninstallIntent
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import com.flivoro.tile8auncher.features.PhoneBackgroundStyle
import com.flivoro.tile8auncher.ui.phone.PhoneLauncherSurface
import com.flivoro.tile8auncher.ui.animation.FlipAnimationDirection
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.animation.FlipReverseReason
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.animation.StartEntranceKind
import com.flivoro.tile8auncher.ui.apps.AllAppsScreen
import com.flivoro.tile8auncher.ui.components.FingerFollowingVerticalNavigation
import com.flivoro.tile8auncher.ui.components.FlipLaunchOverlay
import com.flivoro.tile8auncher.ui.components.LocalStoryLaunchScene
import com.flivoro.tile8auncher.ui.components.StoryLaunchScene
import com.flivoro.tile8auncher.ui.phone.PhoneLaunchSourceFace
import com.flivoro.tile8auncher.ui.phone.PhoneLaunchDestinationIcon
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import com.flivoro.tile8auncher.ui.components.WindowsAppView
import com.flivoro.tile8auncher.ui.components.cachedAllAppsIconForCurrentStyle
import com.flivoro.tile8auncher.ui.components.cachedAppIconForCurrentStyle
import com.flivoro.tile8auncher.ui.components.LauncherStartupSplash
import com.flivoro.tile8auncher.ui.components.LightweightSettingsLaunchPreview
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LauncherScreen {
    START,
    ALL_APPS
}

private data class PendingDefaultTilePick(
    val tileId: String,
    val bounds: Rect,
)

class MainActivity : ComponentActivity() {

    private lateinit var appsRepository: AppsRepository
    private var flipState by mutableStateOf(FlipAnimationState())
    // Only story presets replay a full overlay after returning from an external
    // activity. A phone borrowing Desktop 8.1 plays the Desktop OPEN only;
    // normal Back/Home returns use the phone mode's original entrance.
    private var pendingStoryReturn: FlipAnimationState? = null
    private var activeInAppTile by mutableStateOf<TileModel?>(null)
    private var homeRequest by mutableIntStateOf(0)
    private var entranceRequest by mutableIntStateOf(0)
    private var entranceKind by mutableStateOf(StartEntranceKind.STARTUP)
    private var phoneResumeUsesBackMotion by mutableStateOf(false)
    private var entranceReady by mutableStateOf(false)
    private var startupEntrancePending = true
    private var pendingLaunchIntent: Intent? = null
    private var launchHandoffPending = false
    private var launcherResumed = false
    private var homeIntentPending = false
    private val entranceHandler = Handler(Looper.getMainLooper())
    private val entranceRecovery = LauncherEntranceRecovery(
        isPending = { startupEntrancePending },
        isResumed = { launcherResumed },
        isInteractive = { isDeviceInteractive() },
        isKeyguardLocked = { isDeviceKeyguardLocked() },
        postDelayed = { callback, delay -> entranceHandler.postDelayed(callback, delay) },
        removeCallbacks = { callback -> entranceHandler.removeCallbacks(callback) },
        onReady = { finishStartupEntranceGate() },
    )
    // Ignore delayed activity-resume animation callbacks after a pause, a
    // replacement resume, or destruction. Disco delays activityResume by 200ms.
    private var classicPhoneResumeGeneration = 0
    private var pendingClassicHomeReturn = false
    private var classicExternalAppLaunched = false
    private var mobileExternalAppLaunched = false
    private var phoneReturnSourceTileId by mutableStateOf<String?>(null)
    private var phoneAllAppsVisible = false
    private var screenOffRequest by mutableIntStateOf(0)

    // The system uninstaller must be launched for a result, not as a NEW_TASK
    // no-animation app launch. Android broadcasts PACKAGE_REMOVED after confirmation.
    private val uninstallRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Also refresh if an OEM failed to send us its package broadcast.
            PackageCatalogUpdates.notifyPackageChanged()
        }
    }

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Discard transient launch state from the previous wake cycle. The
                    // existing surface/workspace stays mounted behind the entrance gate.
                    screenOffRequest++
                    pendingStoryReturn = null
                    classicExternalAppLaunched = false
                    mobileExternalAppLaunched = false
                    launchHandoffPending = false
                    phoneReturnSourceTileId = null
                    activeInAppTile = null
                    pendingLaunchIntent = null
                    if (flipState.isRunning) flipState = FlipAnimationState()
                    beginStartupEntranceGate()
                }

                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    // Signals prompt a state check; neither broadcast is a prerequisite.
                    entranceRecovery.recheck()
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

        // A genuine startup crash must not strand HOME. Never infer a crash merely
        // because Android started a fresh launcher process after ordinary shutdown.
        if (savedInstanceState == null) {
            LauncherFeatureStore.recoverInterruptedPhoneRender(this)
        }
        LauncherFeatureStore.beginPhoneRender(this)
        // Restore the persisted custom wallpaper/accent before ANY Start or
        // All Apps UI reads the observable personalization state. Previously it
        // was initialized only when opening the wallpaper picker, so custom
        // images silently disappeared after a cold launcher restart.
        StartPersonalization.ensureLoaded(this)
        appsRepository = (application as Tile8Application).appsRepository
        ContextCompat.registerReceiver(
            this,
            userPresentReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT).apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
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
                    resumeUsesBackMotion = phoneResumeUsesBackMotion,
                    screenOffRequest = screenOffRequest,
                    returningPhoneTileId = phoneReturnSourceTileId,
                    entranceReady = entranceReady,
                    onOpenInAppTile = { activeInAppTile = it },
                    onCloseInAppTile = {
                        activeInAppTile = null
                        flipState = FlipAnimationState(isRunning = false)
                    },
                    onTriggerFlip = { tile, bounds, origin ->
                        if (entranceReady && !flipState.isRunning) {
                            // The launcher-native Settings screen must not resolve
                            // to Android Settings' stock semantic intent.
                            pendingLaunchIntent = if (tile.tileType == TileType.SETTINGS) null
                                else resolveLaunchIntent(tile)
                            val uiMode = LauncherFeatureStore.launcherUiMode(this@MainActivity)
                            val layeredIcons = LauncherFeatureStore.appIconStyle(this@MainActivity) ==
                                com.flivoro.tile8auncher.features.AppIconStyle.DISCO_LAYERED
                            // Both phone All Apps panes, and WP8.1 Start, actually
                            // draw normalized layered glyphs. Capture the same
                            // cached glyph instead of substituting a different
                            // Desktop/Android icon when the live snapshot is culled.
                            val phoneVisibleIcon = tile.packageName?.takeIf {
                                layeredIcons && (origin == LaunchOrigin.ALL_APPS &&
                                    uiMode != LauncherUiMode.DESKTOP ||
                                    uiMode == LauncherUiMode.PHONE_8)
                            }?.let(appsRepository::getCachedWp81NormalizedIcon)
                            val icon = phoneVisibleIcon ?: if (origin == LaunchOrigin.ALL_APPS) {
                                cachedAllAppsIconForCurrentStyle(
                                    context = this@MainActivity,
                                    repository = appsRepository,
                                    packageName = tile.packageName,
                                ) ?: cachedAppIconForCurrentStyle(
                                    context = this@MainActivity,
                                    repository = appsRepository,
                                    packageName = tile.packageName,
                                )
                            } else {
                                cachedAppIconForCurrentStyle(
                                    context = this@MainActivity,
                                    repository = appsRepository,
                                    packageName = tile.packageName,
                                )
                            }
                            val isPhone = uiMode != LauncherUiMode.DESKTOP
                            val tilePicture = origin == LaunchOrigin.START &&
                                LauncherFeatureStore.backgroundStyle(this@MainActivity, uiMode) ==
                                    PhoneBackgroundStyle.TILE_PICTURE
                            val initialOpacity = if (tilePicture) 0f
                                else if (isPhone && origin == LaunchOrigin.START &&
                                    uiMode == LauncherUiMode.MOBILE_10)
                                    LauncherFeatureStore.phoneTileOpacity(this@MainActivity)
                                else 1f
                            // Resolve the *same* tile color as the phone Start renderer;
                            // a borrowed Desktop destination must not fall back to the
                            // tile's old, pre-wallpaper accent color.
                            val launchColor = if (isPhone) {
                                com.flivoro.tile8auncher.ui.phone.resolvePhoneTileBackgroundArgb(
                                    tile.id, tile.title, tile.colorValue,
                                    tile.packageName?.let(appsRepository::getDiscoTileAccentColor),
                                    com.flivoro.tile8auncher.ui.phone.PhoneLayoutStore
                                        .hasCustomColor(this@MainActivity, uiMode, tile.id),
                                    LauncherFeatureStore.appIconStyle(this@MainActivity) ==
                                        com.flivoro.tile8auncher.features.AppIconStyle.DISCO_LAYERED,
                                    StartPersonalization.wallpaperAccentArgb,
                                )
                            } else tile.colorValue
                            val launchTile = if (launchColor != tile.colorValue)
                                tile.copy(colorValue = launchColor) else tile
                            flipState = FlipAnimationState(
                                isRunning = true,
                                sourceTile = launchTile,
                                sourceBounds = bounds,
                                appIcon = icon,
                                accentColor = launchColor.toTileColor(),
                                sourceInitialOpacity = initialOpacity,
                                sourcePictureMode = tilePicture,
                                sourceWallpaperStyle = appsRepository.getWallpaperStyle(),
                                animationMode = if (
                                    LauncherFeatureStore.masterLaunchAnimation(this@MainActivity) ==
                                        MasterLaunchAnimation.DESKTOP
                                ) com.flivoro.tile8auncher.data.FlipAnimationMode.CLASSIC
                                else appsRepository.getFlipAnimationMode(),
                                masterLaunchAnimation = LauncherFeatureStore.masterLaunchAnimation(this@MainActivity),
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
                    onUninstallApp = { packageName -> uninstallApp(packageName) },
                    onPhonePageChanged = { phoneAllAppsVisible = it },
                )
            }
        }
    }

    override fun onStop() {
        launcherResumed = false
        entranceRecovery.cancel()
        classicPhoneResumeGeneration++
        super.onStop()
        if (flipState.isRunning) {
            flipState = FlipAnimationState(isRunning = false)
        }
        pendingLaunchIntent = null
        // Backgrounding HOME before the first settled frame is an ordinary
        // lifecycle transition, not evidence that the renderer crashed.
        LauncherFeatureStore.confirmPhoneRender(this)
        LauncherFeatureStore.confirmPhoneSettings(this)
    }

    override fun onPause() {
        super.onPause()
        launcherResumed = false
        entranceRecovery.cancel()
        classicPhoneResumeGeneration++

        // Some OEMs deliver ACTION_SCREEN_OFF after onPause. Pre-arm the same background-only gate
        // here when the display/keyguard already says the device is leaving the interactive state.
        if (deviceRequiresEntranceGate()) {
            beginStartupEntranceGate()
        }

        if (!launchHandoffPending && flipState.isRunning &&
            flipState.direction != FlipAnimationDirection.REVERSE
        ) {
            flipState = FlipAnimationState()
            pendingLaunchIntent = null
        }
    }

    private fun scheduleClassicPhoneResume() {
        // src/script.js in DiscoLauncher defers activityResume -> onResume()
        // for 200ms. Keep a black launcher surface in the meantime so the
        // Android task switch cannot consume the native-style tile entrance.
        entranceReady = false
        val generation = ++classicPhoneResumeGeneration
        entranceHandler.postDelayed({
            if (generation != classicPhoneResumeGeneration || !launcherResumed) return@postDelayed
            if (deviceRequiresEntranceGate() || startupEntrancePending) {
                // A lock during this delay must transfer ownership of the hidden
                // surface to unlock recovery, not abandon entranceReady=false.
                beginStartupEntranceGate()
                return@postDelayed
            }

            entranceKind = StartEntranceKind.RETURN
            // Explicit Home enters from the right; returning from an app
            // launched here uses the native Back turn from the left.
            phoneResumeUsesBackMotion = classicExternalAppLaunched && !pendingClassicHomeReturn
            classicExternalAppLaunched = false
            entranceReady = true
            entranceRequest++
            if (pendingClassicHomeReturn) {
                pendingClassicHomeReturn = false
                homeRequest++
            }
        }, 200L)
    }

    override fun onResume() {
        super.onResume()
        LiveTileRuntime.requestReconnect(this)
        suppressLauncherTransitions()
        val homeWasPending = homeIntentPending
        homeIntentPending = false
        launcherResumed = true

        val gatedBySystem = deviceRequiresEntranceGate()
        if (gatedBySystem) {
            pendingStoryReturn = null
            phoneResumeUsesBackMotion = false
            beginStartupEntranceGate()
        } else if (startupEntrancePending) {
            phoneResumeUsesBackMotion = false
            finishStartupEntranceGate()
        } else if (pendingStoryReturn != null ||
            (flipState.isRunning && (
                flipState.masterLaunchAnimation.isStory
            ) && flipState.direction == FlipAnimationDirection.REVERSE)) {
            entranceReady = true
            pendingStoryReturn?.let {
                flipState = it.copy(direction = FlipAnimationDirection.REVERSE,
                    reverseReason = if (homeWasPending) FlipReverseReason.HOME else FlipReverseReason.BACK)
            }
            pendingStoryReturn = null
            classicExternalAppLaunched = false
            mobileExternalAppLaunched = false
            phoneResumeUsesBackMotion = false
            pendingClassicHomeReturn = false
        } else {
            val mode = LauncherFeatureStore.launcherUiMode(this)
            if (mode == LauncherUiMode.PHONE_8) {
                // Only an external app handoff or true Home return can replay
                // the classic animation. An incidental onResume is not navigation.
                if (classicExternalAppLaunched || homeWasPending || pendingClassicHomeReturn) {
                    if (homeWasPending) pendingClassicHomeReturn = true
                    scheduleClassicPhoneResume()
                } else {
                    entranceReady = true
                    phoneResumeUsesBackMotion = false
                }
            } else {
                entranceReady = true
                val mobileBackReturn = mode == LauncherUiMode.MOBILE_10 &&
                    mobileExternalAppLaunched && !homeWasPending
                mobileExternalAppLaunched = false
                phoneResumeUsesBackMotion = mobileBackReturn
                if (mobileBackReturn || (mode == LauncherUiMode.DESKTOP && !homeWasPending)) {
                    entranceKind = StartEntranceKind.RETURN
                    entranceRequest++
                } else if (homeWasPending) {
                    phoneReturnSourceTileId = null
                }
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
            entranceRecovery.recheck()
            if (pendingStoryReturn != null && !launcherResumed) {
                homeIntentPending = true
                return
            }
            // Android routinely redelivers HOME to the already-visible launcher.
            // A settled phone Start must not reset the animation clock at all.
            if (LauncherFeatureStore.launcherUiMode(this) != LauncherUiMode.DESKTOP &&
                launcherResumed && entranceReady && !phoneAllAppsVisible && activeInAppTile == null &&
                !flipState.isRunning
            ) return
            phoneResumeUsesBackMotion = false
            mobileExternalAppLaunched = false
            phoneReturnSourceTileId = null
            if (flipState.isRunning) {
                requestFlipReverse(FlipReverseReason.HOME)
                return
            }

            pendingLaunchIntent = null
            val returningFromOutside = !launcherResumed
            homeIntentPending = returningFromOutside
            if (LauncherFeatureStore.launcherUiMode(this) == LauncherUiMode.PHONE_8 &&
                (returningFromOutside || !entranceReady)
            ) {
                // Defer the HOME request alongside the 200ms activity-resume
                // event. Otherwise homeRequest itself restarts motionClock
                // before the window is shown (and causes the observed twitch).
                pendingClassicHomeReturn = true
                if (launcherResumed && !startupEntrancePending) {
                    scheduleClassicPhoneResume()
                }
            } else {
                if (returningFromOutside && entranceReady) {
                    entranceKind = StartEntranceKind.RETURN
                    entranceRequest++
                }
                homeRequest++
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Keyguard/focus callbacks are independent of onResume. In particular,
        // unlocking need not deliver another resume or USER_PRESENT notification.
        if (hasFocus) entranceRecovery.recheck()
    }

    override fun onDestroy() {
        entranceRecovery.dispose()
        classicPhoneResumeGeneration++
        if (userPresentReceiverRegistered) {
            unregisterReceiver(userPresentReceiver)
            userPresentReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun beginStartupEntranceGate() {
        classicPhoneResumeGeneration++
        startupEntrancePending = true
        entranceReady = false
        entranceRecovery.recheck()
    }

    private fun finishStartupEntranceGate() {
        entranceRecovery.cancel()
        if (!startupEntrancePending) {
            entranceReady = true
            return
        }
        startupEntrancePending = false
        entranceReady = true
        entranceKind = StartEntranceKind.STARTUP
        phoneResumeUsesBackMotion = false
        entranceRequest++
        if (pendingClassicHomeReturn) {
            pendingClassicHomeReturn = false
            homeRequest++
        }
    }

    private fun isDeviceKeyguardLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun isDeviceInteractive(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive != false

    private fun deviceRequiresEntranceGate(): Boolean =
        isDeviceKeyguardLocked() || !isDeviceInteractive()

    private fun requestFlipReverse(reason: FlipReverseReason) {
        if (!flipState.isRunning) return
        pendingStoryReturn = null
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
        if (!canRequestAppUninstall(this, packageName)) {
            val installed = runCatching {
                packageManager.getApplicationInfo(packageName, 0)
            }.isSuccess
            if (installed && packageName != this.packageName) {
                Toast.makeText(this, "System apps cannot be removed; opening App info", Toast.LENGTH_LONG).show()
                openAppInfo(packageName)
            } else {
                Toast.makeText(this, "This app cannot be uninstalled", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            // ACTION_UNINSTALL_PACKAGE opens the user's platform confirmation.
            // No FLAG_ACTIVITY_NEW_TASK: the ActivityResult contract must receive
            // success/cancel and the launcher must not suppress the system UI.
            uninstallRequest.launch(userConfirmedAppUninstallIntent(packageName))
        } catch (first: Exception) {
            Log.w("MosaicUninstall", "Uninstaller intent failed for $packageName", first)
            // Some vendor package managers register only the legacy DELETE action.
            try {
                uninstallRequest.launch(Intent(Intent.ACTION_DELETE,
                    Uri.fromParts("package", packageName, null)).apply {
                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    })
            } catch (second: Exception) {
                Log.e("MosaicUninstall", "Unable to show uninstall confirmation for $packageName", second)
                Toast.makeText(this, "Uninstaller unavailable. Try App info.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resolveLaunchIntent(tile: TileModel): Intent? {
        return try {
            // A user/default binding always wins. Stock tiles keep their semantic action while
            // targeting that exact activity (camera, alarms, browser, files, etc.).
            boundTileLaunchIntent(this, tile)?.let { return it }

            if (isStockDefaultTileId(tile.id)) {
                defaultTileSemanticIntent(tile.id)
                    ?.takeIf { it.resolveActivity(packageManager) != null }
                    ?.let { return it }
            }

            val fallback = when (tile.tileType) {
                TileType.READING_LIST, TileType.MONEY, TileType.DESKTOP, TileType.SETTINGS -> null
                TileType.INTERNET_EXPLORER ->
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/"))
                TileType.STORE ->
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=apps"))
                else -> tile.packageName?.let { packageManager.getLaunchIntentForPackage(it) }
            }
            fallback?.takeIf { it.resolveActivity(packageManager) != null }
        } catch (_: Exception) {
            null
        }
    }

    private fun handleTileLaunch(tile: TileModel) {
        val preparedIntent = pendingLaunchIntent
        pendingLaunchIntent = null
        if (preparedIntent == null) {
            activeInAppTile = tile
            if (!flipState.masterLaunchAnimation.isStory) flipState = FlipAnimationState()
            return
        }
        try {
            launchHandoffPending = true
            // Borrowed Desktop motion never comes back from an external app:
            // onResume runs the native WP8.1/W10M entrance instead. A quick Back
            // DURING the unfinished forward flip still reverses in-place.
            pendingStoryReturn = flipState.takeIf { it.masterLaunchAnimation.isStory }
            when (LauncherFeatureStore.launcherUiMode(this)) {
                LauncherUiMode.PHONE_8 -> {
                    classicExternalAppLaunched = true
                    phoneReturnSourceTileId = tile.id
                }
                LauncherUiMode.MOBILE_10 -> {
                    mobileExternalAppLaunched = true
                    phoneReturnSourceTileId = tile.id
                }
                else -> Unit
            }
            startActivityWithCustomAnim(preparedIntent)
        } catch (_: Exception) {
            pendingStoryReturn = null
            classicExternalAppLaunched = false
            mobileExternalAppLaunched = false
            phoneReturnSourceTileId = null
            launchHandoffPending = false
            activeInAppTile = tile
            if (!flipState.masterLaunchAnimation.isStory) flipState = FlipAnimationState()
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
    onPhonePageChanged: (Boolean) -> Unit = {},
    homeRequest: Int = 0,
    entranceRequest: Int = 0,
    entranceKind: StartEntranceKind = StartEntranceKind.RETURN,
    resumeUsesBackMotion: Boolean = false,
    screenOffRequest: Int = 0,
    returningPhoneTileId: String? = null,
    entranceReady: Boolean = true,
) {
    var currentScreen by rememberSaveable { mutableStateOf(LauncherScreen.START) }
    val drawerProgress = remember { mutableFloatStateOf(0f) }
    val appsFullyVisible by remember { derivedStateOf { drawerProgress.floatValue >= 0.999f } }
    val initialStartScrollIndex = remember { appsRepository.getStartScrollIndex() }
    val initialStartScrollOffset = remember { appsRepository.getStartScrollOffsetPx() }
    val startScroll = rememberLazyListState(
        initialFirstVisibleItemIndex = initialStartScrollIndex,
        initialFirstVisibleItemScrollOffset = initialStartScrollOffset,
    )
    val appsScroll = rememberLazyListState()
    // Start's bands have different physical widths because group gutters are real layout items.
    // Keep the wallpaper on a continuous scroll track reported by StartScreen instead of deriving
    // it from firstVisibleItemIndex * currentItemWidth (which jumps when the first item changes).
    var startWallpaperScrollPx by rememberSaveable {
        mutableStateOf(appsRepository.getStartWallpaperScrollPx())
    }
    var wallpaperParallaxEnabled by remember {
        mutableStateOf(appsRepository.getWallpaperParallaxEnabled())
    }
    var wallpaperStyle by remember { mutableIntStateOf(appsRepository.getWallpaperStyle()) }
    val tiles = remember { mutableStateListOf<TileModel>().apply {
        addAll(appsRepository.cachedPinnedTiles.orEmpty())
    } }
    var tilesLoaded by remember { mutableStateOf(appsRepository.cachedPinnedTiles != null) }
    var selectedTileForCustomization by remember { mutableStateOf<TileModel?>(null) }
    var showPowerDialog by remember { mutableStateOf(false) }
    var showPinAppsDialog by remember { mutableStateOf(false) }
    var flipLaunchDispatched by remember(flipState.sourceTile?.id, flipState.sourceBounds) {
        mutableStateOf(false)
    }
    val flipProgress = remember(flipState.sourceTile?.id, flipState.sourceBounds) {
        Animatable(if (flipState.direction == FlipAnimationDirection.REVERSE &&
            (flipState.masterLaunchAnimation.isStory ||
                flipState.masterLaunchAnimation == MasterLaunchAnimation.DESKTOP)) 1f else 0f)
    }
    val storyRunning = flipState.isRunning && flipState.masterLaunchAnimation.isStory
    val storyScene = remember(flipState.sourceTile?.id, flipState.sourceBounds,
        flipState.masterLaunchAnimation, storyRunning) {
        if (storyRunning) StoryLaunchScene(flipState) { flipProgress.value } else null
    }
    CompositionLocalProvider(LocalStoryLaunchScene provides storyScene) {
    var localStartEntranceRequest by remember { mutableIntStateOf(0) }
    var startEntranceKind by remember(entranceRequest, homeRequest) { mutableStateOf(entranceKind) }
    var localPhoneResumeUsesBackMotion by remember(entranceRequest, homeRequest) {
        mutableStateOf(resumeUsesBackMotion)
    }
    var showCharms by remember { mutableStateOf(false) }
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    var typedSearchRequest by remember { mutableStateOf("") }
    var drawerResetRequest by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDefaultTilePick by remember { mutableStateOf<PendingDefaultTilePick?>(null) }

    val defaultTilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pending = pendingDefaultTilePick
        pendingDefaultTilePick = null
        if (pending == null || result.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }

        val selectedIntent = result.data
        val component = selectedIntent?.component
            ?: selectedIntent?.resolveActivity(context.packageManager)
        if (component == null || component.packageName == context.packageName) {
            Toast.makeText(context, "No app was selected for this tile", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        val index = tiles.indexOfFirst { it.id == pending.tileId }
        if (index < 0) return@rememberLauncherForActivityResult

        val updated = tiles[index].copy(
            packageName = component.packageName,
            activityName = component.className,
        )
        tiles[index] = updated
        appsRepository.savePinnedTiles(tiles.toList())
        if (entranceReady) {
            onTriggerFlip(updated, pending.bounds, LaunchOrigin.START)
        }
    }

    fun bindOrPickDefaultTile(tile: TileModel, bounds: Rect): Boolean {
        if (!isStockDefaultTileId(tile.id)) return false
        if (boundTileLaunchIntent(context, tile) != null) return false

        val automatic = resolveDefaultTileApp(context, tile.id)
        if (automatic != null) {
            val index = tiles.indexOfFirst { it.id == tile.id }
            if (index >= 0) {
                val updated = tile.copy(
                    packageName = automatic.packageName,
                    activityName = automatic.activityName,
                )
                tiles[index] = updated
                appsRepository.savePinnedTiles(tiles.toList())
                onTriggerFlip(updated, bounds, LaunchOrigin.START)
                return true
            }
        }

        pendingDefaultTilePick = PendingDefaultTilePick(tile.id, bounds)
        val pickIntent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
            putExtra(Intent.EXTRA_INTENT, defaultTilePickerBaseIntent(context, tile.id))
            putExtra(Intent.EXTRA_TITLE, "Choose app for ${tile.title}")
        }
        defaultTilePicker.launch(pickIntent)
        return true
    }
    val activeCustomWallpaperUri = StartPersonalization.customWallpaperUri
    val manualStartAccentArgb = StartPersonalization.accentArgb

    LaunchedEffect(wallpaperStyle, activeCustomWallpaperUri, manualStartAccentArgb) {
        val fallback = StartPersonalization.accentColor
        val derived = withContext(Dispatchers.IO) {
            StartPersonalization.calculateWallpaperAccent(
                context = context.applicationContext,
                wallpaperStyle = wallpaperStyle,
                customUri = activeCustomWallpaperUri,
                fallbackAccent = fallback,
            )
        }
        StartPersonalization.setWallpaperDerivedAccent(context, derived)
    }

    fun pinAppWithAccent(app: AppInfo) {
        val cachedAccent = appsRepository.getCachedAppAccentColor(app.packageName)
        val tileId = "app_${app.packageName}_${System.currentTimeMillis()}"
        val newTile = TileModel(
            id = tileId,
            title = app.label,
            packageName = app.packageName,
            activityName = app.activityName,
            colorValue = cachedAccent ?: WindowsColors.Purple,
            usesAppAccent = true,
            size = TileSize.MEDIUM,
            order = tiles.size,
        )
        tiles.add(newTile)
        appsRepository.savePinnedTiles(tiles.toList())

        if (cachedAccent == null) {
            scope.launch {
                val accent = withContext(Dispatchers.IO) {
                    appsRepository.loadAppAccentColor(app.packageName)
                }
                val index = tiles.indexOfFirst { it.id == tileId }
                if (index >= 0 && tiles[index].colorValue != accent) {
                    tiles[index] = tiles[index].copy(colorValue = accent)
                    appsRepository.savePinnedTiles(tiles.toList())
                }
            }
        }
    }

    val startEntranceRequest = entranceRequest + localStartEntranceRequest

    // Save only after the parallax position has been quiet for a moment. A cold launcher start
    // can then paint the previous Start position on frame one instead of showing x=0 and snapping
    // to the restored LazyRow coordinate after layout arrives.
    LaunchedEffect(startWallpaperScrollPx, startScroll.firstVisibleItemIndex, startScroll.firstVisibleItemScrollOffset) {
        delay(350L)
        val index = startScroll.firstVisibleItemIndex
        val offset = startScroll.firstVisibleItemScrollOffset
        val wallpaper = startWallpaperScrollPx
        withContext(Dispatchers.IO) {
            appsRepository.setStartViewState(
                firstVisibleItemIndex = index,
                firstVisibleItemScrollOffset = offset,
                wallpaperScrollPx = wallpaper,
            )
        }
    }

    // A temporary entrance gate (including a recreated Activity after app launch) must not
    // discard the saved page. Only an actual screen-off event explicitly resets navigation.
    LaunchedEffect(entranceReady) {
        if (!entranceReady) {
            showCharms = false
            selectedTileForCustomization = null
            showPinAppsDialog = false
            showPowerDialog = false
        }
    }
    LaunchedEffect(screenOffRequest) {
        if (screenOffRequest > 0) {
            currentScreen = LauncherScreen.START
            drawerResetRequest++
        }
    }

    fun navigateToAllApps() {
        if (entranceReady) currentScreen = LauncherScreen.ALL_APPS
    }

    fun searchApps(initialQuery: String = "") {
        typedSearchRequest = initialQuery
        if (!entranceReady) return
        showCharms = false
        if (activeInAppTile != null) onCloseInAppTile()
        navigateToAllApps()
        searchFocusRequest++
    }

    fun openLauncherSettings() {
        if (!entranceReady || flipState.isRunning) return
        showCharms = false
        val settingsTile = tiles.firstOrNull { it.tileType == TileType.SETTINGS }
            ?: TileModel(id = "tile_settings", title = "PC settings",
                colorValue = WindowsColors.SettingsPurple, tileType = TileType.SETTINGS,
                iconGlyph = "settings")
        // Keep non-tile phone shortcuts on the native exit timeline too.
        // Desktop's existing direct Settings opening is unchanged.
        // Crash-loop bookkeeping must not synchronously fsync SharedPreferences
        // on the tap frame of a physics-heavy Desktop flip.
        scope.launch(Dispatchers.IO) {
            LauncherFeatureStore.beginPhoneSettings(context.applicationContext)
        }
        if (LauncherFeatureStore.launcherUiMode(context) == LauncherUiMode.DESKTOP &&
            LauncherFeatureStore.masterLaunchAnimation(context) == MasterLaunchAnimation.DEFAULT
        ) {
            onOpenInAppTile(settingsTile)
        } else {
            // Internal launcher apps honor an explicitly selected master preset too.
            onTriggerFlip(settingsTile, Rect.Zero, LaunchOrigin.START)
        }
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
        localPhoneResumeUsesBackMotion = false
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
        if (storyRunning) {
            onRequestFlipReverse(FlipReverseReason.BACK)
            return
        }
        val wasOpen = activeInAppTile != null
        onCloseInAppTile()
        if (wasOpen && entranceReady) {
            startEntranceKind = StartEntranceKind.RETURN
            localPhoneResumeUsesBackMotion = true
            localStartEntranceRequest++
        }
    }

    LaunchedEffect(homeRequest) {
        if (homeRequest > 0) returnToStart()
    }
    LaunchedEffect(flipState.isRunning) {
        if (flipState.isRunning) showCharms = false
    }

    // Activity recreation reuses process memory. A cold process restores the small saved catalog
    // before validating packages or decoding icons; theme/settings changes never hide a ready HOME.
    val launcherModeRevision = LauncherFeatureRuntime.launcherModeRevision
    val launcherMode = remember(launcherModeRevision) { LauncherFeatureStore.launcherUiMode(context) }
    val iconsRevision = LauncherFeatureRuntime.iconsRevision
    val packageRevision = PackageCatalogUpdates.revision
    var categorizedApps by remember { mutableStateOf(appsRepository.peekCategorizedApps().orEmpty()) }
    var catalogLoaded by remember { mutableStateOf(appsRepository.peekCategorizedApps() != null) }
    val launcherPrepared = tilesLoaded && catalogLoaded
    var showInitialLoading by remember { mutableStateOf(false) }
    LaunchedEffect(launcherPrepared) {
        if (!launcherPrepared) {
            // This only delays the indicator, never the content. Fast disk restores avoid a flash.
            delay(180)
            showInitialLoading = true
            Log.i("MosaicStartup", "Loading indicator shown")
        } else showInitialLoading = false
    }

    val externalPinnedRevision = LauncherFeatureRuntime.pinnedTilesRevision
    LaunchedEffect(externalPinnedRevision) {
        val startedAt = SystemClock.elapsedRealtime()
        val prepared = prepareLauncher(context.applicationContext, appsRepository,
            packageRevision)
        categorizedApps = prepared.sections
        if (tiles.toList() != prepared.tiles) {
            tiles.clear()
            tiles.addAll(prepared.tiles)
        }
        tilesLoaded = true
        catalogLoaded = true
        Log.i("MosaicStartup", "Ready $launcherMode, ${prepared.sections.sumOf { it.apps.size }} apps, " +
            "${SystemClock.elapsedRealtime() - startedAt}ms")
    }

    LaunchedEffect(catalogLoaded, packageRevision) {
        if (catalogLoaded) {
            withFrameNanos { }
            withFrameNanos { }
            // Reconcile package changes without replacing the interactive surface with a splash.
            categorizedApps = withContext(Dispatchers.IO) {
                appsRepository.refreshCatalogIfNeeded(packageRevision)
            }
        }
    }

    val pinnedTiles = tiles.toList()
    // A tile's location, label or color is not a reason to warm every icon again.
    // Stable package membership preserves the existing behavior for new pins/apps.
    val artworkPackages = remember(pinnedTiles) {
        pinnedTiles.mapNotNull(TileModel::packageName).distinct().sorted()
    }
    var sharedPinsRevision by remember { mutableIntStateOf(0) }
    val phonePinnedTiles = remember(pinnedTiles, categorizedApps, externalPinnedRevision, sharedPinsRevision) {
        SharedStartWorkspace.phoneTiles(context, pinnedTiles, categorizedApps.flatMap(AppSection::apps))
    }
    fun unpinPackage(packageName: String) {
        val updated = SharedStartWorkspace.tilesWithoutPackage(context, tiles.toList(),
            categorizedApps.flatMap(AppSection::apps), packageName)
        appsRepository.savePinnedTiles(updated)
        tiles.clear()
        tiles.addAll(updated)
        sharedPinsRevision++
    }
    LaunchedEffect(launcherPrepared, launcherMode, iconsRevision, categorizedApps, artworkPackages) {
        if (launcherPrepared) {
            withFrameNanos { }
            withFrameNanos { }
            warmLauncherArtwork(context.applicationContext, appsRepository, launcherMode,
                iconsRevision, PreparedLauncher(categorizedApps, pinnedTiles, null))
            val refreshed = withContext(Dispatchers.IO) {
                runCatching { appsRepository.refreshAutomaticAppAccentTiles(pinnedTiles, persist = false) }
                    .onFailure { Log.w("MosaicStartup", "Unable to refresh app accents", it) }
                    .getOrDefault(pinnedTiles)
            }
            // A pin/edit may have happened during decoding. Only update untouched tiles.
            val before = pinnedTiles.associateBy(TileModel::id)
            val after = refreshed.associateBy(TileModel::id)
            val updated = tiles.map { tile ->
                if (before[tile.id] == tile) after[tile.id] ?: tile else tile
            }
            if (updated != tiles.toList()) {
                tiles.clear()
                tiles.addAll(updated)
                appsRepository.savePinnedTiles(updated)
            }
        }
    }

    LaunchedEffect(launcherPrepared, entranceReady) {
        if (launcherPrepared && entranceReady) {
            // Wait for the prepared surface to be composed and submitted, not just the splash.
            withFrameNanos { }
            withFrameNanos { }
            (context as? Activity)?.reportFullyDrawn()
        }
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

    // Desktop and phone modes retain their independent motion implementations.
    // The phone skins are Android launchers, not standalone operating systems:
    // keep the user's real Android gesture/three-button navigation visible.
    // The classic mock navigation row was removed from PhoneLauncherSurface.
    LaunchedEffect(launcherMode) {
        (context as? Activity)?.window?.let { hostWindow ->
            WindowCompat.getInsetsController(hostWindow, hostWindow.decorView)
                .show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        }
    }
    val phoneSurfaceStates = rememberSaveableStateHolder()
    // Do not consider a phone mode healthy merely because its preference was written.
    // Confirm it after an actual visible, composed phone Start/All Apps frame.
    LaunchedEffect(launcherMode, launcherPrepared, entranceReady, activeInAppTile) {
        if (launcherMode != LauncherUiMode.DESKTOP && launcherPrepared && entranceReady) {
            withFrameNanos { }
            delay(600)
            if (activeInAppTile == null) {
                LauncherFeatureStore.confirmPhoneRender(context)
            } else if (activeInAppTile.tileType == TileType.SETTINGS) {
                LauncherFeatureStore.confirmPhoneSettings(context)
            }
        }
    }
    // Root Back keeps HOME alive in every mode, retaining its prepared catalog and artwork.
    // More specific page, editing and dialog BackHandlers retain priority.
    BackHandler(
        enabled = entranceReady && activeInAppTile == null && !flipState.isRunning &&
            currentScreen == LauncherScreen.START && !showCharms && !showPowerDialog && !showPinAppsDialog,
    ) {
        // Already at Start: intentionally no navigation.
    }
    // Do not mount either pane behind the splash: phone/Desktop entrance animations
    // must start on the first real launcher frame, not elapse invisibly while loading.
    // An already-open Settings panel stays usable during a mode/style change.
    if (!launcherPrepared && activeInAppTile == null) {
        if (showInitialLoading) LauncherStartupSplash(message = "Loading Start")
        else Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
        return@CompositionLocalProvider
    }
    if (launcherMode != LauncherUiMode.DESKTOP) {
        // Keep the completed exit drawn while Android asynchronously starts the target app.
        // Without this latch, the 61fps recording shows Start tiles reappearing at 7.91–8.04s.
        var phoneHandoffHeld by remember(launcherMode, startEntranceRequest, homeRequest) {
            mutableStateOf(false)
        }
        var handoffTileId by remember(launcherMode, startEntranceRequest, homeRequest) {
            mutableStateOf<String?>(null)
        }
        var handoffAnimationMode by remember(launcherMode, startEntranceRequest, homeRequest) {
            mutableStateOf<LauncherUiMode?>(null)
        }
        // Default keeps each phone mode's original measured Start/All Apps exit.
        // Cross-phone choices run on the original native launch compositor.
        val chosenLaunch = flipState.masterLaunchAnimation
        // Both phone modes share the original native motion engine. Switching
        // between PHONE_8/MOBILE_10 selects the source motion curves, not a new
        // approximate overlay. Desktop's original flip uses the desktop compositor.
        val usesDesktopLaunch = chosenLaunch == MasterLaunchAnimation.DESKTOP
        val usesOverlayLaunch = usesDesktopLaunch || chosenLaunch.isStory
        val desktopOverlayTileId = flipState.sourceTile?.id.takeIf {
            usesDesktopLaunch && flipState.isRunning
        }
        val phoneLaunchActive = flipState.isRunning && !usesOverlayLaunch &&
            flipState.direction == FlipAnimationDirection.FORWARD
        val phoneExitActive = phoneLaunchActive || phoneHandoffHeld
        LaunchedEffect(flipState.isRunning, flipState.direction, launcherMode) {
            if (flipState.isRunning && !usesOverlayLaunch &&
                flipState.direction == FlipAnimationDirection.REVERSE) {
                phoneHandoffHeld = false
                handoffTileId = null
                handoffAnimationMode = null
                onDismissFlip()
                localStartEntranceRequest++
            }
        }
        Box(Modifier.fillMaxSize()) {
            // Keep the panorama mounted at its native size. Only the source page (Start
            // OR All Apps) retreats during a borrowed Desktop flip; transforming the
            // parent made W10M's sibling All Apps page take part in a Start launch.
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = if (entranceReady) 1f else 0f
            }) {
                // PC Settings loads wallpaper previews and animation tooling. Keeping the
                // entire phone panorama + lazy app grid alive beneath it can exhaust graphics
                // memory on physical devices. Save navigation state and unmount it while open.
                if (tilesLoaded && (activeInAppTile == null ||
                        (storyRunning && flipState.direction == FlipAnimationDirection.REVERSE))) {
                    phoneSurfaceStates.SaveableStateProvider(launcherMode.name) {
                        PhoneLauncherSurface(
                        mode = launcherMode,
                        tiles = phonePinnedTiles,
                        sections = categorizedApps,
                        appsRepository = appsRepository,
                        preparedRecentApps = null,
                        homeRequest = homeRequest,
                        entranceRequest = startEntranceRequest,
                        launchingTileId = if (phoneLaunchActive)
                            flipState.sourceTile?.id else handoffTileId,
                        hideOriginalTileId = desktopOverlayTileId,
                        borrowedDesktopOrigin = if (usesDesktopLaunch && flipState.isRunning)
                            flipState.origin else null,
                        borrowedDesktopRetreat = {
                            if (usesDesktopLaunch && flipState.isRunning) {
                                val elapsed = flipProgress.value *
                                    flipState.timing.durationMillis.coerceIn(100, 2000)
                                FastOutSlowInEasing.transform((elapsed / 140f).coerceIn(0f, 1f))
                            } else 0f
                        },
                        launchTiming = flipState.timing,
                        launchMotionMode = if (phoneHandoffHeld) handoffAnimationMode
                            else when (chosenLaunch) {
                                MasterLaunchAnimation.PHONE_8 -> LauncherUiMode.PHONE_8
                                MasterLaunchAnimation.MOBILE_10 -> LauncherUiMode.MOBILE_10
                                else -> null
                            },
                        isLaunching = phoneExitActive,
                        interactionEnabled = activeInAppTile == null && entranceReady && !flipState.isRunning,
                        wallpaperStyle = wallpaperStyle,
                        resumeUsesBackMotion = localPhoneResumeUsesBackMotion,
                        screenOffRequest = screenOffRequest,
                        returningTileId = returningPhoneTileId,
                        onLaunch = { tile, bounds, origin ->
                            // Stock Settings has an Android semantic fallback, but
                            // its phone tile represents our own internal app.
                            val nativeSettings = tile.tileType == TileType.SETTINGS
                            if (entranceReady && !flipState.isRunning &&
                                (nativeSettings || !bindOrPickDefaultTile(tile, bounds))
                            ) {
                                if (nativeSettings) {
                                    // Preserve phone Settings crash-loop recovery.
                                    // Preserve crash-loop detection without blocking the first
                                    // animation frame on a synchronous preferences commit.
                                    scope.launch(Dispatchers.IO) {
                                        LauncherFeatureStore.beginPhoneSettings(context.applicationContext)
                                    }
                                }
                                onTriggerFlip(tile, bounds, origin)
                            }
                        },
                        onExitFinished = {
                            // The compositor reports completion after submitting its final frame.
                            // Preserve the exit pose and selected ID through Android's window swap.
                            if (phoneLaunchActive && !flipLaunchDispatched) {
                                flipState.sourceTile?.let { tile ->
                                    handoffTileId = tile.id
                                    handoffAnimationMode = when (chosenLaunch) {
                                        MasterLaunchAnimation.PHONE_8 -> LauncherUiMode.PHONE_8
                                        MasterLaunchAnimation.MOBILE_10 -> LauncherUiMode.MOBILE_10
                                        else -> launcherMode
                                    }
                                    phoneHandoffHeld = true
                                    flipLaunchDispatched = true
                                    onLaunchTile(tile)
                                }
                                onDismissFlip()
                            }
                        },
                        onOpenSettings = { openLauncherSettings() },
                        onOpenAppInfo = { onOpenAppInfo(it) },
                        onUninstallApp = { onUninstallApp(it) },
                        onVisiblePageChanged = onPhonePageChanged,
                        onSharedPinsChanged = { order ->
                            val updated = SharedStartWorkspace.tilesForPhoneOrder(context,
                                launcherMode, order, tiles.toList(), categorizedApps.flatMap(AppSection::apps))
                            appsRepository.savePinnedTiles(updated)
                            tiles.clear()
                            tiles.addAll(updated)
                            sharedPinsRevision++
                        },
                        )
                    }
                }
            }
            if (entranceReady && activeInAppTile != null && !storyRunning) {
                WindowsAppView(
                    tile = activeInAppTile,
                    appsRepository = appsRepository,
                    onClose = { closeInAppTileAndRetriggerEntrance() },
                    onWallpaperParallaxChanged = { wallpaperParallaxEnabled = it },
                    onWallpaperStyleChanged = { wallpaperStyle = it },
                )
            }
            if (entranceReady && flipState.isRunning && usesOverlayLaunch) {
                FlipLaunchOverlay(
                    state = if (usesDesktopLaunch) flipState.copy(
                        // Use the genuine Desktop compositor rather than an
                        // extra preset or the repository's optional MODERN look.
                        masterLaunchAnimation = MasterLaunchAnimation.DEFAULT,
                        animationMode = com.flivoro.tile8auncher.data.FlipAnimationMode.CLASSIC,
                    ) else flipState,
                    progress = flipProgress,
                    onAnimationEnd = {
                        if (flipState.isRunning &&
                            flipState.direction == FlipAnimationDirection.FORWARD &&
                            !flipLaunchDispatched
                        ) {
                            flipLaunchDispatched = true
                            flipState.sourceTile?.let(onLaunchTile)
                        }
                    },
                    onReverseAnimationEnd = { reason ->
                        if (storyRunning && activeInAppTile != null) onCloseInAppTile()
                        onDismissFlip()
                        if (reason == FlipReverseReason.HOME) returnToStart()
                    },
                    destinationContent = if (flipState.hasInternalWindow) {
                        {
                            flipState.sourceTile?.let { tile ->
                                // Compose expensive settings controls only AFTER the
                                // moving Desktop destination stops. Rendering every
                                // wallpaper preview and settings group inside a 3D
                                // matrix was dropping frames on physical phones.
                                if (usesDesktopLaunch && tile.tileType == TileType.SETTINGS) {
                                    LightweightSettingsLaunchPreview(tile)
                                } else {
                                    WindowsAppView(
                                        tile = tile,
                                        appsRepository = appsRepository,
                                        onClose = { closeInAppTileAndRetriggerEntrance() },
                                        onWallpaperStyleChanged = { wallpaperStyle = it },
                                        onWallpaperParallaxChanged = { wallpaperParallaxEnabled = it },
                                    )
                                }
                            }
                        }
                    } else null,
                    sourceFace = { faceModifier ->
                        flipState.sourceTile?.let { phoneTile ->
                            PhoneLaunchSourceFace(
                                tile = phoneTile,
                                mode = launcherMode,
                                repository = appsRepository,
                                modifier = faceModifier,
                                sourceBounds = flipState.sourceBounds,
                                sourceInitialOpacity = flipState.sourceInitialOpacity,
                                sourcePictureMode = flipState.sourcePictureMode,
                                wallpaperStyle = flipState.sourceWallpaperStyle,
                                launchProgress = { flipProgress.value },
                                sourceOrigin = flipState.origin,
                                preferredIcon = flipState.appIcon,
                            )
                        }
                    },
                    sourceForeground = { faceModifier ->
                        flipState.sourceTile?.let { phoneTile ->
                            PhoneLaunchSourceFace(
                                tile = phoneTile,
                                mode = launcherMode,
                                repository = appsRepository,
                                modifier = faceModifier,
                                sourceOrigin = flipState.origin,
                                preferredIcon = flipState.appIcon,
                                foregroundOnly = true,
                                detailsOnly = true,
                            )
                        }
                    },
                    sourceBackground = { backdropModifier ->
                        flipState.sourceTile?.let { phoneTile ->
                            PhoneLaunchSourceFace(
                                tile = phoneTile,
                                mode = launcherMode,
                                repository = appsRepository,
                                modifier = backdropModifier,
                                sourceBounds = flipState.sourceBounds,
                                sourcePictureMode = flipState.sourcePictureMode,
                                wallpaperStyle = flipState.sourceWallpaperStyle,
                                sourceOrigin = flipState.origin,
                                backgroundOnly = true,
                            )
                        }
                    },
                    destinationIcon = { iconModifier ->
                        flipState.sourceTile?.let { phoneTile ->
                            PhoneLaunchDestinationIcon(
                                tile = phoneTile,
                                mode = launcherMode,
                                repository = appsRepository,
                                modifier = iconModifier,
                                sourceOrigin = flipState.origin,
                                preferredIcon = flipState.appIcon,
                                // Story launches use a source-sized glyph on
                                // their own expanding panel. Native Desktop
                                // launch faces retain the old 78dp artwork.
                                iconSize = if (storyRunning) {
                                    // Keep the icon's exact intended tile-size
                                    // footprint through the entire morph. The
                                    // story plane handles its 1.0→1.44x scale;
                                    // it must not swap sizes at the midpoint.
                                    if (flipState.origin == LaunchOrigin.ALL_APPS) 28.dp
                                    else when (phoneTile.size) {
                                        com.flivoro.tile8auncher.data.TileSize.SMALL -> 28.dp
                                        com.flivoro.tile8auncher.data.TileSize.MEDIUM -> 44.dp
                                        com.flivoro.tile8auncher.data.TileSize.WIDE -> 48.dp
                                        com.flivoro.tile8auncher.data.TileSize.LARGE -> 64.dp
                                    }
                                } else 78.dp,
                            )
                        }
                    },
                )
            }
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
        return@CompositionLocalProvider
    }

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
        // The generated purple ribbons are valid tile artwork too, not only JPEGs.
        val desktopTilePictureMode = LauncherFeatureStore.backgroundStyle(
            context, LauncherUiMode.DESKTOP) == PhoneBackgroundStyle.TILE_PICTURE
        if (!desktopTilePictureMode) WindowsWallpaper(
            wallpaperStyle = wallpaperStyle,
            enabled = wallpaperParallaxEnabled,
            scrollOffsetPx = {
                fun appsOffset(state: androidx.compose.foundation.lazy.LazyListState): Float {
                    val info = state.layoutInfo
                    val itemWidth = info.visibleItemsInfo.firstOrNull()?.size ?: 0
                    return (
                        state.firstVisibleItemIndex.toFloat() * (itemWidth + info.mainAxisItemSpacing) +
                            state.firstVisibleItemScrollOffset
                        ).coerceAtLeast(0f)
                }
                val p = drawerProgress.floatValue
                startWallpaperScrollPx * (1f - p) + appsOffset(appsScroll) * p
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
                    val borrowingNativePhone = flipState.isRunning &&
                        (flipState.masterLaunchAnimation == MasterLaunchAnimation.PHONE_8 ||
                            flipState.masterLaunchAnimation == MasterLaunchAnimation.MOBILE_10)
                    val retreat = if (borrowingNativePhone || storyRunning) 0f
                    else if (flipState.isRunning) {
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
                    if (!tilesLoaded) {
                        // Do not measure an empty LazyRow before the persisted Start index/offset
                        // can be applied. The wallpaper is already painted at its persisted world
                        // coordinate, so the first visible Start frame arrives fully settled.
                        Box(Modifier.fillMaxSize())
                    } else {
                    StartScreen(
                        listState = startScroll,
                        tiles = tiles,
                        launchingTileId = flipState.sourceTile?.id.takeIf {
                            flipState.isRunning &&
                                !flipState.masterLaunchAnimation.isStory &&
                                flipState.masterLaunchAnimation != MasterLaunchAnimation.PHONE_8 &&
                                flipState.masterLaunchAnimation != MasterLaunchAnimation.MOBILE_10
                        },
                        borrowedLaunchStyle = flipState.masterLaunchAnimation.takeIf {
                            flipState.isRunning &&
                                (it == MasterLaunchAnimation.PHONE_8 ||
                                    it == MasterLaunchAnimation.MOBILE_10)
                        },
                        borrowedLaunchSelectedId = flipState.sourceTile?.id,
                        borrowedLaunchProgress = { flipProgress.value },
                        entranceRequest = startEntranceRequest,
                        entranceKind = startEntranceKind,
                        entranceEnabled = entranceReady && currentScreen == LauncherScreen.START &&
                            !flipState.isRunning && activeInAppTile == null,
                        prehideForEntrance = !entranceReady,
                        interactionEnabled = entranceReady && !showCharms && !flipState.isRunning && activeInAppTile == null,
                        appsRepository = appsRepository,
                        onTileClick = { tile, bounds ->
                            if (entranceReady && !bindOrPickDefaultTile(tile, bounds)) {
                                onTriggerFlip(tile, bounds, LaunchOrigin.START)
                            }
                        },
                        onTileLongClick = { tile ->
                            if (entranceReady) selectedTileForCustomization = tile
                        },
                        onUninstallApp = { packageName ->
                            if (entranceReady) onUninstallApp(packageName)
                        },
                        onTilesChanged = { updatedTiles ->
                            if (entranceReady) {
                                tiles.clear()
                                tiles.addAll(updatedTiles)
                                appsRepository.savePinnedTiles(updatedTiles)
                            }
                        },
                        initialWallpaperScrollPx = startWallpaperScrollPx,
                        onWallpaperScrollOffsetChanged = { startWallpaperScrollPx = it },
                        tilePictureMode = desktopTilePictureMode,
                        tileWallpaperStyle = wallpaperStyle,
                        onPowerClick = { if (entranceReady) showPowerDialog = true },
                        onSearchClick = { searchApps() },
                        onTypeToSearch = { searchApps(it) },
                        onCharmsClick = { if (entranceReady) showCharms = true },
                        onAddAppsClick = { if (entranceReady) showPinAppsDialog = true },
                        onNavigateToAllApps = { navigateToAllApps() },
                    )
                    }
                },
                allAppsContent = {
                    AllAppsScreen(
                        listState = appsScroll,
                        searchFocusRequest = searchFocusRequest,
                        initialSearchQuery = typedSearchRequest,
                        onCharmsClick = { if (entranceReady) showCharms = true },
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
                                    colorValue = if (LauncherFeatureStore.useAppAccentColorsInAllApps(context)) {
                                        appsRepository.getCachedAppAccentColor(app.packageName)
                                            ?: StartPersonalization.wallpaperAccentArgb
                                    } else {
                                        StartPersonalization.wallpaperAccentArgb
                                    },
                                    size = TileSize.MEDIUM,
                                )
                                onTriggerFlip(tile, bounds, LaunchOrigin.ALL_APPS)
                            }
                        },
                        isAppPinned = { pkg -> phonePinnedTiles.any { it.packageName == pkg } },
                        onPinApp = { app ->
                            if (entranceReady) {
                                pinAppWithAccent(app)
                            }
                        },
                        onUnpinApp = { pkg ->
                            if (entranceReady) unpinPackage(pkg)
                        },
                        onOpenAppInfo = { pkg -> if (entranceReady) onOpenAppInfo(pkg) },
                        onUninstallApp = { pkg -> if (entranceReady) onUninstallApp(pkg) },
                        onNavigateToStart = { navigateToStart() },
                        borrowedLaunchStyle = flipState.masterLaunchAnimation.takeIf {
                            flipState.isRunning &&
                                (it == MasterLaunchAnimation.PHONE_8 ||
                                    it == MasterLaunchAnimation.MOBILE_10)
                        },
                        borrowedLaunchPackage = flipState.sourceTile?.packageName,
                        borrowedLaunchProgress = { flipProgress.value },
                    )
                },
            )
        }

        if (entranceReady && activeInAppTile != null && !storyRunning) {
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
                    if (storyRunning && activeInAppTile != null) onCloseInAppTile()
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
                                onWallpaperParallaxChanged = { wallpaperParallaxEnabled = it },
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
                            tiles[index] = tile.copy(
                                size = newSize,
                                startBand = null,
                                startColumn = null,
                                startRow = null,
                            )
                            appsRepository.savePinnedTiles(tiles.toList())
                        }
                        selectedTileForCustomization = null
                    },
                    onColorChange = { newColor ->
                        val index = tiles.indexOfFirst { it.id == tile.id }
                        if (index != -1) {
                            tiles[index] = tile.copy(
                                colorValue = newColor,
                                usesAppAccent = false,
                            )
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
                pinnedTiles = phonePinnedTiles,
                appsRepository = appsRepository,
                onDismiss = { showPinAppsDialog = false },
                onTogglePin = { app, isPinned ->
                    if (isPinned) {
                        unpinPackage(app.packageName)
                    } else {
                        pinAppWithAccent(app)
                    }
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
                backgroundContent = {
                    // The revealed area must be the Start wallpaper at Start's real horizontal
                    // position, not another copy of the lock picture and not the Apps-view offset.
                    WindowsWallpaper(
                        wallpaperStyle = wallpaperStyle,
                        enabled = wallpaperParallaxEnabled,
                        scrollOffsetPx = { startWallpaperScrollPx },
                    )
                },
            )
        }
    }
    } // Shared story scene; original compositors see a null provider.
}
