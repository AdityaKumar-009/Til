package com.flivoro.tile8auncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.phone.PhoneFolderStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneFolderStoreTest {
    @Test
    fun foldersPersistAndAreSharedAcrossPhoneThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before81 = PhoneFolderStore.read(context, LauncherUiMode.PHONE_8)
        try {
            PhoneFolderStore.save(context, LauncherUiMode.PHONE_8, emptyList())
            PhoneFolderStore.save(context, LauncherUiMode.MOBILE_10, emptyList())
            val folderId = PhoneFolderStore.createOrAdd(
                context, LauncherUiMode.PHONE_8, "app:camera", "app:photos",
            )
            assertNotNull(folderId)
            assertEquals(
                listOf("app:photos", "app:camera"),
                PhoneFolderStore.read(context, LauncherUiMode.PHONE_8).single().children,
            )
            PhoneFolderStore.createOrAdd(
                context, LauncherUiMode.PHONE_8, "app:music", folderId!!,
            )
            PhoneFolderStore.rename(context, LauncherUiMode.PHONE_8, folderId, "Music & Photos")
            assertEquals(
                "Music & Photos",
                PhoneFolderStore.read(context, LauncherUiMode.PHONE_8).single().name,
            )
            assertEquals("Music & Photos",
                PhoneFolderStore.read(context, LauncherUiMode.MOBILE_10).single().name)
            assertEquals(3,
                PhoneFolderStore.read(context, LauncherUiMode.MOBILE_10).single().children.size)
            PhoneFolderStore.removeChild(context, LauncherUiMode.PHONE_8, folderId, "app:camera")
            assertEquals(2, PhoneFolderStore.read(context, LauncherUiMode.PHONE_8).single().children.size)
            assertEquals(
                listOf("app:photos", "app:music"),
                PhoneFolderStore.dissolve(context, LauncherUiMode.PHONE_8, folderId),
            )
            assertTrue(PhoneFolderStore.read(context, LauncherUiMode.PHONE_8).isEmpty())
        } finally {
            PhoneFolderStore.save(context, LauncherUiMode.PHONE_8, before81)
            // Both phone modes address the same canonical folder store.
        }
    }
}
