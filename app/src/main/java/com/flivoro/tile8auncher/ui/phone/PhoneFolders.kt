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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(value = expansion.folder.name, onValueChange = expansion.onRename,
            enabled = enabled, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White,
                fontSize = 22.sp, fontWeight = FontWeight.Light),
            modifier = Modifier.weight(1f).testTag("phone-folder-title")
                .semantics { contentDescription = "Folder name" })
        Text("⌃", color = Color.White, fontSize = 28.sp,
            modifier = Modifier.size(48.dp).clickable(enabled = enabled, onClick = expansion.onDismiss)
                .semantics { contentDescription = "Collapse folder" }
                .padding(start = 13.dp, top = 6.dp))
    }
}

@Composable
internal fun PhoneFolderPreview(
    children: List<TileModel>,
    repository: AppsRepository,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        children.take(4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                row.forEach { tile ->
                    val icon = rememberAppIcon(repository, tile.packageName)
                    Box(
                        Modifier.padding(2.dp).size(27.dp)
                            .background(Color(tile.colorValue)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tile.iconGlyph.isBlank() && icon != null) {
                            Image(bitmap = icon, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                        } else {
                            MetroIcon(glyph = tile.iconGlyph.ifEmpty { "app" },
                                color = Color.White, size = 17.dp)
                        }
                    }
                }
            }
        }
    }
}
