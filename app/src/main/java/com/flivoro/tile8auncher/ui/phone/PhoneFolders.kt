package com.flivoro.tile8auncher.ui.phone

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.components.MetroIcon
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

    private fun key(mode: LauncherUiMode) = "phone_${mode.name}_live_folders"

    fun read(context: Context, mode: LauncherUiMode): List<PhoneFolder> =
        runCatching {
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

/** Real WP8.1 Update / Windows 10 Mobile-style expanded Start folder surface. */
@Composable
internal fun PhoneFolderSurface(
    folder: PhoneFolder,
    children: List<TileModel>,
    onOpenChild: (String) -> Unit,
    onRemoveChild: (String) -> Unit,
    onRename: (String) -> Unit,
    onUngroup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = StartPersonalization.accentColor
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .94f))
                .clickable(onClick = onDismiss).testTag("phone-expanded-folder"),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(Color(0xFF161616))
                    .clickable(enabled = false) {}
                    .padding(horizontal = 16.dp, vertical = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = folder.name, onValueChange = onRename, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Light,
                        ),
                        modifier = Modifier.weight(1f).testTag("phone-folder-title"),
                    )
                    Text("×", color = Color.White, fontSize = 29.sp,
                        modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                }
                Spacer(Modifier.height(18.dp))
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    children.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { child ->
                                Column(Modifier.weight(1f)) {
                                    Box(
                                        Modifier.fillMaxWidth().height(91.dp)
                                            .background(Color(child.colorValue))
                                            .clickable { onOpenChild(child.id) }
                                            .testTag("phone-folder-app-${child.id}"),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        MetroIcon(
                                            glyph = child.iconGlyph.ifEmpty { "app" },
                                            color = Color.White, size = 35.dp,
                                        )
                                    }
                                    Text(child.title, color = Color.White, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("remove", color = accent, fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            onRemoveChild(child.id)
                                        }.padding(vertical = 4.dp))
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Ungroup tiles", color = accent, fontSize = 16.sp,
                    modifier = Modifier.clickable(onClick = onUngroup)
                        .padding(vertical = 10.dp).testTag("phone-folder-ungroup"))
            }
        }
    }
}

@Composable
internal fun PhoneFolderPreview(
    children: List<TileModel>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        children.take(4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                row.forEach { tile ->
                    Box(
                        Modifier.padding(2.dp).size(27.dp)
                            .background(Color(tile.colorValue)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroIcon(glyph = tile.iconGlyph.ifEmpty { "app" }, color = Color.White, size = 17.dp)
                    }
                }
            }
        }
    }
}
