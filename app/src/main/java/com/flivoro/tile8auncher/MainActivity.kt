package com.flivoro.tile8auncher

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.core.view.WindowCompat
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.apps.AllAppsScreen
import com.flivoro.tile8auncher.ui.components.FlipLaunchOverlay
import com.flivoro.tile8auncher.ui.components.WindowsAppView
import com.flivoro.tile8auncher.ui.components.WindowsWallpaper
import com.flivoro.tile8auncher.ui.dialogs.CustomizeTileDialog
import com.flivoro.tile8auncher.ui.dialogs.PowerDialog
import com.flivoro.tile8auncher.ui.start.StartScreen
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.toTileColor

enum class LauncherScreen {
    START,
    ALL_APPS
}

class MainActivity : ComponentActivity() {

    private lateinit var appsRepository: AppsRepository
    private var flipState by mutableStateOf(FlipAnimationState())
    private var activeInAppTile by mutableStateOf<TileModel?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        appsRepository = AppsRepository(this)

        setContent {
            Tile8LauncherApp(
                appsRepository = appsRepository,
                flipState = flipState,
                activeInAppTile = activeInAppTile,
                onCloseInAppTile = {
                    activeInAppTile = null
                    flipState = FlipAnimationState(isRunning = false)
                },
                onTriggerFlip = { tile, bounds ->
                    val icon = tile.packageName?.let { appsRepository.getAppIcon(it) }
                    flipState = FlipAnimationState(
                        isRunning = true,
                        sourceTile = tile,
                        sourceBounds = bounds,
                        appIcon = icon,
                        accentColor = tile.colorValue.toTileColor()
                    )
                },
                onFlipEnd = { tile ->
                    handleTileLaunch(tile)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset flip overlay when returning to launcher from native app
        if (activeInAppTile == null && flipState.isRunning) {
            flipState = FlipAnimationState(isRunning = false)
        }
    }

    private fun handleTileLaunch(tile: TileModel) {
        when (tile.tileType) {
            TileType.READING_LIST,
            TileType.MONEY,
            TileType.DESKTOP -> {
                // Show authentic Windows 8.1 in-app experience
                activeInAppTile = tile
            }

            TileType.INTERNET_EXPLORER -> {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivityWithCustomAnim(browserIntent)
                } catch (e: Exception) {
                    activeInAppTile = tile
                }
            }

            TileType.STORE -> {
                try {
                    val storeIntent = packageManager.getLaunchIntentForPackage("com.android.vending")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms"))
                    storeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivityWithCustomAnim(storeIntent)
                } catch (e: Exception) {
                    activeInAppTile = tile
                }
            }

            else -> {
                if (!tile.packageName.isNullOrEmpty()) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(tile.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivityWithCustomAnim(launchIntent)
                    } else {
                        activeInAppTile = tile
                    }
                } else {
                    activeInAppTile = tile
                }
            }
        }
    }

    private fun startActivityWithCustomAnim(intent: Intent) {
        val options = ActivityOptions.makeCustomAnimation(this, 0, 0)
        startActivity(intent, options.toBundle())
    }
}

@Composable
fun Tile8LauncherApp(
    appsRepository: AppsRepository,
    flipState: FlipAnimationState,
    activeInAppTile: TileModel?,
    onCloseInAppTile: () -> Unit,
    onTriggerFlip: (tile: TileModel, bounds: Rect) -> Unit,
    onFlipEnd: (tile: TileModel) -> Unit
) {
    var currentScreen by remember { mutableStateOf(LauncherScreen.START) }
    val tiles = remember { mutableStateListOf<TileModel>() }
    var selectedTileForCustomization by remember { mutableStateOf<TileModel?>(null) }
    var showPowerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tiles.clear()
        tiles.addAll(appsRepository.loadPinnedTiles())
    }

    val categorizedApps = remember { appsRepository.getCategorizedApps() }

    // Smooth Start screen exit and entrance animation matching video
    val contentAlpha by animateFloatAsState(
        targetValue = if (flipState.isRunning || activeInAppTile != null) 0f else 1f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "StartScreenAlpha"
    )
    val contentScale by animateFloatAsState(
        targetValue = if (flipState.isRunning || activeInAppTile != null) 0.88f else 1f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "StartScreenScale"
    )

    // Back button handling
    BackHandler(enabled = activeInAppTile != null || currentScreen == LauncherScreen.ALL_APPS) {
        if (activeInAppTile != null) {
            onCloseInAppTile()
        } else if (currentScreen == LauncherScreen.ALL_APPS) {
            currentScreen = LauncherScreen.START
        }
    }

    // Root Container with Static Windows 8.1 Purple Wallpaper
    Box(modifier = Modifier.fillMaxSize()) {
        WindowsWallpaper()

        // Start screen & All Apps drawer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha)
                .scale(contentScale)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState == LauncherScreen.ALL_APPS) {
                        slideInVertically(
                            animationSpec = tween(340, easing = FastOutSlowInEasing),
                            initialOffsetY = { it }
                        ) togetherWith slideOutVertically(
                            animationSpec = tween(340, easing = FastOutSlowInEasing),
                            targetOffsetY = { -it / 3 }
                        )
                    } else {
                        slideInVertically(
                            animationSpec = tween(340, easing = FastOutSlowInEasing),
                            initialOffsetY = { -it / 3 }
                        ) togetherWith slideOutVertically(
                            animationSpec = tween(340, easing = FastOutSlowInEasing),
                            targetOffsetY = { it }
                        )
                    }
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    LauncherScreen.START -> {
                        StartScreen(
                            tiles = tiles,
                            appsRepository = appsRepository,
                            onTileClick = { tile, bounds ->
                                onTriggerFlip(tile, bounds)
                            },
                            onTileLongClick = { tile ->
                                selectedTileForCustomization = tile
                            },
                            onPowerClick = { showPowerDialog = true },
                            onSearchClick = { currentScreen = LauncherScreen.ALL_APPS },
                            onNavigateToAllApps = { currentScreen = LauncherScreen.ALL_APPS }
                        )
                    }

                    LauncherScreen.ALL_APPS -> {
                        AllAppsScreen(
                            sections = categorizedApps,
                            appsRepository = appsRepository,
                            onAppClick = { app, bounds ->
                                val tile = TileModel(
                                    id = "app_${app.packageName}",
                                    title = app.label,
                                    packageName = app.packageName,
                                    activityName = app.activityName,
                                    colorValue = WindowsColors.Purple,
                                    size = TileSize.MEDIUM
                                )
                                onTriggerFlip(tile, bounds)
                            },
                            onAppLongClick = { app ->
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
                                appsRepository.savePinnedTiles(tiles.toList())
                                currentScreen = LauncherScreen.START
                            },
                            onNavigateToStart = { currentScreen = LauncherScreen.START }
                        )
                    }
                }
            }
        }

        // Active In-App Screen (Reading List, Money, Desktop, Help+Tips)
        if (activeInAppTile != null) {
            WindowsAppView(
                tile = activeInAppTile,
                onClose = onCloseInAppTile
            )
        }

        // 3D Flip App Opening Animation Overlay
        if (flipState.isRunning && activeInAppTile == null) {
            FlipLaunchOverlay(
                state = flipState,
                onAnimationEnd = {
                    flipState.sourceTile?.let { tile ->
                        onFlipEnd(tile)
                    }
                }
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

        // Windows 8.1 Power Dialog
        if (showPowerDialog) {
            PowerDialog(
                onDismiss = { showPowerDialog = false }
            )
        }
    }
}
