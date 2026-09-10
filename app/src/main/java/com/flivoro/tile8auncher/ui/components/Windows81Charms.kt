package com.flivoro.tile8auncher.ui.components

import android.os.BatteryManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class Windows81CharmPane { MAIN, SEARCH, SHARE, DEVICES, SETTINGS }

@Composable
fun Windows81CharmsEdgeDetector(
    enabled: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .fillMaxHeight()
            .width(22.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag < -28f) onReveal()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { change, amount ->
                    if (abs(amount) > 0f) change.consume()
                    drag += amount
                }
            },
    )
}

@Composable
fun Windows81CharmsOverlay(
    visible: Boolean,
    requestedPane: Windows81CharmPane,
    apps: List<AppInfo>,
    appsRepository: AppsRepository,
    onDismiss: () -> Unit,
    onSearchApp: (AppInfo) -> Unit,
    onStart: () -> Unit,
    onOpenPcSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    if (!visible) return
    var pane by remember { mutableStateOf(requestedPane) }
    LaunchedEffect(visible, requestedPane) { pane = requestedPane }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.08f))
            .clickable(onClick = onDismiss),
    ) {
        CharmsClockPanel(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 32.dp))

        if (pane != Windows81CharmPane.MAIN) {
            CharmsPane(
                pane = pane,
                apps = apps,
                appsRepository = appsRepository,
                onSearchApp = onSearchApp,
                onOpenPcSettings = onOpenPcSettings,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenDevices = onOpenDevices,
                onOpenHelp = onOpenHelp,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(88.dp)
                .fillMaxHeight()
                .background(Color(0xF0121212))
                .padding(vertical = 68.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CharmButton("search", "Search", pane == Windows81CharmPane.SEARCH) { pane = Windows81CharmPane.SEARCH }
            CharmButton("share", "Share", pane == Windows81CharmPane.SHARE) { pane = Windows81CharmPane.SHARE }
            CharmButton("start", "Start", false) { onDismiss(); onStart() }
            CharmButton("devices", "Devices", pane == Windows81CharmPane.DEVICES) { pane = Windows81CharmPane.DEVICES }
            CharmButton("settings", "Settings", pane == Windows81CharmPane.SETTINGS) { pane = Windows81CharmPane.SETTINGS }
        }
    }
}

@Composable
private fun CharmButton(glyph: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) WindowsColors.Purple.toTileColor() else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (glyph) {
            "search" -> MetroIcon("search", color = Color.White, size = 30.dp)
            "settings" -> MetroIcon("settings", color = Color.White, size = 30.dp)
            "start" -> WindowsStartGlyph()
            "share" -> Text("↗", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            else -> Text("▣", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        }
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun WindowsStartGlyph() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(10.dp).background(Color.White)); Box(Modifier.size(10.dp).background(Color.White))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(10.dp).background(Color.White)); Box(Modifier.size(10.dp).background(Color.White))
        }
    }
}

@Composable
private fun CharmsPane(
    pane: Windows81CharmPane,
    apps: List<AppInfo>,
    appsRepository: AppsRepository,
    onSearchApp: (AppInfo) -> Unit,
    onOpenPcSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .width(330.dp)
            .fillMaxHeight()
            .background(Color(0xFA25102F))
            .padding(end = 88.dp, start = 18.dp, top = 34.dp, bottom = 24.dp),
    ) {
        when (pane) {
            Windows81CharmPane.SEARCH -> SearchCharm(apps, appsRepository, onSearchApp)
            Windows81CharmPane.SHARE -> ShareCharm()
            Windows81CharmPane.DEVICES -> DevicesCharm(onOpenDevices)
            Windows81CharmPane.SETTINGS -> SettingsCharm(onOpenPcSettings, onOpenNotificationSettings, onOpenHelp)
            Windows81CharmPane.MAIN -> Unit
        }
    }
}

@Composable
private fun SearchCharm(apps: List<AppInfo>, appsRepository: AppsRepository, onSearchApp: (AppInfo) -> Unit) {
    var query by remember { mutableStateOf("") }
    Text("Search", color = Color.White, style = WindowsTypography.displayLarge.copy(fontSize = 28.sp))
    Spacer(Modifier.size(14.dp))
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            cursorBrush = SolidColor(Color.Black),
            textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Search everywhere", color = Color.Gray, fontSize = 13.sp)
                inner()
            },
        )
        MetroIcon("search", color = Color(0xFF333333), size = 20.dp)
    }
    Spacer(Modifier.size(10.dp))
    Text("Everywhere", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    Spacer(Modifier.size(8.dp))
    val results = remember(apps, query) {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isBlank()) emptyList() else apps.filter {
            it.label.lowercase(Locale.getDefault()).contains(q)
        }.take(8)
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        results.forEach { app ->
            val icon = rememberAppIcon(appsRepository, app.packageName)
            Row(
                Modifier.fillMaxWidth().clickable { onSearchApp(app) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).background(WindowsColors.Purple.toTileColor()),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) Image(icon, contentDescription = app.label, modifier = Modifier.size(27.dp))
                    else MetroIcon("app", color = Color.White, size = 22.dp)
                }
                Spacer(Modifier.size(9.dp))
                Text(app.label, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ShareCharm() {
    Text("Share", color = Color.White, style = WindowsTypography.displayLarge.copy(fontSize = 28.sp))
    Spacer(Modifier.size(22.dp))
    Text("There's nothing to share from Start.", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
}

@Composable
private fun DevicesCharm(onOpenDevices: () -> Unit) {
    Text("Devices", color = Color.White, style = WindowsTypography.displayLarge.copy(fontSize = 28.sp))
    Spacer(Modifier.size(18.dp))
    CharmPaneItem("Play", "Send media to a connected Android device", onOpenDevices)
    CharmPaneItem("Project", "Open connected-device settings", onOpenDevices)
}

@Composable
private fun SettingsCharm(onOpenPcSettings: () -> Unit, onOpenNotificationSettings: () -> Unit, onOpenHelp: () -> Unit) {
    Text("Settings", color = Color.White, style = WindowsTypography.displayLarge.copy(fontSize = 28.sp))
    Spacer(Modifier.size(18.dp))
    CharmPaneItem("Personalize", "Start colors and backgrounds", onOpenPcSettings)
    CharmPaneItem("PC settings", "Tile8 launcher settings", onOpenPcSettings)
    CharmPaneItem("Notifications", "Enable notification-backed live tiles", onOpenNotificationSettings)
    CharmPaneItem("Help", "Windows 8.1-style help and tips", onOpenHelp)
}

@Composable
private fun CharmPaneItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp)) {
        Text(title, color = Color.White, fontSize = 14.sp)
        Text(subtitle, color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp, maxLines = 2)
    }
}

@Composable
private fun CharmsClockPanel(modifier: Modifier) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMMM d", Locale.getDefault()) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000L)
        }
    }
    val battery = remember(now) {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.coerceIn(0, 100) ?: 0
    }
    Row(
        modifier.background(Color(0xCB111111)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(timeFormat.format(now), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(dayFormat.format(now), color = Color.White, fontSize = 12.sp)
            Text(dateFormat.format(now), color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
            Text("Battery $battery%", color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
        }
    }
}
