package com.flivoro.tile8auncher.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import java.util.Locale

@Composable
fun AllAppsScreen(
    sections: List<AppSection>,
    appsRepository: AppsRepository,
    onAppClick: (app: AppInfo, bounds: Rect) -> Unit,
    onAppLongClick: (app: AppInfo) -> Unit,
    onNavigateToStart: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredSections = remember(sections, searchQuery) {
        if (searchQuery.isBlank()) {
            sections
        } else {
            val query = searchQuery.trim().lowercase(Locale.getDefault())
            sections.mapNotNull { section ->
                val matchingApps = section.apps.filter { it.label.lowercase(Locale.getDefault()).contains(query) }
                if (matchingApps.isNotEmpty()) {
                    section.copy(apps = matchingApps)
                } else null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header: "Apps", sort filter, Search input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "Apps",
                        style = WindowsTypography.displayLarge.copy(fontSize = 38.sp),
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "by name ▾",
                        style = WindowsTypography.bodyMedium.copy(fontSize = 14.sp),
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                // Search Bar (top right, matching Windows 8.1 search)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(180.dp)
                        .height(34.dp)
                        .background(Color(0x33000000))
                        .border(1.dp, Color(0x66FFFFFF))
                        .padding(horizontal = 8.dp),
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search",
                                    style = WindowsTypography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                    ),
                                )
                            }
                            innerTextField()
                        },
                    )
                    MetroIcon(
                        glyph = "search",
                        color = Color.White.copy(alpha = 0.7f),
                        size = 18.dp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // A-Z Categorized Apps Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                filteredSections.forEach { section ->
                    // Section Letter Header
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = section.letter,
                            style = WindowsTypography.headlineMedium.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Normal,
                                color = WindowsColors.Magenta.toTileColor(),
                            ),
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                        )
                    }

                    // App Items in section
                    items(section.apps, key = { it.packageName + it.activityName }) { app ->
                        AppListItem(
                            app = app,
                            appsRepository = appsRepository,
                            onClick = { bounds -> onAppClick(app, bounds) },
                        ) {
                            onAppLongClick(app)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Navigation Arrow (Pointing UP to return to Start screen)
            Box(
                modifier = Modifier
                    .padding(bottom = 18.dp)
                    .size(42.dp)
                    .clickable { onNavigateToStart() },
                contentAlignment = Alignment.Center,
            ) {
                MetroIcon(
                    glyph = "arrow_up",
                    color = Color.White.copy(alpha = 0.85f),
                    size = 38.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(
    app: AppInfo,
    appsRepository: AppsRepository,
    onClick: (bounds: Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    var bounds = remember { Rect.Zero }
    val icon = remember(app.packageName) { appsRepository.getAppIcon(app.packageName) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                bounds = coordinates.boundsInWindow()
            }
            .combinedClickable(
                onClick = { onClick(bounds) },
                onLongClick = onLongClick,
            )
            .padding(vertical = 6.dp),
    ) {
        // App Icon Box (Windows 8 style square background)
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(WindowsColors.Purple.toTileColor()),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                MetroIcon(
                    glyph = "app",
                    color = Color.White,
                    size = 24.dp,
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = app.label,
            style = WindowsTypography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = Color.White,
            maxLines = 1,
        )
    }
}
