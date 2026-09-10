package com.flivoro.tile8auncher.ui.start

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compose exposes RowScope/ColumnScope AnimatedVisibility overloads, but not a BoxScope overload.
 * Start has nested Boxes inside a Column; without this nearest-receiver overload Kotlin can select
 * the hidden outer ColumnScope overload and reject the otherwise valid call. This wrapper delegates
 * to the same top-level Compose animation and changes no timing or visual behavior.
 */
@Composable
internal fun BoxScope.AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}
