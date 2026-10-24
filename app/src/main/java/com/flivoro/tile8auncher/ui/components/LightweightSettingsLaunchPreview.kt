package com.flivoro.tile8auncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.ui.theme.WindowsTypography

/**
 * First-frame shell for a borrowed Desktop launch of an internal Settings tile.
 *
 * The real WindowsAppView/PCSettingsAppContent builds previews, feature settings,
 * scroll states and several preference readers. Running all of them INSIDE each
 * transformed destination frame is expensive on phones. Only this immutable,
 * inexpensive shell turns with the Desktop animation; the actual Settings UI
 * mounts once the forward flip has completed.
 */
@Composable
internal fun LightweightSettingsLaunchPreview(tile: TileModel) {
    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Color(0xFFF0F0F0)),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp)
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroIcon(
                    glyph = tile.iconGlyph.ifEmpty { "settings" },
                    color = Color.White,
                    size = 20.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    tile.title,
                    style = WindowsTypography.titleMedium.copy(fontSize = 14.sp),
                    color = Color.White,
                )
            }
            Text("✕", color = Color.White, fontSize = 16.sp)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp)) {
            Text(
                "Mosaic Settings",
                style = WindowsTypography.displayLarge.copy(
                    color = Color(0xFF222222), fontSize = 30.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Personalize your Start screen, adjust motion, or manage launcher features.",
                color = Color(0xFF666666), fontSize = 13.sp, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("Launcher", "Appearance", "Motion").forEachIndexed { index, label ->
                    Text(
                        label,
                        color = if (index == 0) Color.White else Color(0xFF5133AB),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(if (index == 0) Color(0xFF5133AB) else Color.White)
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                    )
                }
            }
        }
    }
}
