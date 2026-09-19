package com.flivoro.tile8auncher.features

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val settingsPurple = Color(0xFF5133AB)

@Composable
fun LauncherFeatureSettings(appsRepository: AppsRepository?) {
    val context = LocalContext.current
    val repository = appsRepository ?: remember(context) { AppsRepository(context.applicationContext) }
    var appPickerMode by remember { mutableStateOf<AppPickerMode?>(null) }
    var showIconPacks by remember { mutableStateOf(false) }
    var showDoubleTapActions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LiveTileRuntime.requestReconnect(context)
    }
    val liveTileAccessGranted = LiveTileRuntime.hasNotificationAccess(context)
    val liveTileConnected = LiveTileRuntime.listenerConnected
    val liveUpdateCount = LiveTileNotificationStore.current().size
    val liveTileStatus = when {
        liveTileConnected && liveUpdateCount > 0 ->
            "Connected • $liveUpdateCount active update(s) captured. Medium/wide tiles keep live content visible; small tiles show badges."
        liveTileConnected ->
            "Connected • waiting for notifications. A tile becomes live when its Android app posts readable notification content."
        liveTileAccessGranted ->
            "Notification access is granted, but the listener is reconnecting. Tap here if it remains disconnected."
        else ->
            "Grant Notification access so Tile8 can project Android notifications into Windows-style live tile updates."
    }

    val installedApps by produceState<List<AppInfo>>(emptyList(), repository) {
        value = withContext(Dispatchers.IO) { repository.getInstalledApps() }
    }
    val iconPacks by produceState<List<Pair<String, String>>>(emptyList(), showIconPacks) {
        if (showIconPacks) {
            value = withContext(Dispatchers.IO) {
                IconPackManager.discoverIconPacks(context.applicationContext)
            }
        }
    }

    val contactPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Toast.makeText(
            context,
            if (granted) "Contact search enabled" else "Contact permission was not granted",
            Toast.LENGTH_SHORT,
        ).show()
    }

    val slideshowPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        LauncherFeatureStore.setLockSlideshowUris(context, uris.map { it.toString() })
    }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val result = LauncherBackupManager.exportTo(context, uri)
            Toast.makeText(
                context,
                if (result.isSuccess) "Mosaic backup saved" else "Could not save backup",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val result = LauncherBackupManager.importFrom(context, uri)
            Toast.makeText(
                context,
                if (result.isSuccess) "Mosaic backup restored" else "Backup is invalid or unreadable",
                Toast.LENGTH_LONG,
            ).show()
            if (result.isSuccess) (context as? Activity)?.recreate()
        }
    }

    Spacer(Modifier.height(28.dp))
    Text("Launcher features", color = settingsPurple, fontSize = 20.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "Windows 8.1 behavior stays the default. Android-only extensions below are isolated from Start, All Apps and app-launch motion timing.",
        color = Color(0xFF666666),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(12.dp))

    SettingsActionRow(
        title = if (liveTileConnected) "Live tiles • Connected" else "Live tiles",
        description = liveTileStatus,
        glyph = "mail",
    ) {
        LiveTileRuntime.requestReconnect(context)
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    SettingsActionRow(
        title = "Add Android widget",
        description = "Pin a real Android widget as a wide Start tile. Multiple widgets can be stacked later from Start customize mode.",
        glyph = "app",
    ) {
        context.startActivity(Intent(context, WidgetPickerActivity::class.java))
    }

    SettingsActionRow(
        title = "Icon pack",
        description = LauncherFeatureStore.selectedIconPack(context)?.let { "Selected: $it" }
            ?: "Use compatible ADW/Nova-style icon packs, with per-app overrides still taking priority.",
        glyph = "photos",
    ) { showIconPacks = true }

    SettingsActionRow(
        title = "Double-tap Start",
        description = "Action: ${LauncherFeatureStore.doubleTapAction(context).displayName()}",
        glyph = "app",
    ) { showDoubleTapActions = true }

    SettingsActionRow(
        title = "Hidden apps",
        description = "Keep selected apps out of All Apps and launcher search.",
        glyph = "search",
    ) { appPickerMode = AppPickerMode.HIDDEN }

    SettingsActionRow(
        title = "Private apps",
        description = "Hide selected apps until Android device credentials are confirmed in All Apps.",
        glyph = "people",
    ) { appPickerMode = AppPickerMode.PRIVATE }

    SettingsActionRow(
        title = "Contact search",
        description = "Allow universal search to find saved contacts. Permission is requested only when you enable it.",
        glyph = "people",
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Contact search is already enabled", Toast.LENGTH_SHORT).show()
        } else {
            contactPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    SettingsActionRow(
        title = "Most used app sorting",
        description = "Grant Android Usage Access so Windows 8.1's Most used sort can use real foreground history.",
        glyph = "clock",
    ) {
        runCatching { context.startActivity(usageAccessSettingsIntent()) }
    }

    Spacer(Modifier.height(20.dp))
    Text("Windows 8.1 lock screen extras", color = settingsPurple, fontSize = 18.sp)
    Spacer(Modifier.height(10.dp))

    SettingsActionRow(
        title = "Lock-screen slideshow",
        description = LauncherFeatureStore.lockSlideshowUris(context).let { uris ->
            if (uris.isEmpty()) "Choose multiple pictures for the Windows 8.1 slideshow." else "${uris.size} slideshow picture(s) selected."
        },
        glyph = "photos",
    ) { slideshowPicker.launch(arrayOf("image/*")) }

    SettingsActionRow(
        title = "Quick status apps",
        description = "Choose up to seven apps for Windows 8.1-style lock-screen status slots.",
        glyph = "app",
    ) { appPickerMode = AppPickerMode.LOCK_QUICK }

    SettingsActionRow(
        title = "Detailed status app",
        description = "Choose one app whose latest notification can appear as detailed lock-screen status.",
        glyph = "mail",
    ) { appPickerMode = AppPickerMode.LOCK_DETAILED }

    Spacer(Modifier.height(20.dp))
    Text("Backup", color = settingsPurple, fontSize = 18.sp)
    Spacer(Modifier.height(10.dp))

    SettingsActionRow(
        title = "Back up Mosaic",
        description = "Export Start layout, tile sizes/colors/groups, wallpapers, lock screen and launcher feature preferences.",
        glyph = "pin",
    ) { exportBackup.launch("Mosaic-Launcher-backup.json") }

    SettingsActionRow(
        title = "Restore Mosaic",
        description = "Restore a Mosaic Launcher JSON backup and reload the launcher configuration.",
        glyph = "unpin",
    ) { restoreBackup.launch(arrayOf("application/json", "text/plain")) }

    if (showIconPacks) {
        IconPackDialog(
            packs = iconPacks,
            selected = LauncherFeatureStore.selectedIconPack(context),
            onDismiss = { showIconPacks = false },
            onSelect = { packageName ->
                LauncherFeatureStore.setSelectedIconPack(context, packageName)
                IconPackManager.clearCaches()
                LauncherFeatureRuntime.notifyIconsChanged()
                showIconPacks = false
            },
        )
    }

    if (showDoubleTapActions) {
        DoubleTapActionDialog(
            selected = LauncherFeatureStore.doubleTapAction(context),
            onDismiss = { showDoubleTapActions = false },
            onSelect = { action ->
                LauncherFeatureStore.setDoubleTapAction(context, action)
                showDoubleTapActions = false
                if (action == StartDoubleTapAction.LOCK_DEVICE && !MosaicAccessibilityService.isConnected()) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
            },
        )
    }

    appPickerMode?.let { mode ->
        AppSelectionDialog(
            mode = mode,
            apps = installedApps,
            repository = repository,
            onDismiss = { appPickerMode = null },
        )
    }
}

private enum class AppPickerMode { HIDDEN, PRIVATE, LOCK_QUICK, LOCK_DETAILED }

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    glyph: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFD0D0D0))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroIcon(glyph = glyph, color = settingsPurple, size = 21.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFF222222), fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(description, color = Color(0xFF666666), fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun IconPackDialog(
    packs: List<Pair<String, String>>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(Color(0xF0180424)).padding(18.dp)) {
            Text("Icon pack", color = Color.White, style = WindowsTypography.titleLarge.copy(fontSize = 19.sp))
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    ChoiceRow("System icons", selected == null) { onSelect(null) }
                }
                items(packs, key = { it.first }) { (pkg, label) ->
                    ChoiceRow(label, selected == pkg) { onSelect(pkg) }
                }
            }
        }
    }
}

