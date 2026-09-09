package com.flivoro.tile8auncher.ui.dialogs

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.theme.WindowsTypography

@Composable
fun PowerDialog(
    onDismiss: () -> Unit,
    onOpenLauncherSettings: () -> Unit = {},
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RectangleShape,
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF444444)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetroIcon(glyph = "power", color = Color.White, size = 24.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Power options",
                        style = WindowsTypography.titleLarge,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                PowerOptionItem(label = "Launcher Settings (Animations)") {
                    onDismiss()
                    onOpenLauncherSettings()
                }

                PowerOptionItem(label = "System Settings") {
                    onDismiss()
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }

                PowerOptionItem(label = "Display & Sleep") {
                    onDismiss()
                    context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                }

                PowerOptionItem(label = "Battery & Power Saver") {
                    onDismiss()
                    context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
                }

                PowerOptionItem(label = "Cancel") {
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun PowerOptionItem(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Text(
            text = label,
            style = WindowsTypography.bodyMedium.copy(fontSize = 15.sp),
            color = Color.White,
        )
    }
}
