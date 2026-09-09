package com.flivoro.tile8auncher.ui.apps

import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllAppsColumnPackingTest {

    @Test
    fun metricsKeepEqualConstrainedColumnsAndMinimumRows() {
        val phone = calculateAllAppsColumnMetrics(
            availableWidthDp = 312f,
            availableHeightDp = 432f,
        )
        val narrow = calculateAllAppsColumnMetrics(
            availableWidthDp = 144f,
            availableHeightDp = 140f,
        )

        assertEquals(ALL_APPS_DEFAULT_COLUMN_WIDTH_DP, phone.columnWidthDp, 0.001f)
        assertEquals(60f, ALL_APPS_ROW_HEIGHT_DP, 0.001f)
        assertEquals(ALL_APPS_ROW_HEIGHT_DP, phone.rowHeightDp, 0.001f)
        assertEquals(7, phone.rowsPerColumn)
        assertTrue(phone.columnHeightDp <= 432f)
        assertEquals(48f, ALL_APPS_ICON_BACKGROUND_DP, 0.001f)
        assertEquals(40f, ALL_APPS_ICON_DP, 0.001f)

        assertEquals(144f, narrow.columnWidthDp, 0.001f)
        assertTrue(narrow.rowsPerColumn >= ALL_APPS_MIN_ROWS_PER_COLUMN)
        assertTrue(narrow.rowHeightDp >= ALL_APPS_ROW_HEIGHT_DP)
    }

    @Test
    fun invalidMetricsUseFiniteSafeDefaults() {
        val metrics = calculateAllAppsColumnMetrics(
            availableWidthDp = Float.NaN,
            availableHeightDp = Float.POSITIVE_INFINITY,
            desiredColumnWidthDp = Float.NaN,
            columnGapDp = -1f,
            rowHeightDp = 12f,
        )

        assertTrue(metrics.columnWidthDp.isFinite())
        assertTrue(metrics.columnGapDp.isFinite() && metrics.columnGapDp >= 0f)
        assertTrue(metrics.rowHeightDp >= ALL_APPS_ROW_HEIGHT_DP)
        assertTrue(metrics.rowsPerColumn >= ALL_APPS_MIN_ROWS_PER_COLUMN)
        assertTrue(metrics.columnHeightDp.isFinite())
    }

    @Test
    fun sectionsRemainAlphabeticalGroupsAndAppsStayTopToBottom() {
        val sections = listOf(
            section("A", 2),
            section("B", 3),
        )

        val columns = packAllAppsColumns(sections, rowsPerColumn = 5)

        // The two spare rows after A are used by B's header and first app.
        assertEquals(2, columns.size)
        assertEquals(
            listOf("A-0", "A-1", "B-0", "B-1", "B-2"),
            columns.flatMap { it.apps }.map { it.label },
        )

        assertEquals(5, columns[0].items.size)
        assertEquals(2, columns[1].items.size)
        assertEquals(
            listOf("A", "B"),
            columns.flatMap { column ->
                column.items.filterIsInstance<AllAppsColumnItem.LetterHeader>().map { it.letter }
            },
        )
        assertTrue(columns[1].items.all { it is AllAppsColumnItem.App })
    }

    @Test
    fun oneRowRemainderStartsNextGroupInTheNextColumn() {
        val columns = packAllAppsColumns(
            sections = listOf(section("A", 3), section("B", 1)),
            rowsPerColumn = 5,
        )

        assertEquals(2, columns.size)
        assertEquals(listOf("A", "B"), columns.flatMap { column ->
            column.items.filterIsInstance<AllAppsColumnItem.LetterHeader>().map { it.letter }
        })
        assertTrue(columns.all { column ->
            column.items.zipWithNext().all { (item, next) ->
                item !is AllAppsColumnItem.LetterHeader || next is AllAppsColumnItem.App
            }
        })
        assertEquals(listOf("A-0", "A-1", "A-2", "B-0"), columns.flatMap { it.apps }.map { it.label })
    }

    @Test
    fun columnKeyIsDerivedFromItsStableFirstItem() {
        val columns = packAllAppsColumns(
            sections = listOf(section("A", 2), section("B", 2)),
            rowsPerColumn = 5,
        )

        assertTrue(columns.all { column -> column.key == "all-apps-column:${column.firstItemKey}" })
        assertEquals(columns.size, columns.map { it.key }.toSet().size)
    }

    @Test
    fun emptySectionsAreSkippedAndRepeatedLettersStillHaveUniqueKeys() {
        val columns = packAllAppsColumns(
            sections = listOf(
                AppSection("A", emptyList()),
                section("A", 1),
                section("A", 1, prefix = "other"),
            ),
            rowsPerColumn = 3,
        )

        assertEquals(2, columns.size)
        assertEquals(
            listOf("A:0", "A:1"),
            columns.flatMap { column ->
                column.items.filterIsInstance<AllAppsColumnItem.LetterHeader>().map { it.groupKey }
            },
        )
        assertEquals(columns.size, columns.map { it.key }.toSet().size)
        assertNotEquals(columns[0].items.first().key, columns[1].items.first().key)
    }

    @Test
    fun everyAppAppearsExactlyOnceWithStableIdentityKeys() {
        val apps = (0 until 11).map { app("app-$it", "A") }
        val columns = packAllAppsColumns(
            sections = listOf(AppSection("A", apps)),
            rowsPerColumn = 5,
        )

        assertEquals(apps, columns.flatMap { it.apps })
        assertEquals(1, columns.flatMap { column ->
            column.items.filterIsInstance<AllAppsColumnItem.LetterHeader>()
        }.size)
        assertEquals(listOf(5, 5, 2), columns.map { it.items.size })
        val itemKeys = columns.flatMap { column ->
            column.items.filterIsInstance<AllAppsColumnItem.App>().map { it.key }
        }
        assertEquals(itemKeys.size, itemKeys.toSet().size)
        assertEquals(columns.size, columns.map { it.key }.toSet().size)
    }

    private fun section(letter: String, count: Int, prefix: String = letter) =
        AppSection(letter, (0 until count).map { app("$prefix-$it", letter) })

    private fun app(id: String, letter: String) = AppInfo(
        label = id,
        packageName = "package.$letter.${id.replace('-', '_')}",
        activityName = ".$id",
    )
}
