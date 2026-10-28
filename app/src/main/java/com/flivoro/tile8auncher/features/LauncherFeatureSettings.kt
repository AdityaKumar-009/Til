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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.components.preloadAllAppsIcons
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.phone.PhoneLayoutStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val settingsPurple = Color(0xFF5133AB)
private val settingsRowShape = RoundedCornerShape(14.dp)

enum class LauncherSettingsSection { APPEARANCE, START, PRIVACY, DATA }

@Composable
fun LauncherFeatureSettings(
    appsRepository: AppsRepository?,
    section: LauncherSettingsSection = LauncherSettingsSection.START,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = appsRepository ?: remember(context) { AppsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var appPickerMode by remember { mutableStateOf<AppPickerMode?>(null) }
    var showIconPacks by remember { mutableStateOf(false) }
    var showIconStyles by remember { mutableStateOf(false) }
    var confirmLayoutReset by remember { mutableStateOf(false) }
    var showPhoneColumnsPicker by remember { mutableStateOf(false) }
    var showPhoneTransparencyPicker by remember { mutableStateOf(false) }
    val launcherModeRevision = LauncherFeatureRuntime.launcherModeRevision
    val launcherMode = remember(launcherModeRevision) { LauncherFeatureStore.launcherUiMode(context) }
    var showDoubleTapActions by remember { mutableStateOf(false) }
    var showStartGroupHeadings by remember {
        mutableStateOf(LauncherFeatureStore.showStartGroupHeadings(context))
    }
    var showRecentlyInstalledApps by remember {
        mutableStateOf(LauncherFeatureStore.showRecentlyInstalledPhoneApps(context))
    }
    var useAppAccentColorsInAllApps by remember {
        mutableStateOf(LauncherFeatureStore.useAppAccentColorsInAllApps(context))
    }
    var permissionRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val contactsAllowed = remember(permissionRevision) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val usageAllowed = remember(permissionRevision) { hasUsageAccess(context) }

    // MainActivity already requests the listener on resume. Reconnecting again
    // as soon as Settings composes competes with the app-opening transition.
    val liveTileAccessGranted = LiveTileRuntime.hasNotificationAccess(context)
    val liveTileConnected = LiveTileRuntime.listenerConnected
    val liveUpdateCount = LiveTileNotificationStore.current().size
    val liveTileStatus = when {
        liveTileConnected -> "Connected · $liveUpdateCount active updates"
        liveTileAccessGranted -> "Notification access granted · reconnecting"
        else -> "Notification access needed"
    }

    // Avoid package-manager work while a Settings window is animating open.
    // Only populate the catalog when a picker actually needs the app list.
    val installedApps by produceState<List<AppInfo>>(
        emptyList(), repository, appPickerMode, showIconStyles, showIconPacks,
    ) {
        if (appPickerMode != null || showIconStyles || showIconPacks) {
            value = withContext(Dispatchers.IO) { repository.getInstalledApps() }
        }
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
        permissionRevision++
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

    when (section) {
        LauncherSettingsSection.APPEARANCE -> {
            if (launcherMode != LauncherUiMode.DESKTOP) {
                SettingsActionRow("Tile density", "${LauncherFeatureStore.phoneSmallColumns(context, launcherMode)} small tiles across", "app") {
                    showPhoneColumnsPicker = true
                }
            }
            if (launcherMode == LauncherUiMode.MOBILE_10) {
                SettingsActionRow("Tile transparency", "${(LauncherFeatureStore.phoneTileOpacity(context) * 100).toInt()}% opacity", "photos") {
                    showPhoneTransparencyPicker = true
                }
            }
            SettingsActionRow("App icon style", LauncherFeatureStore.appIconStyle(context).displayName(), "app") {
                showIconStyles = true
            }
            SettingsActionRow("Icon pack", if (LauncherFeatureStore.appIconStyle(context) != AppIconStyle.DEFAULT)
                "Available with Default icon style" else LauncherFeatureStore.selectedIconPack(context)
                    ?: "System icons", "photos") { showIconPacks = true }
            SettingsToggleRow("App colors in All Apps", "Use each app's accent color", useAppAccentColorsInAllApps) { enabled ->
                useAppAccentColorsInAllApps = enabled
                LauncherFeatureStore.setUseAppAccentColorsInAllApps(context, enabled)
            }
            SettingsActionRow("Lock-screen slideshow", LauncherFeatureStore.lockSlideshowUris(context).let {
                if (it.isEmpty()) "No photos selected" else "${it.size} photos selected"
            }, "photos") { slideshowPicker.launch(arrayOf("image/*")) }
            SettingsActionRow("Quick status apps", "Up to seven apps on the lock screen", "app") {
                appPickerMode = AppPickerMode.LOCK_QUICK
            }
            SettingsActionRow("Detailed status app", "One app for expanded lock-screen updates", "mail") {
                appPickerMode = AppPickerMode.LOCK_DETAILED
            }
        }
        LauncherSettingsSection.START -> {
            SettingsActionRow("Live tiles & notifications", liveTileStatus, "mail") {
                LiveTileRuntime.requestReconnect(context)
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            if (launcherMode == LauncherUiMode.MOBILE_10) {
                SettingsToggleRow("Recently installed apps", "Show new apps first in All Apps", showRecentlyInstalledApps) { enabled ->
                    showRecentlyInstalledApps = enabled
                    LauncherFeatureStore.setShowRecentlyInstalledPhoneApps(context, enabled)
                }
            }
            if (launcherMode == LauncherUiMode.DESKTOP) {
                SettingsToggleRow("Start group headings", "Show names above tile groups", showStartGroupHeadings) { enabled ->
                    showStartGroupHeadings = enabled
                    LauncherFeatureStore.setShowStartGroupHeadings(context, enabled)
                }
            }
            SettingsActionRow("Double-tap Start", LauncherFeatureStore.doubleTapAction(context).displayName(), "app") {
                showDoubleTapActions = true
            }
        }
        LauncherSettingsSection.PRIVACY -> {
            SettingsActionRow("Hidden apps", "Hide apps from the list and search", "search") {
                appPickerMode = AppPickerMode.HIDDEN
            }
            SettingsActionRow("Private apps", "Require device authentication before opening", "people") {
                appPickerMode = AppPickerMode.PRIVATE
            }
            SettingsActionRow("Contact search",
                if (contactsAllowed) "Allowed" else "Permission needed",
                "people") {
                if (contactsAllowed) {
                    Toast.makeText(context, "Contact search is already enabled", Toast.LENGTH_SHORT).show()
                } else contactPermission.launch(Manifest.permission.READ_CONTACTS)
            }
            SettingsActionRow("Most-used app sorting",
                if (usageAllowed) "Usage access granted" else "Usage access needed", "clock") {
                runCatching { context.startActivity(usageAccessSettingsIntent()) }
            }
        }
        LauncherSettingsSection.DATA -> {
            SettingsActionRow("Back up launcher", "Save your tiles, layouts and preferences", "pin") {
                exportBackup.launch("TIL-Launcher-backup.json")
            }
            SettingsActionRow("Restore backup", "Import a previously saved launcher backup", "unpin") {
                restoreBackup.launch(arrayOf("application/json", "text/plain"))
            }
            if (launcherMode != LauncherUiMode.DESKTOP) {
                SettingsActionRow("Reset phone Start layout", "Restore the original Lumia tile arrangement", "app") {
                    confirmLayoutReset = true
                }
            }
            LauncherFeatureStore.lastPhoneCrash(context)?.let { crash ->
                SettingsActionRow("Copy crash report", "Diagnostic details from the last phone-mode recovery", "settings") {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("TIL phone crash", crash))
                    Toast.makeText(context, "Crash details copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (confirmLayoutReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLayoutReset = false },
            title = { Text("Reset phone Start?") },
            text = { Text("This restores the default phone tile arrangement. Your Desktop layout is unaffected.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLayoutReset = false
                    PhoneLayoutStore.resetOrder(context, launcherMode)
                    (context as? Activity)?.recreate()
                }) { Text("Reset") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmLayoutReset = false }) { Text("Cancel") }
            },
        )
    }
    if (showPhoneColumnsPicker) {
        Dialog(onDismissRequest = { showPhoneColumnsPicker = false }) {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(20.dp)) {
                Text("Phone tile density", fontSize = 22.sp, color = settingsPurple)
                listOf(4, 6).forEach { n ->
                    ChoiceRow(
                        label = if (n == 4) "Classic: 2 medium tiles across" else "Dense: 3 medium tiles across",
                        selected = LauncherFeatureStore.phoneSmallColumns(context, launcherMode) == n,
                        lightSurface = true,
                        onClick = {
                            LauncherFeatureStore.setPhoneSmallColumns(context, launcherMode, n)
                            showPhoneColumnsPicker = false
                        },
                    )
                }
            }
        }
    }

    if (showPhoneTransparencyPicker) {
        Dialog(onDismissRequest = { showPhoneTransparencyPicker = false }) {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(20.dp)) {
                Text("Windows 10 Mobile tile opacity", fontSize = 20.sp, color = settingsPurple)
                listOf(0, 25, 50, 75, 82, 100).forEach { value ->
                    ChoiceRow(
                        label = "$value%",
                        selected = (LauncherFeatureStore.phoneTileOpacity(context) * 100).toInt() == value,
                        lightSurface = true,
                        onClick = {
                            LauncherFeatureStore.setPhoneTileOpacity(context, value)
                            showPhoneTransparencyPicker = false
                        },
                    )
                }
            }
        }
    }

    if (showIconStyles) {
        AppIconStyleDialog(
            selected = LauncherFeatureStore.appIconStyle(context),
            onDismiss = { showIconStyles = false },
            onSelect = { style ->
                LauncherFeatureStore.setAppIconStyle(context, style)
                val revision = LauncherFeatureRuntime.iconsRevision
                // Warm the newly selected style immediately while the settings surface is still
                // alive. All Apps then re-enters with the new thumbnail generation already filling
                // instead of waiting for a launcher restart/cold cache rebuild.
                scope.launch {
                    preloadAllAppsIcons(
                        context = context.applicationContext,
                        repository = repository,
                        packageNames = installedApps.map(AppInfo::packageName),
                        style = style,
                        iconsRevision = revision,
                    )
                }
                showIconStyles = false
            },
        )
    }

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
private fun SettingsCategoryHeader(title: String) {
    Spacer(Modifier.height(22.dp))
    Text(title, color = settingsPurple, fontSize = 18.sp)
    Spacer(Modifier.height(8.dp))
}

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
            .clip(settingsRowShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE4E7EF), settingsRowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp))
            .background(Color(0xFFF0ECFB)), contentAlignment = Alignment.Center) {
            MetroIcon(glyph = glyph, color = settingsPurple, size = 22.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFF1C2435), fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(description, color = Color(0xFF687183), fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text("›", color = Color(0xFF99A0B0), fontSize = 25.sp)
    }
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(settingsRowShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE4E7EF), settingsRowShape)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFF1C2435), fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(description, color = Color(0xFF687183), fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun AppIconStyleDialog(
    selected: AppIconStyle,
    onDismiss: () -> Unit,
    onSelect: (AppIconStyle) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(settingsRowShape)
            .background(Color(0xFFF6F7FA)).padding(18.dp)) {
            Text(
                "App icon style",
                color = Color(0xFF1C2435),
                style = WindowsTypography.titleLarge.copy(fontSize = 19.sp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Choose how app icons look on Start and in All Apps. Icon packs work with Default.",
                color = Color(0xFF687183),
                style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
            )
            Spacer(Modifier.height(10.dp))
            AppIconStyle.entries.forEach { style ->
                ChoiceRow(style.displayName(), style == selected) { onSelect(style) }
            }
        }
    }
}

