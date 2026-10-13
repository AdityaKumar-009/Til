package com.flivoro.tile8auncher.ui.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.components.StartPersonalization

/** Context command strip attached to a Windows Phone All Apps row, not an Android dialog. */
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
    val accent = StartPersonalization.accentColor

    fun launchIntent(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Popup(
        alignment = androidx.compose.ui.Alignment.TopStart,
        offset = IntOffset(xPx.coerceAtLeast(0), yPx.coerceAtLeast(0)),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(Modifier.width(216.dp)
            .background(Color(0xFF202020))
            .border(1.dp, Color(0xFF383838))
            .testTag("phone-lumia-app-context-menu")
            .padding(vertical = 6.dp)) {
            @Composable
            fun item(title: String, action: () -> Unit) {
                Text(title,
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.clickable {
                        onDismiss()
                        action()
                    }.padding(horizontal = 20.dp, vertical = 11.dp)
                        .testTag("phone-app-command-${title.replace(' ', '-')}"))
            }
            item(if (alreadyPinned) "unpin from start" else "pin to start", onPinChanged)
            if (packageName != "mosaic.launcher.settings") {
                if (mode == LauncherUiMode.PHONE_8) {
                    item("rate and review") {
                        val market = Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=$packageName"))
                        if (market.resolveActivity(context.packageManager) == null) {
                            launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://play.google.com/store/apps/details?id=$packageName",
                            )))
                        } else launchIntent(market)
                    }
                }
                item("share") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT,
                            "https://play.google.com/store/apps/details?id=$packageName")
                    }
                    launchIntent(Intent.createChooser(send, "Share app"))
                }
                item("uninstall") {
                    launchIntent(Intent(Intent.ACTION_DELETE,
                        Uri.parse("package:$packageName")))
                }
                item("app info", onAppInfo)
            } else {
                item("Mosaic Settings", onAppInfo)
            }
        }
    }
}
