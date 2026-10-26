package com.flivoro.tile8auncher.ui.phone

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.BoxWithConstraints
import com.flivoro.tile8auncher.features.LiveTileNotificationStore
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A Start Live Folder contains ordinary phone-pinned tile identities, not Android files. */
internal data class PhoneFolder(
    val id: String,
    val name: String,
    val children: List<String>,
)

internal object PhoneFolderStore {
    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE,
        )

    private fun key(mode: LauncherUiMode) =
        if (mode == LauncherUiMode.DESKTOP) "phone_DESKTOP_live_folders"
        else "phone_shared_live_folders"

    private fun migrateIfNeeded(context: Context, mode: LauncherUiMode) {
        if (mode == LauncherUiMode.DESKTOP) return
        val pref = preferences(context)
        if (pref.contains(key(mode))) return
        val current = "phone_${mode.name}_live_folders"
        val other = "phone_${if (mode == LauncherUiMode.PHONE_8) LauncherUiMode.MOBILE_10.name else LauncherUiMode.PHONE_8.name}_live_folders"
        val existing = listOf(current, other).firstOrNull(pref::contains) ?: return
        pref.edit { putString(key(mode), pref.getString(existing, "[]")) }
    }

    fun read(context: Context, mode: LauncherUiMode): List<PhoneFolder> =
        runCatching {
            migrateIfNeeded(context, mode)
            val data = JSONArray(preferences(context).getString(key(mode), "[]") ?: "[]")
            (0 until data.length()).mapNotNull { index ->
                val entry = data.optJSONObject(index) ?: return@mapNotNull null
                val id = entry.optString("id").takeIf { it.startsWith("phone_folder_") }
                    ?: return@mapNotNull null
                val childArray = entry.optJSONArray("children") ?: JSONArray()
                val children = (0 until childArray.length()).mapNotNull { i ->
                    childArray.optString(i).takeIf { it.isNotBlank() && it != id }
                }.distinct()
                PhoneFolder(id, entry.optString("name", "Folder").ifBlank { "Folder" }, children)
            }.distinctBy(PhoneFolder::id)
        }.getOrDefault(emptyList())

    fun save(context: Context, mode: LauncherUiMode, folders: List<PhoneFolder>) {
        val data = JSONArray()
        folders.forEach { folder ->
            data.put(JSONObject().apply {
                put("id", folder.id)
                put("name", folder.name)
                put("children", JSONArray().apply { folder.children.distinct().forEach(::put) })
            })
        }
        preferences(context).edit { putString(key(mode), data.toString()) }
    }

    fun createOrAdd(
        context: Context,
        mode: LauncherUiMode,
        sourceId: String,
        targetId: String,
    ): String? {
        if (sourceId.isBlank() || targetId.isBlank() || sourceId == targetId ||
            sourceId.startsWith("phone_folder_")) return null
        val folders = read(context, mode).toMutableList()
        if (folders.any { sourceId in it.children }) return null
        val targetFolder = folders.indexOfFirst { it.id == targetId }
        if (targetFolder >= 0) {
            val original = folders[targetFolder]
            folders[targetFolder] = original.copy(children = (original.children + sourceId).distinct())
            save(context, mode, folders)
            return targetId
        }
        if (targetId.startsWith("phone_folder_") ||
            folders.any { targetId in it.children }) return null
        val id = "phone_folder_${UUID.randomUUID()}"
        folders.add(PhoneFolder(id, "Folder", listOf(targetId, sourceId)))
        save(context, mode, folders)
        return id
    }

    fun rename(context: Context, mode: LauncherUiMode, folderId: String, name: String) {
        save(context, mode, read(context, mode).map {
            if (it.id == folderId) it.copy(name = name.take(48).ifBlank { "Folder" }) else it
        })
    }

    /** Removing an app unpins it from the folder; caller restores it to Start. */
    fun removeChild(
        context: Context, mode: LauncherUiMode, folderId: String, childId: String,
    ): List<String> {
        val folders = read(context, mode).toMutableList()
        val index = folders.indexOfFirst { it.id == folderId }
        if (index == -1 || childId !in folders[index].children) return emptyList()
        val remaining = folders[index].children.filterNot { it == childId }
        if (remaining.size < 2) folders.removeAt(index)
        else folders[index] = folders[index].copy(children = remaining)
        save(context, mode, folders)
        return remaining
    }

    fun dissolve(context: Context, mode: LauncherUiMode, folderId: String): List<String> {
        val folders = read(context, mode).toMutableList()
        val existing = folders.firstOrNull { it.id == folderId } ?: return emptyList()
        folders.removeAll { it.id == folderId }
        save(context, mode, folders)
        return existing.children
    }
}

/** The stored identities are unchanged; expansion is owned by the Start grid. */
internal data class PhoneFolderExpansion(
    val folder: PhoneFolder,
    val children: List<TileModel>,
    val onRename: (String) -> Unit,
    val onRemoveChild: (String) -> Unit,
    val onReorderChild: (String, String) -> Unit,
    val onDismiss: () -> Unit,
)

/** Insert below the complete row, never through a neighboring tall tile. */
internal fun phoneFolderInsertionRow(slots: List<PhoneTileSlot>, folderIndex: Int): Int {
    val folder = slots.getOrNull(folderIndex) ?: return 0
    var boundary = folder.row + folder.rows
    while (true) {
        val next = slots.filter { it.row < boundary }.maxOfOrNull { it.row + it.rows } ?: boundary
        if (next <= boundary) return boundary
        boundary = next
    }
}

