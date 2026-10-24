package com.flivoro.tile8auncher.data

/** The catalog is complete; only the opening viewport's artwork belongs on the startup path. */
internal data class StartupIconPlan(
    val startPackages: List<String>,
    val allAppsPackages: List<String>,
)

internal fun startupIconPlan(
    startPackages: List<String>,
    orderedApps: List<AppInfo>,
    excludedPackages: Set<String>,
    recentApps: List<AppInfo> = emptyList(),
    viewportIconCount: Int,
): StartupIconPlan {
    val visible = orderedApps.filter { it.packageName !in excludedPackages }
    val allowed = visible.mapTo(HashSet(), AppInfo::packageName)
    return StartupIconPlan(
        // A hidden drawer app can still be explicitly pinned to Start.
        startPackages = startPackages.filter(String::isNotBlank).distinct(),
        allAppsPackages = (recentApps.filter { it.packageName in allowed } + visible)
            .asSequence().map(AppInfo::packageName).distinct()
            .take(viewportIconCount.coerceAtLeast(0)).toList(),
    )
}
