package com.flivoro.tile8auncher

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.SharedStartWorkspace
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.phone.PhoneFolder
import com.flivoro.tile8auncher.ui.phone.PhoneFolderStore
import com.flivoro.tile8auncher.ui.phone.PhoneLayoutStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID

/** Real Android persistence, isolated from the launcher's user preferences and artwork. */
@RunWith(AndroidJUnit4::class)
class StartupPreparationTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "startup-test-${UUID.randomUUID()}"
    private val directory = File(base.cacheDir, namespace).apply { mkdirs() }
    private val preferenceNames = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int) =
            base.getSharedPreferences("$namespace-$name".also { preferenceNames += it }, mode)
    }
    private val prefs get() = context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, 0)
    private val apps = listOf("alpha", "beta", "gamma").map { AppInfo(it, it, "$it.Main") }
    private val alpha = TileModel("app_alpha_saved", "My alpha", "alpha", "alpha.Main",
        colorValue = 0xFF123456)

    @After fun cleanUp() {
        preferenceNames.forEach { base.deleteSharedPreferences(it) }
        directory.deleteRecursively()
    }

    @Test fun coldCatalogRestoreUsesSavedLabelsThenRefreshesOnce() {
        val snapshot = JSONObject().put("locale", Locale.getDefault().toLanguageTag())
            .put("sections", JSONArray().put(JSONObject().put("letter", "A")
                .put("apps", JSONArray().put(JSONObject().put("label", "Saved alpha")
                    .put("package", "alpha").put("activity", "alpha.Main")))))
        File(directory, "launcher_catalog_v1.json").writeText(snapshot.toString())
        val repository = AppsRepository(context)
        val restored = repository.loadInitialCatalog(0)
        assertEquals("Saved alpha", restored.single().apps.single().label)
        assertSame(restored, repository.loadInitialCatalog(0))
        val verified = repository.refreshCatalogIfNeeded(0)
        assertFalse(verified.flatMap { it.apps }.any { it.packageName == "alpha" })
        assertSame(verified, repository.refreshCatalogIfNeeded(0))
        assertEquals(verified, AppsRepository(context).loadInitialCatalog(0))
    }

    @Test fun corruptCatalogFallsBackToInstalledApps() {
        File(directory, "launcher_catalog_v1.json").writeText("partial json")
        val repository = AppsRepository(context)
        val restored = repository.loadInitialCatalog(0)
        assertEquals(repository.getCategorizedApps(), restored)
        assertTrue(JSONObject(File(directory, "launcher_catalog_v1.json").readText()).has("sections"))
    }

    @Test fun migrationPreservesBothWorkspacesAndRunsOnlyOnce() {
        val repository = AppsRepository(context)
        repository.savePinnedTiles(listOf(alpha))
        val original = prefs.getString("pinned_tiles_json", null)
        PhoneLayoutStore.saveOrder(context, LauncherUiMode.PHONE_8, listOf("app:alpha", "app:beta"))
        val merged = SharedStartWorkspace.migrate(context, repository, repository.loadPinnedTiles(), apps)
        assertEquals(listOf("alpha", "beta"), merged.map { it.packageName })
        assertEquals(alpha, merged.first())
        val backup = JSONObject(prefs.getString("shared_start_workspace_backup_v1", "{}")!!)
        assertEquals(original, backup.getString("pinned_tiles_json"))
        repository.savePinnedTiles(emptyList())
        assertTrue(SharedStartWorkspace.migrate(context, repository, emptyList(), apps).isEmpty())
        assertTrue(AppsRepository(context).loadPinnedTiles().isEmpty())
    }

    @Test fun migrationKeepsChildrenFromBothLegacyPhoneFolderStores() {
        val repository = AppsRepository(context)
        repository.savePinnedTiles(listOf(alpha))
        prefs.edit().putString("phone_PHONE_8_order", "[\"phone_folder_old8\"]")
            .putString("phone_MOBILE_10_order", "[\"phone_folder_old10\"]")
            .putString("phone_PHONE_8_live_folders", "[{\"id\":\"phone_folder_old8\",\"children\":[\"app:beta\"]}]")
            .putString("phone_MOBILE_10_live_folders", "[{\"id\":\"phone_folder_old10\",\"children\":[\"app:gamma\"]}]")
            .commit()
        val merged = SharedStartWorkspace.migrate(context, repository, listOf(alpha), apps)
        assertEquals(setOf("alpha", "beta", "gamma"), merged.map { it.packageName }.toSet())
        assertEquals(2, PhoneFolderStore.read(context, LauncherUiMode.MOBILE_10).size)
    }

    @Test fun desktopAndBothPhoneModesUseTheSamePinsIncludingFolderChildren() {
        val repository = AppsRepository(context)
        prefs.edit().putBoolean("shared_start_workspace_v1", true).commit()
        repository.savePinnedTiles(listOf(alpha))
        for (mode in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
            assertEquals(listOf("app:alpha"), SharedStartWorkspace.projectPhoneOrder(
                repository.loadPinnedTiles(), emptyList(), emptyList()))
            val folder = PhoneFolder("phone_folder_test", "My folder", listOf("app:alpha", "app:beta"))
            PhoneFolderStore.save(context, mode, listOf(folder))
            val fromPhone = SharedStartWorkspace.tilesForPhoneOrder(context, mode,
                listOf(folder.id, "app:gamma"), repository.loadPinnedTiles(), apps)
            repository.savePinnedTiles(fromPhone)
            assertEquals(setOf("alpha", "beta", "gamma"),
                AppsRepository(context).loadPinnedTiles().map { it.packageName }.toSet())
            assertEquals(listOf(folder.id, "app:gamma"), SharedStartWorkspace.projectPhoneOrder(
                fromPhone, listOf(folder.id), PhoneFolderStore.read(context, mode)))

            // Desktop unpin prunes the phone folder without losing its remaining child.
            repository.savePinnedTiles(fromPhone.filterNot { it.packageName == "beta" })
            assertEquals(listOf("app:alpha"), PhoneFolderStore.read(context, mode).single().children)
            // Phone All Apps unpin also removes a package held inside a live folder.
            val order = SharedStartWorkspace.orderWithoutPackage(context, mode,
                listOf(folder.id, "app:gamma"), repository.loadPinnedTiles(), "alpha")
            repository.savePinnedTiles(SharedStartWorkspace.tilesForPhoneOrder(context, mode,
                order, repository.loadPinnedTiles(), apps))
            assertEquals(listOf("gamma"), repository.loadPinnedTiles().map { it.packageName })
            assertTrue(PhoneFolderStore.read(context, mode).isEmpty())
            repository.savePinnedTiles(listOf(alpha))
        }
    }

    @Test fun anotherActivityOrBackupRestoreInvalidatesCachedPinsIncludingEmptyStart() {
        val repository = AppsRepository(context)
        repository.savePinnedTiles(listOf(alpha))
        assertEquals(listOf(alpha), repository.cachedPinnedTiles)
        AppsRepository(context).savePinnedTiles(emptyList())
        assertNull(repository.cachedPinnedTiles)
        assertTrue(repository.loadPinnedTiles().isEmpty())
        assertTrue(AppsRepository(context).loadPinnedTiles().isEmpty())
    }
}
