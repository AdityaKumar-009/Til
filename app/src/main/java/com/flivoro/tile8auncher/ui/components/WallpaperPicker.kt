package com.flivoro.tile8auncher.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val wallpaperNames = listOf("Purple ribbons", "Robots", "Pixel city", "Swirls", "Blossom", "Garden", "Facets", "Night mountains", "Dragon", "Gears")
private val wallpaperResources = listOf(0, R.drawable.start_wallpaper_1, R.drawable.start_wallpaper_2,
    R.drawable.start_wallpaper_3, R.drawable.start_wallpaper_4, R.drawable.start_wallpaper_5,
    R.drawable.start_wallpaper_6, R.drawable.start_wallpaper_7, R.drawable.start_wallpaper_8,
    R.drawable.start_wallpaper_9)

@Composable
internal fun WallpaperPicker(selected: Int, onSelect: (Int) -> Unit) {
    val resources = LocalContext.current.resources
    Text("Start background", color = Color(0xFF5133AB), fontSize = 20.sp)
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        wallpaperNames.indices.chunked(2).forEach { indices ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                indices.forEach { index ->
                    val preview by produceState<ImageBitmap?>(null, index) {
                        if (index > 0) value = withContext(Dispatchers.IO) {
                            BitmapFactory.decodeResource(resources, wallpaperResources[index],
                                BitmapFactory.Options().apply { inSampleSize = 8; inScaled = false })?.asImageBitmap()
                        }
                    }
                    Column(Modifier.weight(1f)
                        .selectable(selected == index, role = Role.RadioButton, onClick = { onSelect(index) })
                        .border(if (selected == index) 3.dp else 1.dp,
                            if (selected == index) Color(0xFF5133AB) else Color(0xFFCCCCCC))
                        .padding(4.dp)) {
                        Box(Modifier.fillMaxWidth().height(86.dp).background(Color(0xFF23053D))) {
                            if (index == 0) WindowsWallpaper(enabled = false)
                            else preview?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        }
                        Text(wallpaperNames[index], color = Color(0xFF222222), fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}
