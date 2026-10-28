package com.flivoro.tile8auncher.ui.components

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.LaunchTiming
import com.flivoro.tile8auncher.features.DefaultHomeApp
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureSettings
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherSettingsSection
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import com.flivoro.tile8auncher.features.PhoneBackgroundStyle

private val ink = Color(0xFF1C2435)
private val muted = Color(0xFF687183)
private val accent = Color(0xFF5D3AB5)
private val canvas = Color(0xFFF6F7FA)
private val stroke = Color(0xFFE4E7EF)
private val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

private enum class SettingsPage(val title: String, val subtitle: String) {
    OVERVIEW("Settings", "Make your Start screen feel like yours"),
    PERSONALIZATION("Personalization", "Wallpapers, colors, tiles and icons"),
    START("Start & motion", "How your launcher looks and responds"),
    PRIVACY("Apps & privacy", "App visibility, search and permissions"),
    DATA("Backup & reset", "Keep a copy of your setup"),
}

/** A single design system and navigation path for all three launcher interfaces. */
@Composable
internal fun LauncherSettingsScreen(
    onClose: () -> Unit,
    appsRepository: AppsRepository?,
    onWallpaperStyleChanged: ((Int) -> Unit)? = null,
    onWallpaperParallaxChanged: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = appsRepository ?: remember(context) { AppsRepository(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val revision = LauncherFeatureRuntime.launcherModeRevision
    val mode = remember(revision) { LauncherFeatureStore.launcherUiMode(context) }
    val animation = remember(revision) { LauncherFeatureStore.masterLaunchAnimation(context) }
    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.OVERVIEW) }
    // Each Settings page starts at its own top. Reusing one scroll offset across
    // pages can strand the Overview navigation rows outside the visible viewport.
    val pageScrollState = key(currentPage) { rememberScrollState() }
    var showModes by remember { mutableStateOf(false) }
    var showAnimations by remember { mutableStateOf(false) }
    var showWallpapers by rememberSaveable { mutableStateOf(false) }
    var showAdvancedMotion by rememberSaveable { mutableStateOf(false) }
    var editingAllApps by rememberSaveable { mutableStateOf(false) }
    var wallpaperStyle by remember { mutableStateOf(repository.getWallpaperStyle()) }
    var parallax by remember { mutableStateOf(repository.getWallpaperParallaxEnabled()) }
    var startTiming by remember { mutableStateOf(repository.getLaunchTiming()) }
    var appsTiming by remember { mutableStateOf(repository.getLaunchTiming(allApps = true)) }
    var isDefault by remember { mutableStateOf(DefaultHomeApp.isSelected(context)) }
    val homePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { isDefault = DefaultHomeApp.isSelected(context) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isDefault = DefaultHomeApp.isSelected(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun back() {
        if (currentPage == SettingsPage.OVERVIEW) onClose()
        else currentPage = SettingsPage.OVERVIEW
    }
    BackHandler { back() }

    Box(
        Modifier.fillMaxSize().background(canvas).statusBarsPadding().navigationBarsPadding()
            .testTag("til-settings-screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .verticalScroll(pageScrollState)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(role = Role.Button, onClick = ::back)
                        .semantics { contentDescription = "Back" }
                        .testTag("launcher-settings-back"),
                    contentAlignment = Alignment.Center,
                ) { Text("←", color = ink, fontSize = 25.sp) }
                Spacer(Modifier.width(6.dp))
                Text("TIL", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(22.dp))
            Text(currentPage.title, color = ink, fontSize = 32.sp,
                lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(currentPage.subtitle, color = muted, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(26.dp))

            when (currentPage) {
                SettingsPage.OVERVIEW -> {
                    SettingsEyebrow("GET STARTED")
                    Column(Modifier.fillMaxWidth().clip(shape).background(Color.White)
                        .border(1.dp, stroke, shape).padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile("desktop")
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Default Home app", color = ink, fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(3.dp))
                                Text(if (isDefault) "✓  TIL is your default" else "Not set up yet",
                                    color = if (isDefault) Color(0xFF197B53) else Color(0xFF99651F),
                                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.testTag("launcher-default-home-status"))
                            }
                        }
                        Spacer(Modifier.height(13.dp))
                        Text(
                            if (isDefault) "Pressing Home takes you to TIL."
                            else "Use TIL whenever you swipe Home or press the Home button. Android will ask you to confirm.",
                            color = muted, fontSize = 13.sp, lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(11.dp))
                                .background(accent).clickable(role = Role.Button) {
                                    try {
                                        homePicker.launch(DefaultHomeApp.selectionIntent(context, isDefault))
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(context, "Home app settings unavailable", Toast.LENGTH_LONG).show()
                                    }
                                }.testTag("launcher-default-home-action")
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (isDefault) "Manage Home app" else "Set TIL as default",
                                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    SettingsNavRow("Launcher style", mode.title,
                        "app", tag = "launcher-style-action") { showModes = true }
                    Spacer(Modifier.height(28.dp))
                    SettingsEyebrow("YOUR PREFERENCES")
                    SettingsNavRow("Personalization", "Wallpaper, tiles, icons & lock screen", "photos") {
                        currentPage = SettingsPage.PERSONALIZATION
                    }
                    SettingsNavRow("Start & motion", "Live tiles, gestures & animation", "app") {
                        currentPage = SettingsPage.START
                    }
                    SettingsNavRow("Apps & privacy", "Hidden apps, search & permissions", "people") {
                        currentPage = SettingsPage.PRIVACY
                    }
                    SettingsNavRow("Backup & reset", "Save or restore your layout", "pin") {
                        currentPage = SettingsPage.DATA
                    }
                    Spacer(Modifier.height(12.dp))
                }
                SettingsPage.PERSONALIZATION -> {
                    SettingsEyebrow("YOUR BACKGROUND")
                    SettingsNavRow("Wallpaper & accent", "Background picture, colors & lock screen", "photos") {
                        showWallpapers = !showWallpapers
                    }
                    if (showWallpapers) {
                        Column(Modifier.fillMaxWidth().clip(shape).background(Color.White)
                            .padding(16.dp)) {
                            WallpaperPicker(
                                selected = wallpaperStyle,
                                appsRepository = repository,
                                onSelect = { selected ->
                                    wallpaperStyle = selected
                                    repository.setWallpaperStyle(selected)
                                    onWallpaperStyleChanged?.invoke(selected)
                                },
                            )
                        }
                    }
                    if (mode != LauncherUiMode.DESKTOP) {
                        Spacer(Modifier.height(8.dp))
                        SettingsEyebrow("BACKGROUND PLACEMENT")
                        PhoneBackgroundStyle.entries.forEach { style ->
                            SettingsRadioRow(style.displayName,
                                LauncherFeatureStore.backgroundStyle(context, mode) == style,
                                tag = "background-${style.name}") {
                                LauncherFeatureStore.setBackgroundStyle(context, mode, style)
                            }
                        }
                    } else {
                        SettingsToggleRow("Wallpaper movement", "Subtle movement when scrolling Start",
                            parallax) { enabled ->
                            parallax = enabled
                            repository.setWallpaperParallaxEnabled(enabled)
                            onWallpaperParallaxChanged?.invoke(enabled)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SettingsEyebrow("TILES & ICONS")
                    LauncherFeatureSettings(repository, LauncherSettingsSection.APPEARANCE)
                }
                SettingsPage.START -> {
                    SettingsEyebrow("START BEHAVIOR")
                    LauncherFeatureSettings(repository, LauncherSettingsSection.START)
                    Spacer(Modifier.height(18.dp))
                    SettingsEyebrow("APP-OPENING MOTION")
                    SettingsNavRow("Animation style", animation.title, "app") {
                        showAnimations = true
                    }
                    SettingsNavRow("Advanced motion timing", "Fine-tune speed and easing (optional)", "clock") {
                        showAdvancedMotion = !showAdvancedMotion
                    }
                    if (showAdvancedMotion) {
                        Column(Modifier.fillMaxWidth().clip(shape).background(Color.White)
                            .padding(16.dp)) {
                            Text("Leave these at their defaults for the original Windows motion.",
                                color = muted, fontSize = 12.sp, lineHeight = 18.sp)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(false to "Start", true to "All Apps").forEach { (allApps, label) ->
                                    Box(Modifier.weight(1f)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                                        .background(if (editingAllApps == allApps) accent else canvas)
                                        .clickable { editingAllApps = allApps }
                                        .padding(12.dp), contentAlignment = Alignment.Center) {
                                        Text(label, fontSize = 13.sp,
                                            color = if (editingAllApps == allApps) Color.White else ink)
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            AnimationTimingSettings(
                                timing = if (editingAllApps) appsTiming else startTiming,
                                onTimingChange = { next ->
                                    val safe = next.sanitized()
                                    if (editingAllApps) appsTiming = safe else startTiming = safe
                                    repository.setLaunchTiming(safe, allApps = editingAllApps)
                                },
                            )
                        }
                    }
                }
                SettingsPage.PRIVACY -> {
                    SettingsEyebrow("APP VISIBILITY & ACCESS")
                    LauncherFeatureSettings(repository, LauncherSettingsSection.PRIVACY)
                }
                SettingsPage.DATA -> {
                    SettingsEyebrow("YOUR DATA")
                    Text("Export a backup before resetting or switching phones.",
                        color = muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                    LauncherFeatureSettings(repository, LauncherSettingsSection.DATA)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showModes) {
        SettingsChoiceDialog("Launcher style", onDismiss = { showModes = false }) {
            Column(Modifier.selectableGroup()) {
                LauncherUiMode.entries.forEach { choice ->
                    SettingsRadioRow(choice.title, mode == choice,
                        detail = when (choice) {
                            LauncherUiMode.DESKTOP -> "Wide, horizontal Windows Start"
                            LauncherUiMode.PHONE_8 -> "Classic Lumia phone tiles"
                            LauncherUiMode.MOBILE_10 -> "Modern phone Start and live tiles"
                        },
                        tag = "launcher-mode-${choice.name}") {
                        showModes = false
                        if (choice != mode) {
                            LauncherFeatureStore.setLauncherUiMode(context, choice,
                                phoneRenderAttempted = choice != LauncherUiMode.DESKTOP)
                            // Show the new interface immediately and keep Settings out of
                            // the cross-mode handoff; a second Back was previously required.
                            onClose()
                        }
                    }
                }
            }
        }
    }
    if (showAnimations) {
        SettingsChoiceDialog("App-opening animation", onDismiss = { showAnimations = false }) {
            Column(Modifier.selectableGroup()) {
                MasterLaunchAnimation.selectableChoices.forEach { choice ->
                    SettingsRadioRow(choice.title, animation == choice, choice.detail) {
                        LauncherFeatureStore.setMasterLaunchAnimation(context, choice)
                        showAnimations = false
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEyebrow(label: String) {
    Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 3.dp, bottom = 10.dp))
}

@Composable
private fun IconTile(glyph: String) {
    Box(Modifier.size(45.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        .background(Color(0xFFF0ECFB)), contentAlignment = Alignment.Center) {
        MetroIcon(glyph, color = accent, size = 23.dp)
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    detail: String,
    glyph: String,
    tag: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(shape).background(Color.White)
            .border(1.dp, stroke, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .padding(horizontal = 15.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(glyph)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = Color(0xFF99A0B0), fontSize = 26.sp)
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    detail: String,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(shape).background(Color.White)
            .border(1.dp, stroke, shape).clickable { onChange(!enabled) }
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsRadioRow(
    title: String,
    selected: Boolean,
    detail: String? = null,
    tag: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 7.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFF1EDFC) else Color.White)
            .border(1.dp, if (selected) accent else stroke,
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(detail, color = muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(23.dp).border(2.dp, if (selected) accent else Color(0xFFADB4C2),
            androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(11.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(accent))
        }
    }
}

@Composable
private fun SettingsChoiceDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).clip(shape)
            .background(canvas).padding(19.dp)) {
            Text(title, color = ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        }
    }
}
