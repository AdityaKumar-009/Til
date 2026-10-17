package com.flivoro.tile8auncher.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Same case-insensitive colored match used by classic WP8.1 All Apps.
 * The displayed label remains intact; only its first matched query range is tinted.
 */
internal fun highlightedAppLabel(label: String, query: String, accent: Color): AnnotatedString {
    val term = query.trim()
    if (term.isEmpty()) return AnnotatedString(label)
    val index = label.indexOf(term, ignoreCase = true)
    if (index < 0) return AnnotatedString(label)
    return buildAnnotatedString {
        append(label.substring(0, index))
        withStyle(SpanStyle(color = accent)) {
            append(label.substring(index, index + term.length))
        }
        append(label.substring(index + term.length))
    }
}
