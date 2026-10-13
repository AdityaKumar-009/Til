package com.flivoro.tile8auncher.ui.phone

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.PhoneBackgroundStyle
import com.flivoro.tile8auncher.ui.components.StartPersonalization

/**
 * Phone-native Settings presentation. The phone/desktop mode switch remains available
 * in TIL Settings, and system-only changes open the real Android settings activities.
 */
@Composable
internal fun PhoneNativeSettingsSurface(
    mode: LauncherUiMode,
    onClose: () -> Unit,
    onLauncherSettings: () -> Unit,
) {
    val context = LocalContext.current
    val isTen = mode == LauncherUiMode.MOBILE_10
    var page by remember(mode) { mutableStateOf<String?>(null) }
    var filter by remember(mode) { mutableStateOf("") }
    val accent = StartPersonalization.accentColor
    // Subscribe to preference changes so theme/density choices refresh immediately.
    @Suppress("UNUSED_VARIABLE")
    val revision = LauncherFeatureRuntime.launcherModeRevision
    fun back() { if (page != null) page = null else onClose() }
    fun openSystem(action: String) {
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Color.White, fontSize = 29.sp,
                modifier = Modifier.clickable { back() }.padding(horizontal = 21.dp))
            if (isTen) Text("Settings", color = Color.White, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(page ?: if (isTen) "Settings" else "settings",
                color = Color.White, fontSize = if (isTen) 36.sp else 43.sp,
                fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 7.dp, bottom = 15.dp))
            if (page == null) {
                if (isTen) {
                    BasicTextField(
                        value = filter, onValueChange = { filter = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF292929))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (filter.isBlank()) Text("Find a setting", color = Color.LightGray, fontSize = 16.sp)
                                inner()
                            }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    listOf("System", "Devices", "Network & wireless", "Personalization",
                        "Accounts", "Time & language", "Ease of access", "Privacy",
                        "Update & security", "TIL launcher settings")
                        .filter { it.contains(filter, ignoreCase = true) }
                        .forEach { title ->
                            PhoneSettingsRow(title, "",
                                tag = if (title == "TIL launcher settings") "phone-native-advanced-settings" else null) {
                                if (title == "TIL launcher settings") onLauncherSettings()
                                else page = title
                            }
                        }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        Text("system", color = accent, fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 12.dp))
                        Text("applications", color = Color.LightGray, fontSize = 20.sp,
                            modifier = Modifier.clickable { page = "applications" })
                    }
                    listOf("start+theme", "ringtones+sounds", "email+accounts", "Wi-Fi",
                        "Bluetooth", "cellular+SIM", "lock screen", "notifications+actions",
                        "brightness", "date+time", "about", "TIL launcher settings")
                        .forEach { item ->
                            PhoneSettingsRow(item, "",
                                tag = if (item == "TIL launcher settings") "phone-native-advanced-settings" else null) {
                                when (item) {
                                    "TIL launcher settings" -> onLauncherSettings()
                                    "Wi-Fi" -> openSystem(Settings.ACTION_WIFI_SETTINGS)
                                    "Bluetooth" -> openSystem(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    "brightness" -> openSystem(Settings.ACTION_DISPLAY_SETTINGS)
                                    "date+time" -> openSystem(Settings.ACTION_DATE_SETTINGS)
                                    "email+accounts" -> openSystem(Settings.ACTION_SYNC_SETTINGS)
                                    "ringtones+sounds" -> openSystem(Settings.ACTION_SOUND_SETTINGS)
                                    "cellular+SIM" -> openSystem(Settings.ACTION_WIRELESS_SETTINGS)
                                    "about" -> openSystem(Settings.ACTION_DEVICE_INFO_SETTINGS)
                                    else -> page = item
                                }
                            }
                        }
                }
            } else if (page == "Personalization" || page == "start+theme") {
                Text(if (isTen) "Start" else "start background", color = accent, fontSize = 21.sp)
                if (isTen) {
                    PhoneBackgroundStyle.entries.forEach { style ->
                        PhoneSettingsRow(style.displayName,
                            if (LauncherFeatureStore.phoneBackgroundStyle(context) == style) "selected" else "") {
                            LauncherFeatureStore.setPhoneBackgroundStyle(context, style)
                        }
                    }
                    PhoneSettingsRow("Tile transparency", "Configure in TIL settings") { onLauncherSettings() }
                }
                val columns = LauncherFeatureStore.phoneSmallColumns(context, mode)
                PhoneSettingsRow("Show more tiles: off", if (columns == 4) "selected" else "") {
                    LauncherFeatureStore.setPhoneSmallColumns(context, mode, 4)
                }
                PhoneSettingsRow("Show more tiles: on", if (columns == 6) "selected" else "") {
                    LauncherFeatureStore.setPhoneSmallColumns(context, mode, 6)
                }
                PhoneSettingsRow("Choose picture and accent color", "TIL personalization") { onLauncherSettings() }
            } else {
                val choices = when (page) {
                    "Network & wireless" -> listOf("Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
                        "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
                        "Airplane mode" to Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                    "Devices" -> listOf("Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
                        "Display" to Settings.ACTION_DISPLAY_SETTINGS)
                    "Time & language" -> listOf("Date & time" to Settings.ACTION_DATE_SETTINGS)
                    "Privacy" -> listOf("Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    "System" -> listOf("Display" to Settings.ACTION_DISPLAY_SETTINGS,
                        "Sound" to Settings.ACTION_SOUND_SETTINGS)
                    else -> listOf("Open Android settings" to Settings.ACTION_SETTINGS)
                }
                choices.forEach { (label, action) ->
                    PhoneSettingsRow(label, "") { openSystem(action) }
                }
                PhoneSettingsRow("TIL launcher settings", "Launcher customization") { onLauncherSettings() }
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { back() }.padding(horizontal = 28.dp)) {
                PhoneNavGlyph(PhoneNavKey.BACK, Modifier.size(25.dp))
            }
            Box(Modifier.clickable { onClose() }.padding(horizontal = 28.dp)) {
                PhoneNavGlyph(PhoneNavKey.START, Modifier.size(25.dp))
            }
            Box(Modifier.clickable { page = null; filter = "" }.padding(horizontal = 28.dp)) {
                PhoneNavGlyph(PhoneNavKey.SEARCH, Modifier.size(25.dp))
            }
        }
    }
}

@Composable
private fun PhoneSettingsRow(
    title: String, subtext: String, tag: String? = null, onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .clickable(onClick = onClick).padding(vertical = 11.dp)) {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Light)
        if (subtext.isNotBlank()) Text(subtext, color = StartPersonalization.accentColor,
            fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
