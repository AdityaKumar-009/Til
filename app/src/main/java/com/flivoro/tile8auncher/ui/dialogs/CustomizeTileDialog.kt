package com.flivoro.tile8auncher.ui.dialogs

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.IconPackManager
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor

@Composable
fun CustomizeTileDialog(
    tile: TileModel,
    onDismiss: () -> Unit,
    onResize: (TileSize) -> Unit,
    onColorChange: (Long) -> Unit,
    onUnpin: () -> Unit,
) {
    val context = LocalContext.current
    var liveTileEnabled by remember(tile.packageName) {
        mutableStateOf(
            tile.packageName?.let { LauncherFeatureStore.isLiveTileEnabled(context, it) } ?: false,
        )
    }
    var customIconSet by remember(tile.packageName) {
        mutableStateOf(
            tile.packageName?.let { LauncherFeatureStore.customIconUri(context, it) != null } ?: false,
        )
    }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val packageName = tile.packageName ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            LauncherFeatureStore.setCustomIconUri(context, packageName, uri.toString())
            IconPackManager.clearCaches()
            customIconSet = true
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RectangleShape,
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF444444)),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = tile.title,
                    style = WindowsTypography.titleLarge,
                    color = Color.White,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Resize Tile",
                    style = WindowsTypography.labelSmall.copy(fontSize = 12.sp),
                    color = Color.LightGray,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val sizes = listOf(TileSize.SMALL, TileSize.MEDIUM, TileSize.WIDE, TileSize.LARGE)
                    sizes.forEach { size ->
                        val isSelected = tile.size == size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(if (isSelected) WindowsColors.MailBlue.toTileColor() else Color(0xFF333333))
                                .clickable { onResize(size) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = size.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = WindowsTypography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Tile Color",
                    style = WindowsTypography.labelSmall.copy(fontSize = 12.sp),
                    color = Color.LightGray,
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(WindowsColors.ColorOptions) { colorLong ->
                        val isSelected = tile.colorValue == colorLong
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(colorLong.toTileColor())
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                )
                                .clickable { onColorChange(colorLong) },
                        )
                    }
                }

                if (!tile.packageName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                liveTileEnabled = !liveTileEnabled
                                LauncherFeatureStore.setLiveTileEnabled(
                                    context,
                                    tile.packageName,
                                    liveTileEnabled,
                                )
                            }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Live tile",
                                style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.White,
                            )
                            Text(
                                text = "Use Android notifications as Windows-style upward live updates.",
                                style = WindowsTypography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.LightGray,
                            )
                        }
                        Switch(
                            checked = liveTileEnabled,
                            onCheckedChange = { checked ->
                                liveTileEnabled = checked
                                LauncherFeatureStore.setLiveTileEnabled(context, tile.packageName, checked)
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { iconPicker.launch(arrayOf("image/*")) },
                            shape = RectangleShape,
                        ) {
                            Text(
                                if (customIconSet) "Change custom icon" else "Choose custom icon",
                                style = WindowsTypography.labelSmall,
                            )
                        }
                        if (customIconSet) {
                            OutlinedButton(
                                onClick = {
                                    LauncherFeatureStore.setCustomIconUri(context, tile.packageName, null)
                                    IconPackManager.clearCaches()
                                    customIconSet = false
                                },
                                shape = RectangleShape,
                            ) {
                                Text("Reset icon", style = WindowsTypography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onUnpin,
                        shape = RectangleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WindowsColors.Red.toTileColor()),
                    ) {
                        Text(text = "Unpin from Start", style = WindowsTypography.labelSmall)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onDismiss,
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = WindowsColors.MailBlue.toTileColor()),
                    ) {
                        Text(text = "Done", style = WindowsTypography.labelSmall)
                    }
                }
            }
        }
    }
}
