package com.flivoro.tile8auncher.ui.phone

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.flivoro.tile8auncher.features.LauncherUiMode

/**
 * Disco-style full-width light command strip in WP8.1; retain W10M commands.
 * The held row is highlighted by PhoneLauncherSurface while the menu unfolds.
 */
@Composable
internal fun PhoneAppContextMenu(
    mode: LauncherUiMode,
    packageName: String,
    alreadyPinned: Boolean,
    xPx: Int,
    yPx: Int,
    onPinChanged: () -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val classic = mode == LauncherUiMode.PHONE_8
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val unfold = remember(packageName, mode) { Animatable(if (classic) 0f else 1f) }
    LaunchedEffect(packageName, mode) {
        if (classic) {
            // Disco's context strip reveals its line before the panel expands,
            // reaching its final pose after about 750ms.
            kotlinx.coroutines.delay(190)
            unfold.animateTo(1f, tween(560, easing = CubicBezierEasing(.075f, .82f, .165f, 1f)))
        } else unfold.snapTo(1f)
    }
    fun launchIntent(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(if (classic) 0 else xPx.coerceAtLeast(0), yPx.coerceAtLeast(0)),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier.width(if (classic) screenWidth.dp else 216.dp)
                .graphicsLayer {
                    if (classic) {
                        alpha = unfold.value
                        scaleY = .04f + .96f * unfold.value
                        transformOrigin = TransformOrigin(.5f, 0f)
                    }
                }
                .background(if (classic) Color.White else Color(0xFF202020))
                .then(if (classic) Modifier else Modifier.border(1.dp, Color(0xFF383838)))
                .testTag("phone-lumia-app-context-menu")
                .padding(vertical = if (classic) 10.dp else 6.dp),
        ) {
            @Composable
            fun item(title: String, enabled: Boolean = true, action: () -> Unit) {
                Text(
                    title,
                    color = if (classic) {
                        if (enabled) Color.Black else Color(0xFF949494)
                    } else Color.White,
                    fontSize = if (classic) 24.sp else 17.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier
                        .clickable(enabled = enabled && unfold.value >= .96f) {
                            onDismiss()
                            action()
                        }
                        .padding(horizontal = if (classic) 20.dp else 20.dp,
                            vertical = if (classic) 12.dp else 11.dp)
                        .testTag("phone-app-command-${title.replace(' ', '-')}"),
                )
            }
            if (classic) {
                // Disco disables Pin when the app is already on Start. It does
                // not mix the modern Android Share/Rate commands into this strip.
                item("pin to start", enabled = !alreadyPinned, action = onPinChanged)
                if (packageName != "mosaic.launcher.settings") {
                    item("app info", action = onAppInfo)
                    item("uninstall") {
                        launchIntent(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
                    }
                } else item("Mosaic Settings", action = onAppInfo)
            } else {
                item(if (alreadyPinned) "unpin from start" else "pin to start", action = onPinChanged)
                if (packageName != "mosaic.launcher.settings") {
                    item("share") {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=$packageName")
                        }
                        launchIntent(Intent.createChooser(send, "Share app"))
                    }
                    item("uninstall") {
                        launchIntent(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
                    }
                    item("app info", action = onAppInfo)
                } else item("Mosaic Settings", action = onAppInfo)
            }
        }
    }
}
