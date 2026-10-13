package com.flivoro.tile8auncher.ui.phone

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.flivoro.tile8auncher.features.LauncherUiMode

/**
 * WP8.1 matches Disco's appList.scss:
 * - 219px white menu, 12px outer padding, three 65px rows, 30px light text.
 * - 750ms intro: pointer-anchored white 2px line for 0..50%;
 *   panel folds open after 51% with cubic-bezier(.075,.82,.165,1).
 * W10M retains its existing compact dark Android command strip.
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
    val screenWidthPx = with(LocalDensity.current) { screenWidth.dp.toPx() }.coerceAtLeast(1f)
    val pointerFraction = (xPx / screenWidthPx).coerceIn(0f, 1f)
    // Disco uses fixed CSS pixels for commands rather than scaling them with tile size.
    val scale = 1f
    val introduction = remember(packageName, mode) { Animatable(if (classic) 0f else 1f) }
    LaunchedEffect(packageName, mode) {
        if (classic) introduction.animateTo(1f, tween(750, easing = LinearEasing))
        else introduction.snapTo(1f)
    }
    fun launchIntent(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    @Composable
    fun item(title: String, enabled: Boolean = true, action: () -> Unit) {
        Text(
            title,
            color = if (classic) {
                if (enabled) Color.Black else Color(0xFF999999)
            } else Color.White,
            fontSize = if (classic) (30f * scale).sp else 17.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier
                .then(if (classic) Modifier.fillMaxWidth().height((65f * scale).dp) else Modifier)
                .clickable(enabled = enabled && introduction.value >= (if (classic) .9f else 0f)) {
                    onDismiss()
                    action()
                }
                .padding(horizontal = if (classic) (12f * scale).dp else 20.dp,
                    vertical = if (classic) (12f * scale).dp else 11.dp)
                .testTag("phone-app-command-${title.replace(' ', '-')}"),
        )
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(if (classic) 0 else xPx.coerceAtLeast(0), yPx.coerceAtLeast(0)),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        if (classic) {
            val phase = introduction.value
            val fold = if (phase <= .51f) 0f else
                CubicBezierEasing(.075f, .82f, .165f, 1f)
                    .transform(((phase - .51f) / .49f).coerceIn(0f, 1f))
            Box(
                Modifier.width(screenWidth.dp)
                    .height((219f * scale).dp)
                    .testTag("phone-lumia-app-context-menu"),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .height((219f * scale).dp)
                        .graphicsLayer {
                            scaleY = fold.coerceAtLeast(.001f)
                            alpha = if (phase >= .51f) 1f else 0f
                            transformOrigin = TransformOrigin(.5f, 0f)
                        }
                        .background(Color.White)
                        .clipToBounds()
                        .padding((12f * scale).dp),
                ) {
                    // Disco disables pin when it is already present; never unpin
                    // from the All Apps context menu.
                    item("pin to start", enabled = !alreadyPinned, action = onPinChanged)
                    if (packageName != "mosaic.launcher.settings") {
                        item("app info", action = onAppInfo)
                        item("uninstall") {
                            launchIntent(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
                        }
                    } else item("Mosaic Settings", action = onAppInfo)
                }
                Box(
                    Modifier.fillMaxWidth().height((2f * scale).dp)
                        .graphicsLayer {
                            scaleX = (phase / .5f).coerceIn(.001f, 1f)
                            alpha = if (phase < .55f) 1f else 0f
                            transformOrigin = TransformOrigin(pointerFraction, 0f)
                        }
                        .background(Color.White)
                )
            }
        } else {
            Column(
                Modifier.width(216.dp).background(Color(0xFF202020))
                    .border(1.dp, Color(0xFF383838))
                    .padding(vertical = 6.dp)
                    .testTag("phone-lumia-app-context-menu"),
            ) {
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
