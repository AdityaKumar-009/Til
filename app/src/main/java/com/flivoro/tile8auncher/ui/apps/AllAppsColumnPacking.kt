package com.flivoro.tile8auncher.ui.apps

import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min

internal const val ALL_APPS_DEFAULT_COLUMN_WIDTH_DP = 200f
internal const val ALL_APPS_COLUMN_GAP_DP = 24f
internal const val ALL_APPS_ROW_HEIGHT_DP = 60f
internal const val ALL_APPS_ICON_BACKGROUND_DP = 48f
internal const val ALL_APPS_ICON_DP = 40f
internal const val ALL_APPS_MIN_ROWS_PER_COLUMN = 2

internal data class AllAppsColumnMetrics(
    val columnWidthDp: Float,
    val columnGapDp: Float,
    val rowHeightDp: Float,
    val rowsPerColumn: Int,
    val columnHeightDp: Float,
)

/**
 * Chooses one width for every lazy column. A phone gets the intended 200dp
 * Windows column, while an unusually narrow window clamps that width so the
 * row remains usable instead of being measured outside the viewport.
 */
internal fun calculateAllAppsColumnMetrics(
    availableWidthDp: Float,
    availableHeightDp: Float,
    desiredColumnWidthDp: Float = ALL_APPS_DEFAULT_COLUMN_WIDTH_DP,
    columnGapDp: Float = ALL_APPS_COLUMN_GAP_DP,
    rowHeightDp: Float = ALL_APPS_ROW_HEIGHT_DP,
): AllAppsColumnMetrics {
    val width = availableWidthDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val height = availableHeightDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val desiredWidth = desiredColumnWidthDp.takeIf { it.isFinite() && it > 0f }
        ?: ALL_APPS_DEFAULT_COLUMN_WIDTH_DP
    val gap = columnGapDp.takeIf { it.isFinite() && it >= 0f }
        ?: ALL_APPS_COLUMN_GAP_DP
    val rowHeight = rowHeightDp.takeIf { it.isFinite() && it >= ALL_APPS_ROW_HEIGHT_DP }
        ?: ALL_APPS_ROW_HEIGHT_DP

    val rows = floor(height / rowHeight)
        .toInt()
        .coerceAtLeast(ALL_APPS_MIN_ROWS_PER_COLUMN)

    return AllAppsColumnMetrics(
        columnWidthDp = min(desiredWidth, width).coerceAtLeast(1f),
        columnGapDp = gap,
        rowHeightDp = rowHeight,
        rowsPerColumn = rows,
        columnHeightDp = rows * rowHeight,
    )
}

internal sealed interface AllAppsColumnItem {
    val key: String
    val groupKey: String

    data class LetterHeader(
        val letter: String,
        override val groupKey: String,
    ) : AllAppsColumnItem {
        override val key: String
            get() = "header:$groupKey"
    }

    data class App(
        val app: AppInfo,
        override val groupKey: String,
    ) : AllAppsColumnItem {
        override val key: String
            get() = "app:${app.packageName}\u0000${app.activityName}:$groupKey"
    }
}

internal data class AllAppsColumnModel(
    val key: String,
    val items: List<AllAppsColumnItem>,
) {
    val firstItemKey: String
        get() = items.first().key

    val apps: List<AppInfo>
        get() = items.filterIsInstance<AllAppsColumnItem.App>().map { it.app }
}

/**
 * Packs alphabetical sections into finite-height vertical columns. A new
 * section may share the current column when at least two rows remain, which
 * gives it a header and its first app. A one-row remainder is left blank so a
 * letter header is never orphaned. Once a section continues into another
 * column, its remaining apps use every available row without repeating the
 * letter header.
 */
internal fun packAllAppsColumns(
    sections: List<AppSection>,
    rowsPerColumn: Int,
): List<AllAppsColumnModel> {
    val rows = rowsPerColumn.coerceAtLeast(ALL_APPS_MIN_ROWS_PER_COLUMN)
    val letterOccurrences = mutableMapOf<String, Int>()
    val columns = mutableListOf<AllAppsColumnModel>()
    var currentItems = mutableListOf<AllAppsColumnItem>()

    fun finishCurrentColumn() {
        if (currentItems.isEmpty()) return

        val items = currentItems.toList()
        columns += AllAppsColumnModel(
            // The first item is the column's stable identity. Appending a
            // later group to this column therefore keeps its lazy key.
            key = "all-apps-column:${items.first().key}",
            items = items,
        )
        currentItems = mutableListOf()
    }

    sections.forEach { section ->
        if (section.apps.isEmpty()) return@forEach

        val letter = section.letter.trim().ifEmpty { "#" }
        val normalizedLetter = letter.uppercase(Locale.ROOT)
        val occurrence = letterOccurrences.getOrDefault(normalizedLetter, 0)
        letterOccurrences[normalizedLetter] = occurrence + 1
        val groupKey = "$normalizedLetter:$occurrence"

        var appIndex = 0
        var isFirstColumn = true
        while (appIndex < section.apps.size) {
            // The first column needs a header and an app. A continuation
            // column needs only one app row because the letter is shown once.
            val requiredRows = if (isFirstColumn) 2 else 1
            if (currentItems.isNotEmpty() && rows - currentItems.size < requiredRows) {
                finishCurrentColumn()
            }

            if (isFirstColumn) {
                currentItems += AllAppsColumnItem.LetterHeader(
                    letter = letter,
                    groupKey = groupKey,
                )
            }

            val availableAppRows = rows - currentItems.size
            val appCount = min(availableAppRows, section.apps.size - appIndex)
            repeat(appCount) { offset ->
                currentItems += AllAppsColumnItem.App(
                    app = section.apps[appIndex + offset],
                    groupKey = groupKey,
                )
            }
            appIndex += appCount

            if (appIndex < section.apps.size) {
                // This group owns the next column, whose first row is the
                // next app. No repeated header is inserted.
                finishCurrentColumn()
                isFirstColumn = false
            }
        }
    }

    finishCurrentColumn()
    return columns
}