@Composable
private fun DoubleTapActionDialog(
    selected: StartDoubleTapAction,
    onDismiss: () -> Unit,
    onSelect: (StartDoubleTapAction) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(Color(0xF0180424)).padding(18.dp)) {
            Text("Double-tap Start", color = Color.White, style = WindowsTypography.titleLarge.copy(fontSize = 19.sp))
            Spacer(Modifier.height(10.dp))
            StartDoubleTapAction.entries.forEach { action ->
                ChoiceRow(action.displayName(), action == selected) { onSelect(action) }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(17.dp)
                .border(2.dp, if (selected) Color.White else Color(0x99FFFFFF))
                .background(if (selected) Color.White else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp))
    }
}

@Composable
private fun AppSelectionDialog(
    mode: AppPickerMode,
    apps: List<AppInfo>,
    repository: AppsRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var hidden by remember { mutableStateOf(LauncherFeatureStore.hiddenPackages(context)) }
    var privateApps by remember { mutableStateOf(LauncherFeatureStore.privatePackages(context)) }
    var quick by remember { mutableStateOf(LauncherFeatureStore.lockStatusPackages(context).toSet()) }
    var detailed by remember { mutableStateOf(LauncherFeatureStore.lockDetailedPackage(context)) }

    val title = when (mode) {
        AppPickerMode.HIDDEN -> "Hidden apps"
        AppPickerMode.PRIVATE -> "Private apps"
        AppPickerMode.LOCK_QUICK -> "Quick status apps"
        AppPickerMode.LOCK_DETAILED -> "Detailed status app"
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(Color(0xF0180424)).padding(16.dp)) {
            Text(title, color = Color.White, style = WindowsTypography.titleLarge.copy(fontSize = 19.sp))
            if (mode == AppPickerMode.LOCK_QUICK) {
                Text(
                    "${quick.size}/7 selected",
                    color = Color.White.copy(alpha = .7f),
                    style = WindowsTypography.labelSmall.copy(fontSize = 10.sp),
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                items(apps, key = { it.packageName }) { app ->
                    val icon = rememberAppIcon(repository, app.packageName)
                    val checked = when (mode) {
                        AppPickerMode.HIDDEN -> app.packageName in hidden
                        AppPickerMode.PRIVATE -> app.packageName in privateApps
                        AppPickerMode.LOCK_QUICK -> app.packageName in quick
                        AppPickerMode.LOCK_DETAILED -> app.packageName == detailed
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                when (mode) {
                                    AppPickerMode.HIDDEN -> {
                                        hidden = hidden.toMutableSet().apply {
                                            if (!add(app.packageName)) remove(app.packageName)
                                        }
                                        LauncherFeatureStore.setHiddenPackages(context, hidden)
                                    }
                                    AppPickerMode.PRIVATE -> {
                                        privateApps = privateApps.toMutableSet().apply {
                                            if (!add(app.packageName)) remove(app.packageName)
                                        }
                                        LauncherFeatureStore.setPrivatePackages(context, privateApps)
                                    }
                                    AppPickerMode.LOCK_QUICK -> {
                                        quick = quick.toMutableSet().apply {
                                            if (app.packageName in this) remove(app.packageName)
                                            else if (size < 7) add(app.packageName)
                                        }
                                        LauncherFeatureStore.setLockStatusPackages(context, quick.toList())
                                    }
                                    AppPickerMode.LOCK_DETAILED -> {
                                        detailed = if (detailed == app.packageName) null else app.packageName
                                        LauncherFeatureStore.setLockDetailedPackage(context, detailed)
                                    }
                                }
                            }
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(34.dp).background(settingsPurple),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (icon != null) androidx.compose.foundation.Image(icon, app.label, Modifier.size(25.dp))
                            else MetroIcon("app", color = Color.White, size = 20.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            app.label,
                            color = Color.White,
                            style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        if (checked) Text("✓", color = Color.White, fontSize = 17.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Done",
                color = Color.White,
                modifier = Modifier.align(Alignment.End).background(settingsPurple).clickable(onClick = onDismiss)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}

private fun StartDoubleTapAction.displayName(): String = when (this) {
    StartDoubleTapAction.NONE -> "None"
    StartDoubleTapAction.SEARCH -> "Search"
    StartDoubleTapAction.ALL_APPS -> "All Apps"
    StartDoubleTapAction.CHARMS -> "Charms"
    StartDoubleTapAction.LOCK_DEVICE -> "Lock device"
}
