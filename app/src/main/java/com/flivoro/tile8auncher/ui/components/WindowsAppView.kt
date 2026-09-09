package com.flivoro.tile8auncher.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.FlipAnimationMode
import com.flivoro.tile8auncher.data.LaunchTiming
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.animation.AllAppsLaunchMotion
import com.flivoro.tile8auncher.ui.animation.WindowsLaunchMotion
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor

/**
 * Authentic Windows 8.1 App screens matching the video recordings
 * for Reading List (00:19), Help+Tips (01:00), Money (00:37), Desktop (00:10),
 * and PC settings.
 */
@Composable
fun WindowsAppView(
    tile: TileModel,
    appsRepository: AppsRepository? = null,
    onTestFlip: ((TileModel, LaunchOrigin) -> Unit)? = null,
    onClose: () -> Unit,
    onWallpaperParallaxChanged: ((Boolean) -> Unit)? = null,
    onWallpaperStyleChanged: ((Int) -> Unit)? = null,
) {
    val accentColor = tile.colorValue.toTileColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()
            .background(if (tile.tileType == TileType.DESKTOP) Color(0xFF0D254C) else Color(0xFFF0F0F0))) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetroIcon(
                        glyph = tile.iconGlyph.ifEmpty { "app" },
                        color = Color.White,
                        size = 20.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = tile.title,
                        style = WindowsTypography.titleMedium.copy(fontSize = 14.sp),
                        color = Color.White
                    )
                }

                // Close / Back button (Windows 8.1 style)
                Text(
                    text = "✕",
                    style = WindowsTypography.titleMedium.copy(fontSize = 16.sp),
                    color = Color.White,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(8.dp)
                )
            }

            // App Content
            when (tile.tileType) {
                TileType.READING_LIST -> {
                    ReadingListAppContent(onClose = onClose)
                }

                TileType.DESKTOP -> {
                    DesktopAppContent(onClose = onClose)
                }

                TileType.MONEY -> {
                    MoneyAppContent()
                }

                TileType.SETTINGS -> {
                    PCSettingsAppContent(
                        appsRepository = appsRepository,
                        onTestFlip = onTestFlip,
                        onClose = onClose,
                        onWallpaperParallaxChanged = onWallpaperParallaxChanged,
                        onWallpaperStyleChanged = onWallpaperStyleChanged,
                    )
                }

                else -> {
                    GenericMetroAppContent(tile = tile, accentColor = accentColor)
                }
            }
        }
    }
}

@Composable
private fun ReadingListAppContent(onClose: () -> Unit) {
    // Replicating frame 00:19 from video:
    // Header: "Reading List", "Read at your leisure"
    // Two cards: "Share to Reading List" and "Read it later"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text(
            text = "Reading List",
            style = WindowsTypography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA20025)
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Read at your leisure",
            style = WindowsTypography.displayLarge.copy(
                fontSize = 32.sp,
                color = Color(0xFF222222)
            )
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Card 1
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFF7F7F7))
                    .padding(16.dp)
            ) {
                MetroIcon(glyph = "reading_list", color = Color(0xFFA20025), size = 36.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Share to Reading List",
                    style = WindowsTypography.titleMedium.copy(color = Color.Black, fontSize = 15.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Add articles to your list by sharing from your browser.",
                    style = WindowsTypography.bodyMedium.copy(color = Color.Gray, fontSize = 12.sp)
                )
            }

            // Card 2
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFF7F7F7))
                    .padding(16.dp)
            ) {
                MetroIcon(glyph = "mail", color = Color(0xFFA20025), size = 36.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Read it later",
                    style = WindowsTypography.titleMedium.copy(color = Color.Black, fontSize = 15.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "When you're ready, saved articles will be waiting for you.",
                    style = WindowsTypography.bodyMedium.copy(color = Color.Gray, fontSize = 12.sp)
                )
            }
        }
    }
}

@Composable
private fun DesktopAppContent(onClose: () -> Unit) {
    // Replicating frame 00:10 from video:
    // VMware Blue Desktop Wallpaper with Recycle Bin icon and taskbar
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF091F44),
                        Color(0xFF0C2B61),
                        Color(0xFF06142B)
                    )
                )
            )
    ) {
        // Recycle Bin in top-left
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🗑", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Recycle Bin",
                style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
                color = Color.White
            )
        }

        // VMware / Windows 8.1 text at bottom right
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 64.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "Windows 8.1 Pro",
                style = WindowsTypography.labelSmall.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "Build 9600",
                style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        // Taskbar at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(44.dp)
                .background(Color(0xD9050C1A))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Windows Start button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                MetroIcon(glyph = "store", color = Color(0xFF00A4EF), size = 20.dp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            MetroIcon(glyph = "ie", color = Color(0xFF00A4EF), size = 20.dp)
            Spacer(modifier = Modifier.width(16.dp))
            MetroIcon(glyph = "desktop", color = Color(0xFFFFB900), size = 20.dp)
        }
    }
}

