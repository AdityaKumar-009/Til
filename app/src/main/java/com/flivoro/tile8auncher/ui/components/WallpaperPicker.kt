package com.flivoro.tile8auncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.features.LauncherFeatureSettings
import com.flivoro.tile8auncher.ui.lockscreen.WindowsLockScreenPreferences

private val wallpaperNames = listOf(
    "Purple ribbons",
    "Robots",
    "Pixel city",
    "Swirls",
    "Blossom",
    "Garden",
    "Facets",
    "Night mountains",
    "Dragon",
    "Gears",
)

@Composable
internal fun WallpaperPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    appsRepository: AppsRepository? = null,
) {
    val context = LocalContext.current
    LaunchedEffect(context) { StartPersonalization.ensureLoaded(context) }

    var lockScreenEnabled by remember {
        mutableStateOf(WindowsLockScreenPreferences.isEnabled(context))
    }
    var cameraGestureEnabled by remember {
        mutableStateOf(WindowsLockScreenPreferences.isCameraGestureEnabled(context))
    }
    val backgroundColor = StartPersonalization.backgroundColor
    val accentColor = StartPersonalization.accentColor

    fun updateLockScreen(enabled: Boolean) {
        lockScreenEnabled = enabled
        WindowsLockScreenPreferences.setEnabled(context, enabled)
    }

    fun updateCameraGesture(enabled: Boolean) {
        cameraGestureEnabled = enabled
        WindowsLockScreenPreferences.setCameraGestureEnabled(context, enabled)
    }

    Text("Start background", color = Color(0xFF5133AB), fontSize = 20.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "Artwork is rendered from the same transparent vector scene used on Start. Motion Accents " +
            "wake with horizontal interaction while the background color stays independent.",
        color = Color(0xFF666666),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        wallpaperNames.indices.chunked(2).forEach { indices ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                indices.forEach { index ->
                    val motionKind = WindowsMotionAccent.kindForStyle(index)
                    Column(
                        Modifier
                            .weight(1f)
                            .selectable(
                                selected = selected == index,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) },
                            )
                            .border(
                                if (selected == index) 3.dp else 1.dp,
                                if (selected == index) Color(0xFF5133AB) else Color(0xFFCCCCCC),
                            )
                            .padding(4.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                                .background(backgroundColor),
                        ) {
                            WindowsWallpaper(
                                wallpaperStyle = index,
                                enabled = false,
                                trackLauncherScroll = false,
                            )
                        }
                        Text(
                            wallpaperNames[index],
                            color = Color(0xFF222222),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 5.dp, end = 5.dp, top = 8.dp),
                        )
                        Text(
                            if (motionKind == WindowsMotionAccentKind.NONE) "Parallax" else "Motion Accent",
                            color = if (motionKind == WindowsMotionAccentKind.NONE) {
                                Color(0xFF777777)
                            } else {
                                accentColor
                            },
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 5.dp, end = 5.dp, top = 1.dp, bottom = 7.dp),
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Text("Background color", color = Color(0xFF5133AB), fontSize = 18.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "Changes the solid Start color underneath every transparent artwork layer.",
        color = Color(0xFF666666),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(10.dp))
    PersonalizeColorGrid(
        choices = StartPersonalization.backgroundChoices,
        selected = backgroundColor,
        onSelect = { StartPersonalization.setBackgroundColor(context, it) },
    )

    Spacer(Modifier.height(20.dp))
    Text("Accent color", color = Color(0xFF5133AB), fontSize = 18.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "Recolors the artwork, shadows, highlights and Motion Accent parts without tinting the background.",
        color = Color(0xFF666666),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(10.dp))
    PersonalizeColorGrid(
        choices = StartPersonalization.accentChoices,
        selected = accentColor,
        onSelect = { StartPersonalization.setAccentColor(context, it) },
    )

    Spacer(Modifier.height(28.dp))
    Text("Lock screen", color = Color(0xFF5133AB), fontSize = 20.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "Windows 8.1-style wake surface with the original swipe directions and Metro clock/date layout.",
        color = Color(0xFF666666),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFD0D0D0))
            .background(Color.White)
            .clickable { updateLockScreen(!lockScreenEnabled) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Windows 8.1 lock screen", color = Color(0xFF222222), fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                "Show after a real screen-off/wake cycle. Swipe up or tap to enter Start.",
                color = Color(0xFF666666),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = lockScreenEnabled, onCheckedChange = ::updateLockScreen)
    }

    if (lockScreenEnabled) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFD0D0D0))
                .background(Color.White)
                .clickable { updateCameraGesture(!cameraGestureEnabled) }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Swipe down for camera", color = Color(0xFF222222), fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Use the Windows 8.1 downward lock-screen gesture to open the Android camera.",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = cameraGestureEnabled, onCheckedChange = ::updateCameraGesture)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Android's device lock remains unchanged; Mosaic's Windows surface appears within the " +
                "launcher after Android has finished its own unlock flow.",
            color = Color(0xFF777777),
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }

    LauncherFeatureSettings(appsRepository = appsRepository)
}

@Composable
private fun PersonalizeColorGrid(
    choices: List<Color>,
    selected: Color,
    onSelect: (Color) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        choices.chunked(5).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                rowColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .background(color)
                            .border(
                                width = if (color == selected) 3.dp else 1.dp,
                                color = if (color == selected) Color.White else Color(0xFFB8B8B8),
                            )
                            .selectable(
                                selected = color == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(color) },
                            ),
                    )
                }
            }
        }
    }
}
