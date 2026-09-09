package com.flivoro.tile8auncher.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import java.util.Locale

@Composable
fun PinAppsDialog(
    installedApps: List<AppInfo>,
    pinnedTiles: List<TileModel>,
    appsRepository: AppsRepository,
    onDismiss: () -> Unit,
    onTogglePin: (app: AppInfo, isPinned: Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val pinnedPackageSet = remember(pinnedTiles) {
        pinnedTiles.mapNotNull { it.packageName }.toSet()
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            installedApps.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RectangleShape,
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .fillMaxSize(0.92f)
                .border(1.dp, Color(0xFF444444))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pin Apps to Start",
                        style = WindowsTypography.headlineMedium.copy(fontSize = 24.sp),
                        color = Color.White
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Box
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Color(0xFF111111))
                        .border(1.dp, Color(0xFF555555))
                        .padding(horizontal = 10.dp)
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search installed apps...",
                                    style = WindowsTypography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            text = "✕",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { searchQuery = "" }
                                .padding(horizontal = 4.dp)
                        )
                    } else {
                        MetroIcon(
                            glyph = "search",
                            color = Color.White.copy(alpha = 0.7f),
                            size = 18.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // App list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredApps, key = { "${it.packageName}\u0000${it.activityName}" }) { app ->
                        val isPinned = pinnedPackageSet.contains(app.packageName)
                        val icon = rememberAppIcon(appsRepository, app.packageName)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isPinned) Color(0xFF2A2A2A) else Color(0xFF252525))
                                .border(
                                    width = 1.dp,
                                    color = if (isPinned) WindowsColors.MailBlue.toTileColor().copy(alpha = 0.6f) else Color(0xFF333333)
                                )
                                .clickable {
                                    onTogglePin(app, isPinned)
                                    val msg = if (isPinned) "Unpinned ${app.label} from Start" else "Pinned ${app.label} to Start"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon Box
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(WindowsColors.Purple.toTileColor()),
                                contentAlignment = Alignment.Center
                            ) {
                                if (icon != null) {
                                    Image(
                                        bitmap = icon,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(26.dp)
                                    )
                                } else {
                                    MetroIcon(glyph = "app", color = Color.White, size = 22.dp)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = app.label,
                                style = WindowsTypography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )

                            // Pin / Unpin Badge
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isPinned) WindowsColors.Red.toTileColor().copy(alpha = 0.85f)
                                        else WindowsColors.MailBlue.toTileColor()
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isPinned) "Unpin" else "Pin",
                                    style = WindowsTypography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Done Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = WindowsColors.MailBlue.toTileColor())
                    ) {
                        Text(text = "Done", style = WindowsTypography.labelSmall)
                    }
                }
            }
        }
    }
}
