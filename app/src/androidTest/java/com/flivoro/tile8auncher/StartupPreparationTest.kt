package com.flivoro.tile8auncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.phone.PhoneFolder
import com.flivoro.tile8auncher.ui.phone.PhoneFolderStore
import com.flivoro.tile8auncher.ui.phone.PhoneLayoutStore
import com.flivoro.tile8auncher.ui.phone.phoneStartupPackages
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupPreparationTest {
    @Test
    fun phoneStartupRestoresSavedPinsAndFolderPreviewsInsteadOfDesktopPins() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, 0)
        val oldOrder = prefs.getString("phone_shared_order", null)
        val oldFolders = prefs.getString("phone_shared_live_folders", null)
        try {
            PhoneLayoutStore.saveOrder(context, LauncherUiMode.PHONE_8,
                listOf("app:z-last", "phone_folder_test", "tile_mail", "app:removed"))
            PhoneFolderStore.save(context, LauncherUiMode.PHONE_8,
                listOf(PhoneFolder("phone_folder_test", "Folder", listOf("app:camera", "app:z-last"))))
            val tiles = listOf(TileModel("tile_mail", "Mail", packageName = "mail"),
                TileModel("tile_desktop", "Unused desktop pin", packageName = "desktop"))
            val apps = listOf("camera", "mail", "z-last").map { AppInfo(it, it, "$it.Main") }
            for (mode in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
                assertEquals(listOf("z-last", "camera", "mail"),
                    phoneStartupPackages(context, mode, tiles, apps))
            }
        } finally {
            prefs.edit().apply {
                if (oldOrder == null) remove("phone_shared_order")
                else putString("phone_shared_order", oldOrder)
                if (oldFolders == null) remove("phone_shared_live_folders")
                else putString("phone_shared_live_folders", oldFolders)
            }.commit()
        }
    }
}