@Composable
private fun MoneyAppContent() {
    // Replicating frame 00:37 from video:
    // Green Money app with financial market quotes and graph
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(20.dp)
    ) {
        Text(
            text = "Markets Overview",
            style = WindowsTypography.headlineMedium.copy(color = Color(0xFF008A00), fontSize = 24.sp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MarketStatCard(title = "DOW", value = "38,722.69", change = "+0.45%", isUp = true)
            MarketStatCard(title = "S&P 500", value = "5,354.03", change = "+0.23%", isUp = true)
            MarketStatCard(title = "NASDAQ", value = "17,173.12", change = "+0.64%", isUp = true)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large stock chart preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFFF4FBF4)),
            contentAlignment = Alignment.Center
        ) {
            MetroIcon(glyph = "money", color = Color(0xFF008A00), size = 80.dp)
        }
    }
}

@Composable
private fun MarketStatCard(title: String, value: String, change: String, isUp: Boolean) {
    Column(
        modifier = Modifier
            .background(Color(0xFFF5F5F5))
            .padding(12.dp)
    ) {
        Text(text = title, style = WindowsTypography.labelSmall.copy(color = Color.Gray, fontSize = 11.sp))
        Text(text = value, style = WindowsTypography.titleMedium.copy(color = Color.Black, fontSize = 14.sp))
        Text(text = change, style = WindowsTypography.bodyMedium.copy(color = if (isUp) Color(0xFF008A00) else Color.Red, fontSize = 12.sp))
    }
}

@Composable
private fun GenericMetroAppContent(tile: TileModel, accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                MetroIcon(glyph = tile.iconGlyph.ifEmpty { "app" }, color = Color.White, size = 28.dp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = tile.title,
                style = WindowsTypography.displayLarge.copy(color = Color(0xFF222222), fontSize = 28.sp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to ${tile.title}.",
            style = WindowsTypography.bodyMedium.copy(color = Color.Gray, fontSize = 14.sp)
        )
    }
}

