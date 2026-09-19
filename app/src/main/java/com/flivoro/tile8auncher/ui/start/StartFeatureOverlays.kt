package com.flivoro.tile8auncher.ui.start

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsTypography

@Composable
internal fun StartFolderDialog(
    tile: TileModel,
    appsRepository: AppsRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val packages = remember(tile.id) { LauncherFeatureStore.folderPackages(context, tile.id) }
    val installed = remember(packages) {
        val all = runCatching { appsRepository.getInstalledApps() }.getOrDefault(emptyList())
        packages.mapNotNull { pkg -> all.firstOrNull { it.packageName == pkg } }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xF0180424))
                .padding(18.dp),
        ) {
            Text(
                tile.title,
                color = Color.White,
                style = WindowsTypography.headlineMedium.copy(fontSize = 25.sp),
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(installed, key = { it.packageName }) { app ->
                    val icon = rememberAppIcon(appsRepository, app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(intent) }
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(38.dp).background(Color(0xFF6B4AA5)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (icon != null) Image(icon, app.label, Modifier.size(28.dp))
                            else MetroIcon("app", color = Color.White, size = 23.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            app.label,
                            color = Color.White,
                            style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun StartGroupNameDialog(
    title: String,
    initialValue: String,
    suggestions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xF0180424))
                .padding(18.dp),
        ) {
            Text(title, color = Color.White, style = WindowsTypography.titleLarge.copy(fontSize = 19.sp))
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = value,
                onValueChange = { value = it.take(40) },
                singleLine = true,
                textStyle = TextStyle(color = Color.Black, fontSize = 15.sp),
                cursorBrush = SolidColor(Color(0xFF5133AB)),
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(11.dp),
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestions.distinct().take(8).forEach { group ->
                        Text(
                            group,
                            color = Color.White,
                            style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { value = group }
                                .padding(vertical = 7.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Cancel",
                    color = Color.White,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "OK",
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xFF5133AB))
                        .clickable {
                            // Windows 8.1 groups may intentionally have no visible name.
                            onConfirm(value.trim())
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}
