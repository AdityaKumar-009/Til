package com.flivoro.tile8auncher.features

import com.flivoro.tile8auncher.data.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AllAppsEnhancementsTest {
    @Test
    fun recentlyInstalledAppsFiltersAndOrdersNewestFirst() {
        val apps = listOf(
            app("older-new", installedAt = 20L, isNew = true),
            app("already-used", installedAt = 40L, isNew = false),
            app("latest-new", installedAt = 30L, isNew = true),
        )

        assertEquals(
            listOf("latest-new", "older-new"),
            recentlyInstalledApps(apps).map { it.app.label },
        )
    }

    private fun app(name: String, installedAt: Long, isNew: Boolean) = EnhancedAppInfo(
        app = AppInfo(
            label = name,
            packageName = "example.$name",
            activityName = "example.$name.Main",
            firstInstallTime = installedAt,
        ),
        firstInstallTime = installedAt,
        lastTimeUsed = 0L,
        totalForegroundTime = 0L,
        category = "Other",
        isNew = isNew,
    )
}
