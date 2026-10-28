package com.flivoro.tile8auncher.features

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetProviderFilteringTest {
    private data class Provider(val packageName: String, val name: String)

    private val installed = listOf(
        Provider("com.example.clock", "clock-small"),
        Provider("com.example.calendar", "calendar"),
        Provider("com.example.clock", "clock-large"),
    )

    @Test fun globalPickerKeepsEveryInstalledWidget() {
        assertEquals(installed, widgetChoicesForPackage(installed, null, Provider::packageName))
    }

    @Test fun appPickerShowsOnlyThatAppsWidgetVariants() {
        assertEquals(
            listOf(installed[0], installed[2]),
            widgetChoicesForPackage(installed, "com.example.clock", Provider::packageName),
        )
    }

    @Test fun appPickerDoesNotLeakSimilarPackageNamesOrOtherApps() {
        assertEquals(
            emptyList<Provider>(),
            widgetChoicesForPackage(installed, "com.example.clock.pro", Provider::packageName),
        )
    }
}
