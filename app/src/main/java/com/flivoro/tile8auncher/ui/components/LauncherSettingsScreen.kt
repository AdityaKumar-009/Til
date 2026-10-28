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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flivoro.tile8auncher.features.DefaultHomeApp
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode

private val ink = Color(0xFF18233B)
private val muted = Color(0xFF596579)
private val accent = Color(0xFF5633AE)
private val page = Color(0xFFF5F6FA)

/** One settings destination, shared by Desktop, WP8.1 and Windows 10 Mobile. */
@Composable
internal fun LauncherSettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val modeRevision = LauncherFeatureRuntime.launcherModeRevision
    val selectedMode = remember(modeRevision) { LauncherFeatureStore.launcherUiMode(context) }
    var isDefault by remember { mutableStateOf(DefaultHomeApp.isSelected(context)) }
    val defaultPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { isDefault = DefaultHomeApp.isSelected(context) }

    // The user may change the Home app in Android Settings instead of the role dialog.
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isDefault = DefaultHomeApp.isSelected(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = onClose)

    Box(
        Modifier.fillMaxSize().background(page).statusBarsPadding().navigationBarsPadding()
            .testTag("til-settings-screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 620.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClose)
                        .testTag("launcher-settings-back"),
                    contentAlignment = Alignment.CenterStart,
                ) { Text("←", fontSize = 27.sp, color = ink) }
                Text("TIL  /  SETTINGS", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = accent, letterSpacing = 1.2.sp)
            }
            Spacer(Modifier.height(22.dp))
            Text("Make TIL yours.", fontSize = 32.sp, lineHeight = 37.sp,
                fontWeight = FontWeight.SemiBold, color = ink)
            Spacer(Modifier.height(9.dp))
            Text("Two simple choices to get started. You can change them anytime.",
                fontSize = 14.sp, lineHeight = 21.sp, color = muted)

            Spacer(Modifier.height(28.dp))
            Text("01  ·  HOME APP", color = accent, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(11.dp))
            Column(
                Modifier.fillMaxWidth().background(Color.White)
                    .border(1.dp, Color(0xFFE3E5EF)).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(Color(0xFFECE6FA)),
                        contentAlignment = Alignment.Center) {
                        MetroIcon("desktop", color = accent, size = 27.dp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Default launcher", fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold, color = ink)
                        Spacer(Modifier.height(3.dp))
                        Text(if (isDefault) "✓  TIL is your Home app" else "Not set yet",
                            color = if (isDefault) Color(0xFF177A4D) else Color(0xFF996414),
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("launcher-default-home-status"))
                    }
                }
                Spacer(Modifier.height(15.dp))
                Text(
                    if (isDefault) "Pressing Home now takes you to TIL."
                    else "Make TIL appear when you swipe Home or press the Home button. Android will ask you to confirm.",
                    color = muted, fontSize = 13.sp, lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth().background(accent).clickable(role = Role.Button) {
                        try {
                            defaultPicker.launch(DefaultHomeApp.selectionIntent(context, isDefault))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "Home app settings are not available on this device",
                                Toast.LENGTH_LONG).show()
                        }
                    }.testTag("launcher-default-home-action")
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (isDefault) "Manage Home app" else "Set TIL as default",
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(31.dp))
            Text("02  ·  LOOK & FEEL", color = accent, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(5.dp))
            Text("Choose your Start screen", fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold, color = ink)
            Spacer(Modifier.height(5.dp))
            Text("Try any style. Your tiles and settings are saved.",
                color = muted, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                LauncherUiMode.entries.forEach { mode ->
                    val selected = selectedMode == mode
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (selected) Color(0xFFF0EBFC) else Color.White)
                            .border(if (selected) 2.dp else 1.dp,
                                if (selected) accent else Color(0xFFE3E5EF))
                            .selectable(selected = selected, role = Role.RadioButton) {
                                LauncherFeatureStore.setLauncherUiMode(
                                    context, mode,
                                    phoneRenderAttempted = mode != LauncherUiMode.DESKTOP,
                                )
                            }
                            .testTag("launcher-mode-${mode.name}")
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(42.dp)
                            .background(if (selected) accent else Color(0xFFECEEF5)),
                            contentAlignment = Alignment.Center) {
                            MetroIcon(if (mode == LauncherUiMode.DESKTOP) "desktop" else "app",
                                color = if (selected) Color.White else accent, size = 24.dp)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(when (mode) {
                                LauncherUiMode.DESKTOP -> "Windows 8.1 Desktop"
                                LauncherUiMode.PHONE_8 -> "Windows Phone 8.1"
                                LauncherUiMode.MOBILE_10 -> "Windows 10 Mobile"
                            }, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(when (mode) {
                                LauncherUiMode.DESKTOP -> "Wide, horizontal Start screen"
                                LauncherUiMode.PHONE_8 -> "Classic Lumia-style phone tiles"
                                LauncherUiMode.MOBILE_10 -> "Modern phone Start with live tiles"
                            }, color = muted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(23.dp).border(2.dp, if (selected) accent else Color(0xFFA2A8B9)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Text("✓", color = accent, fontSize = 15.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            Text("That's it. Go back to Start and enjoy your launcher.",
                color = muted, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(20.dp))
        }
    }
}