@Composable
internal fun PhoneFolderHeader(expansion: PhoneFolderExpansion, enabled: Boolean) {
    var draft by remember(expansion.folder.id) { mutableStateOf(expansion.folder.name) }
    var editing by remember(expansion.folder.id) { mutableStateOf(false) }
    var hasFocused by remember(expansion.folder.id) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing && enabled) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(48) },
                enabled = enabled,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light
                ),
                modifier = Modifier.weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) hasFocused = true
                        // Initial attachment emits "not focused" before requestFocus;
                        // do not commit/close the editor until it actually had focus.
                        else if (hasFocused && editing) {
                            expansion.onRename(draft.trim().ifBlank { "Folder" })
                            editing = false
                            hasFocused = false
                        }
                    }
                    .testTag("phone-folder-title")
                    .semantics { contentDescription = "Edit folder name" },
            )
        } else {
            Text(
                text = draft.ifBlank { "Folder" },
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
                    .pointerInput(enabled, expansion.folder.id) {
                        detectTapGestures(onLongPress = {
                            if (enabled) editing = true
                        })
                    }
                    .testTag("phone-folder-title")
                    .semantics { contentDescription = "Folder name; long press to rename" },
            )
        }
        Canvas(
            modifier = Modifier.size(48.dp)
                .clickable(enabled = enabled) {
                    if (editing) {
                        expansion.onRename(draft.trim().ifBlank { "Folder" })
                        editing = false
                    }
                    expansion.onDismiss()
                }
                .semantics { contentDescription = "Collapse folder" },
        ) {
            // Crisp, density-independent Metro chevron rather than a font-dependent glyph.
            val center = Offset(size.width / 2f, size.height / 2f)
            val half = size.width * .15f
            val rise = size.height * .075f
            val path = Path().apply {
                moveTo(center.x - half, center.y + rise)
                lineTo(center.x, center.y - rise)
                lineTo(center.x + half, center.y + rise)
            }
            drawPath(path, Color.White, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
internal fun PhoneFolderPreview(
    children: List<TileModel>,
    repository: AppsRepository,
    modifier: Modifier = Modifier,
    mode: LauncherUiMode = LauncherUiMode.MOBILE_10,
) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val revision = LauncherFeatureRuntime.liveTilesRevision
    if (mode == LauncherUiMode.MOBILE_10) {
        // The W10M folder face is a miniature Live Tile grid, not a fixed
        // collection of four equal icon squares. Child tile sizes and accent
        // surfaces retain their identity across the preview and expanded view.
        val preview = children.take(8)
        val placements = remember(preview) {
            packPhoneTiles(preview.map(TileModel::size), 4)
        }
        val usedRows = (placements.maxOfOrNull { it.row + it.rows } ?: 1).coerceAtLeast(1)
        BoxWithConstraints(modifier.padding(4.dp)) {
            val unit = minOf(
                (maxWidth - 4.dp) / 4,
                (maxHeight - 4.dp) / usedRows,
            ).coerceAtLeast(1.dp)
            val tileGap = (unit * .07f).coerceAtMost(2.dp)
            val originX = (maxWidth - unit * 4) / 2
            val originY = (maxHeight - unit * usedRows) / 2
            preview.forEachIndexed { index, tile ->
                val placement = placements[index]
                val icon = rememberAppIcon(repository, tile.packageName)
                val count = if (LauncherFeatureStore.isLiveTileEnabled(context, tile.packageName))
                    LiveTileNotificationStore.latest(context, tile.packageName)?.count ?: 0 else 0
                val tileWidth = (unit * placement.columns - tileGap).coerceAtLeast(1.dp)
                val tileHeight = (unit * placement.rows - tileGap).coerceAtLeast(1.dp)
                val glyphSize = minOf(tileWidth, tileHeight) * .56f
                Box(
                    Modifier.offset(
                        x = originX + unit * placement.column,
                        y = originY + unit * placement.row,
                    ).size(width = tileWidth, height = tileHeight)
                        .background(Color(tile.colorValue)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (tile.iconGlyph.isBlank() && icon != null) {
                        Image(bitmap = icon, contentDescription = null,
                            modifier = Modifier.size(glyphSize))
                    } else {
                        MetroIcon(glyph = tile.iconGlyph.ifEmpty { "app" },
                            color = Color.White, size = glyphSize)
                    }
                    if (count > 0 && tileWidth >= 20.dp) {
                        Text(
                            if (count > 99) "99+" else count.toString(),
                            color = Color.White, fontSize = 7.sp,
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .background(Color.Black).padding(horizontal = 1.dp),
                        )
                    }
                }
            }
        }
    } else BoxWithConstraints(modifier.padding(6.dp)) {
        val side = (minOf(maxWidth, maxHeight - 14.dp) / 2 - 4.dp).coerceAtLeast(8.dp)
        Column(Modifier.align(Alignment.Center).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.Center) {
            children.take(4).chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    row.forEach { tile ->
                        val icon = rememberAppIcon(repository, tile.packageName)
                        val liveCount = if (LauncherFeatureStore.isLiveTileEnabled(context, tile.packageName))
                            LiveTileNotificationStore.latest(context, tile.packageName)?.count ?: 0 else 0
                        Box(Modifier.padding(2.dp).size(side).background(Color(tile.colorValue)),
                            contentAlignment = Alignment.Center) {
                            if (tile.iconGlyph.isBlank() && icon != null) {
                                Image(bitmap = icon, contentDescription = null,
                                    modifier = Modifier.size(side * .66f))
                            } else {
                                MetroIcon(glyph = tile.iconGlyph.ifEmpty { "app" },
                                    color = Color.White, size = side * .62f)
                            }
                            if (liveCount > 0) Text(if (liveCount > 99) "99+" else liveCount.toString(),
                                color = Color.White, fontSize = 8.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black)
                                    .padding(horizontal = 1.dp))
                        }
                    }
                }
            }
        }
    }
}