@Composable
private fun IconPackDialog(
    packs: List<Pair<String, String>>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(settingsRowShape)
            .background(Color(0xFFF6F7FA)).padding(18.dp)) {
            Text("Icon pack", color = Color(0xFF1C2435), style = WindowsTypography.titleLarge.copy(fontSize = 19.sp))
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
        Column(Modifier.fillMaxWidth().clip(settingsRowShape)
            .background(Color(0xFFF6F7FA)).padding(18.dp)) {
            Text("Double-tap Start", color = Color(0xFF1C2435), style = WindowsTypography.titleLarge.copy(fontSize = 19.sp))
            Spacer(Modifier.height(10.dp))
            StartDoubleTapAction.entries.forEach { action ->
                ChoiceRow(action.displayName(), action == selected) { onSelect(action) }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    lightSurface: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val foreground = if (lightSurface) Color(0xFF242424) else Color.White
    val accent = if (lightSurface) settingsPurple else Color.White
    val outline = if (lightSurface) Color(0xFF777777) else Color(0x99FFFFFF)
    Row(
        Modifier.fillMaxWidth()
            .then(modifier)
            .clip(RoundedCornerShape(11.dp))
            .background(if (lightSurface && selected) Color(0xFFF1EBFA) else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp)
                .border(2.dp, if (selected) accent else outline)
                .background(if (selected) accent else Color.Transparent),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = foreground, style = WindowsTypography.bodyMedium.copy(fontSize = 14.sp))
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
        Column(Modifier.fillMaxWidth().clip(settingsRowShape)
            .background(Color(0xFFF6F7FA)).padding(16.dp)) {
            Text(title, color = Color(0xFF1C2435), style = WindowsTypography.titleLarge.copy(fontSize = 19.sp))
            if (mode == AppPickerMode.LOCK_QUICK) {
                Text(
                    "${quick.size}/7 selected",
                    color = Color(0xFF687183),
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
                            color = Color(0xFF1C2435),
                            style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        if (checked) Text("✓", color = settingsPurple, fontSize = 17.sp)
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


private fun AppIconStyle.displayName(): String = when (this) {
    AppIconStyle.DISCO_LAYERED -> "Disco layered (recommended)"
    AppIconStyle.DEFAULT -> "Default / icon pack"
    AppIconStyle.ANDROID_ADAPTIVE -> "Android adaptive"
    AppIconStyle.WHITE_MONOCHROME -> "White monochrome"
}