@Composable
private fun PCSettingsAppContent(
    appsRepository: AppsRepository?,
    onTestFlip: ((TileModel, LaunchOrigin) -> Unit)?,
    onClose: () -> Unit,
    onWallpaperParallaxChanged: ((Boolean) -> Unit)?,
    onWallpaperStyleChanged: ((Int) -> Unit)?,
) {
    val context = LocalContext.current
    var selectedMode by remember {
        mutableStateOf(appsRepository?.getFlipAnimationMode() ?: FlipAnimationMode.CLASSIC)
    }
    var launchTiming by remember {
        mutableStateOf(appsRepository?.getLaunchTiming() ?: LaunchTiming())
    }
    var editingAllApps by remember { mutableStateOf(false) }
    var allAppsTiming by remember {
        mutableStateOf(appsRepository?.getLaunchTiming(allApps = true) ?: LaunchTiming())
    }
    var wallpaperStyle by remember { mutableStateOf(appsRepository?.getWallpaperStyle() ?: 0) }
    var wallpaperParallaxEnabled by remember {
        mutableStateOf(appsRepository?.getWallpaperParallaxEnabled() ?: true)
    }

    fun updateWallpaperParallax(enabled: Boolean) {
        wallpaperParallaxEnabled = enabled
        appsRepository?.setWallpaperParallaxEnabled(enabled)
        onWallpaperParallaxChanged?.invoke(enabled)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F0F0))
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Page Title
        Text(
            text = "PC settings",
            style = WindowsTypography.displayLarge.copy(
                color = Color(0xFF222222),
                fontSize = 32.sp,
                fontWeight = FontWeight.Light
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Group Header
        Text(
            text = "App opening animation",
            style = WindowsTypography.titleLarge.copy(
                color = Color(0xFF5133AB),
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Select the 3D transition effect when opening apps from tiles",
            style = WindowsTypography.bodyMedium.copy(
                color = Color(0xFF666666),
                fontSize = 13.sp
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Option 1: Classic
        AnimationModeOptionCard(
            title = "Classic",
            tag = "Authentic Windows 8.1",
            description = "Continuous 3D flip from the tile into the full-screen app view.",
            duration = "${launchTiming.durationMillis} ms",
            isSelected = selectedMode == FlipAnimationMode.CLASSIC,
            onSelect = {
                selectedMode = FlipAnimationMode.CLASSIC
                appsRepository?.setFlipAnimationMode(FlipAnimationMode.CLASSIC)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Option 2: Modern
        AnimationModeOptionCard(
            title = "Modern",
            tag = "Expanding Card (No Flip)",
            description = "Smooth expansion from the tile into the full-screen app view without a flip.",
            duration = "${launchTiming.durationMillis} ms",
            isSelected = selectedMode == FlipAnimationMode.MODERN,
            onSelect = {
                selectedMode = FlipAnimationMode.MODERN
                appsRepository?.setFlipAnimationMode(FlipAnimationMode.MODERN)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "Start screen", true to "All Apps").forEach { (allApps, label) ->
                Text(label, color = if (editingAllApps == allApps) Color.White else Color(0xFF5133AB),
                    modifier = Modifier.background(if (editingAllApps == allApps) Color(0xFF5133AB) else Color.White)
                        .clickable { editingAllApps = allApps }.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimationTimingSettings(
            timing = if (editingAllApps) allAppsTiming else launchTiming,
            referenceProgress = if (editingAllApps) AllAppsLaunchMotion::expansionFraction
                else WindowsLaunchMotion::rotationFractionAtProgress,
            onTimingChange = { timing ->
                val safeTiming = timing.sanitized()
                if (editingAllApps) allAppsTiming = safeTiming else launchTiming = safeTiming
                appsRepository?.setLaunchTiming(safeTiming, allApps = editingAllApps)
            },
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFD0D0D0))
                .background(Color.White)
                .clickable { updateWallpaperParallax(!wallpaperParallaxEnabled) }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wallpaper parallax",
                    style = WindowsTypography.titleMedium.copy(
                        color = Color(0xFF222222),
                        fontSize = 15.sp,
                    ),
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Move the background as you scroll horizontally.",
                    style = WindowsTypography.bodyMedium.copy(
                        color = Color(0xFF666666),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = wallpaperParallaxEnabled,
                onCheckedChange = ::updateWallpaperParallax,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        WallpaperPicker(selected = wallpaperStyle, onSelect = { style ->
            wallpaperStyle = style
            appsRepository?.setWallpaperStyle(style)
            onWallpaperStyleChanged?.invoke(style)
        })
        Spacer(Modifier.height(20.dp))

        // Test Animation Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF5133AB))
                .clickable {
                    val testTile = TileModel(
                        id = "test_preview_tile",
                        title = "PC settings",
                        colorValue = 0xFF5133AB,
                        tileType = TileType.SETTINGS,
                        iconGlyph = "settings"
                    )
                    onTestFlip?.invoke(testTile, if (editingAllApps) LaunchOrigin.ALL_APPS else LaunchOrigin.START)
                }
                .padding(vertical = 14.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MetroIcon(glyph = "settings", color = Color.White, size = 18.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (editingAllApps) "Test All Apps Animation" else "Test ${selectedMode.displayName} Animation",
                style = WindowsTypography.titleMedium.copy(
                    color = Color.White,
                    fontSize = 14.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // System Settings Section
        Text(
            text = "System",
            style = WindowsTypography.titleLarge.copy(
                color = Color(0xFF5133AB),
                fontSize = 18.sp
            )
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFCCCCCC))
                .background(Color.White)
                .clickable {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
                .padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetroIcon(glyph = "desktop", color = Color(0xFF5133AB), size = 20.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Open Android System Settings",
                style = WindowsTypography.bodyLarge.copy(
                    color = Color.Black,
                    fontSize = 14.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // About Section
        Text(
            text = "About Tile8 Launcher",
            style = WindowsTypography.titleLarge.copy(
                color = Color(0xFF5133AB),
                fontSize = 18.sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .border(1.dp, Color(0xFFE0E0E0))
                .padding(16.dp)
        ) {
            Text(
                text = "Windows 8.1 Start Screen for Android",
                style = WindowsTypography.titleMedium.copy(color = Color.Black, fontSize = 14.sp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Horizontal Start layout • Continuous tile flip",
                style = WindowsTypography.bodyMedium.copy(color = Color.Gray, fontSize = 12.sp)
            )
        }
    }
}

@Composable
private fun AnimationModeOptionCard(
    title: String,
    tag: String,
    description: String,
    duration: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF5133AB) else Color(0xFFD0D0D0)
            )
            .background(if (isSelected) Color(0xFFF7F3FF) else Color.White)
            .clickable(onClick = onSelect)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(2.dp, if (isSelected) Color(0xFF5133AB) else Color.Gray)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF5133AB))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = title,
                        style = WindowsTypography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF5133AB) else Color.Black
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .background(if (isSelected) Color(0xFF5133AB) else Color(0xFFE5E5E5))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = tag,
                        style = WindowsTypography.labelSmall.copy(
                            color = if (isSelected) Color.White else Color(0xFF555555),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = WindowsTypography.bodyMedium.copy(
                    fontSize = 12.sp,
                    color = Color(0xFF555555),
                    lineHeight = 17.sp
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Duration: $duration",
                style = WindowsTypography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = Color(0xFF888888)
                )
            )
        }
    }
}
