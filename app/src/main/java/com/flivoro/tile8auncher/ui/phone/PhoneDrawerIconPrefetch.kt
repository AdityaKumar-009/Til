package com.flivoro.tile8auncher.ui.phone

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.components.preloadStartIcons
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** Warm nearby rows as the finger moves, without decoding hundreds of distant app icons. */
@Composable
internal fun PhoneDrawerIconPrefetch(
    repository: AppsRepository,
    state: LazyListState,
    groups: Map<String, List<AppInfo>>,
    mode: LauncherUiMode,
) {
    val context = LocalContext.current.applicationContext
    val revision = LauncherFeatureRuntime.iconsRevision
    val style = remember(revision) { LauncherFeatureStore.appIconStyle(context) }
    val rows = remember(groups) {
        groups.flatMap { (letter, apps) ->
            (if (letter.isEmpty()) emptyList<String?>() else listOf(null)) +
                apps.map(AppInfo::packageName)
        }
    }
    LaunchedEffect(rows, revision, style, mode) {
        withFrameNanos { }
        withFrameNanos { }
        snapshotFlow { state.firstVisibleItemIndex to state.layoutInfo.visibleItemsInfo.size }
            .distinctUntilChanged()
            .collectLatest { (first, visibleCount) ->
                val count = visibleCount.coerceAtLeast(12)
                val nearby = rows.drop((first - 2).coerceAtLeast(0))
                    .take(count * 2 + 4).filterNotNull()
                    .filterNot { it == "mosaic.launcher.settings" }
                preloadStartIcons(context, repository, nearby, style, revision,
                    normalizeWp81 = mode == LauncherUiMode.PHONE_8,
                    parallelism = 2, retainForStart = false)
            }
    }
}
