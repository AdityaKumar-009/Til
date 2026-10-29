package com.flivoro.tile8auncher.features

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import com.flivoro.tile8auncher.ui.components.metroIconBitmap
import com.flivoro.tile8auncher.ui.components.readableTileLabelColor
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A separate translucent activity preserves the app target through the system image picker. */
class AppIconPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_PACKAGE)?.takeIf(String::isNotBlank)
        if (target == null) { finish(); return }
        val label = intent.getStringExtra(EXTRA_LABEL)?.takeIf(String::isNotBlank) ?: target
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppIconPickerSheet(target, label, onClose = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "icon_package"
        private const val EXTRA_LABEL = "icon_label"
        fun forApp(context: Context, packageName: String, label: String): Intent =
            Intent(context, AppIconPickerActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName).putExtra(EXTRA_LABEL, label)
    }
}

private data class IconChoice(val id: String, val label: String, val image: ImageBitmap?)

private suspend fun iconChoices(context: Context, packageName: String): List<IconChoice> = withContext(Dispatchers.IO) {
    val repository = AppsRepository(context)
    val resolvedPackage = if (packageName == "mosaic.launcher.settings") context.packageName else packageName
    val original = repository.loadAppIcon(resolvedPackage)
    val layered = repository.loadDiscoLayeredAppIcon(resolvedPackage)
    val mono = repository.loadMonochromeAppIcon(resolvedPackage)
    val packIcon = IconPackManager.loadPackIcon(context, resolvedPackage)
    val default = when (LauncherFeatureStore.appIconStyle(context)) {
        AppIconStyle.DISCO_LAYERED -> layered
        AppIconStyle.WHITE_MONOCHROME -> mono
        AppIconStyle.ANDROID_ADAPTIVE -> original
        AppIconStyle.DEFAULT -> packIcon ?: original
    }
    buildList {
        add(IconChoice(AppIconArtwork.DEFAULT, "Default", default))
        if (original != null) add(IconChoice("original", "Original", original))
        if (layered != null) add(IconChoice("layered", "Metro", layered))
        if (packIcon != null) add(IconChoice("pack", "Icon pack", packIcon))
        if (mono != null) add(IconChoice("mono", "Monochrome", mono))
        listOf(
            "wp81_phone" to "Phone", "wp81_messages" to "Messages", "mail" to "Mail",
            "camera" to "Camera", "photos" to "Photos", "music" to "Music",
            "video" to "Video", "ie" to "Browser", "maps" to "Maps",
            "games" to "Games", "folder" to "Files", "settings" to "Settings",
        ).forEach { (glyph, label) -> add(IconChoice("glyph:$glyph", label, metroIconBitmap(context, glyph))) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppIconPickerSheet(packageName: String, appName: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode = remember { LauncherFeatureStore.launcherUiMode(context) }
    val accent = Color(StartPersonalization.wallpaperAccentArgb)
    val surface = if (mode == LauncherUiMode.DESKTOP) Color(0xFF180424) else Color(0xFF202020)
    val shape = RoundedCornerShape(6.dp)
    var choices by remember(packageName) { mutableStateOf<List<IconChoice>>(emptyList()) }
    var selected by rememberSaveable(packageName) { mutableStateOf(AppIconArtwork.selected(context, packageName)) }
    var customUri by rememberSaveable(packageName) {
        mutableStateOf(if (selected == AppIconArtwork.CUSTOM)
            LauncherFeatureStore.customIconUri(context, packageName) else null)
    }
    var pendingImage by rememberSaveable(packageName) { mutableStateOf(false) }
    var customImage by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !busy },
    )

    LaunchedEffect(packageName) {
        try { choices = iconChoices(context.applicationContext, packageName) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Couldn't load icon previews. You can still choose an image." }
    }
    LaunchedEffect(customUri) {
        val uri = customUri ?: return@LaunchedEffect
        importing = true
        try {
            val decoded = withContext(Dispatchers.IO) { IconPackManager.decodeUri(context, Uri.parse(uri), 256) }
            if (decoded == null) error = "Couldn't open that image. Choose a PNG, JPG or WebP image."
            else { customImage = decoded; if (pendingImage) selected = AppIconArtwork.CUSTOM; error = null }
        } finally { importing = false; pendingImage = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // This also lets a recreated picker decode its saved draft after process death.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingImage = true
            customUri = uri.toString()
        }
    }
    val preview = if (selected == AppIconArtwork.CUSTOM) customImage else choices.firstOrNull { it.id == selected }?.image
    fun dismiss() { scope.launch { sheetState.hide(); onClose() } }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onClose() }, sheetState = sheetState,
        containerColor = surface, contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        scrimColor = Color.Black.copy(alpha = .60f),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .84f).dp)
            .padding(horizontal = 20.dp).testTag("app-icon-bottom-sheet")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).background(accent, shape).testTag("app-icon-preview"),
                    contentAlignment = Alignment.Center) {
                    if (preview != null) Image(preview, "Selected icon preview", Modifier.size(48.dp))
                    else MetroIcon("app", size = 42.dp)
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Change icon", style = WindowsTypography.displayLarge.copy(fontSize = 27.sp))
                    Text(appName, color = Color.White.copy(alpha = .8f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    Text("Start and All Apps · all modes", color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { runCatching { picker.launch(arrayOf("image/*")) }
                    .onFailure { error = "No image picker is available on this device." } },
                    enabled = !busy && !importing, shape = shape,
                    modifier = Modifier.weight(1f).testTag("app-icon-custom")) {
                    Text(if (importing) "Opening image…" else "Choose image", color = Color.White)
                }
                TextButton(onClick = { selected = AppIconArtwork.DEFAULT; error = null }, enabled = !busy && !importing,
                    modifier = Modifier.testTag("app-icon-reset")) { Text("Reset", color = Color.White) }
            }
            if (error != null) Text(error!!, color = Color(0xFFFFB4AB), fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 6.dp).testTag("app-icon-error"))
            Text("Choose an icon", color = Color.White.copy(alpha = .72f), fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
            if (choices.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = accent)
            val allChoices = if (customImage != null) listOf(IconChoice(AppIconArtwork.CUSTOM, "Your image", customImage)) + choices else choices
            LazyVerticalGrid(columns = GridCells.Adaptive(84.dp),
                modifier = Modifier.weight(1f, fill = false).testTag("app-icon-choices"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)) {
                items(allChoices, key = { it.id }) { choice ->
                    val chosen = selected == choice.id
                    Column(Modifier.fillMaxWidth()
                        .background(if (chosen) accent.copy(alpha = .25f) else Color.White.copy(alpha = .05f), shape)
                        .border(if (chosen) 2.dp else 1.dp, if (chosen) accent else Color.White.copy(alpha = .13f), shape)
                        .selectable(selected = chosen, enabled = !busy && !importing, role = Role.RadioButton,
                            onClick = { selected = choice.id; error = null })
                        .padding(8.dp).testTag("app-icon-choice-${choice.id}"), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                            if (choice.image != null) Image(choice.image, null, Modifier.size(38.dp))
                            else MetroIcon("app", size = 38.dp)
                            if (chosen) Text("✓", color = Color.White, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopEnd))
                        }
                        Text(choice.label, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = ::dismiss, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = Color.White)
                }
                Button(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            // Finish publishing a committed choice even if the activity rotates while writing.
                            withContext(NonCancellable) {
                                withContext(Dispatchers.IO) { AppIconArtwork.apply(context, packageName, selected, preview) }
                                IconPackManager.clearCaches()
                                LauncherFeatureRuntime.notifyIconsChanged()
                            }
                            busy = false
                            dismiss()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { busy = false; error = "Couldn't save the icon. Please try again." }
                    }
                }, enabled = !busy && !importing && (selected == AppIconArtwork.DEFAULT || preview != null), shape = shape,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = readableTileLabelColor(accent)),
                    modifier = Modifier.weight(1f).testTag("app-icon-apply")) {
                    Text(if (busy) "Applying…" else "Apply")
                }
            }
        }
    }
}
