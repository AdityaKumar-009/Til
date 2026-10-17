package com.flivoro.tile8auncher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.features.canRequestAppUninstall
import com.flivoro.tile8auncher.features.userConfirmedAppUninstallIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies Android's uninstall contract without deleting any package. */
@RunWith(AndroidJUnit4::class)
class AppUninstallRequestTest {
    private val context: Context get() =
        InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun manifestDeclaresDeleteRequestPermission() {
        val info = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS,
        )
        assertTrue(info.requestedPermissions.orEmpty()
            .contains(Manifest.permission.REQUEST_DELETE_PACKAGES))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            assertEquals(PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.REQUEST_DELETE_PACKAGES))
        }
    }

    @Test fun opensTheSystemUninstallConfirmationForRequestedPackage() {
        val intent = userConfirmedAppUninstallIntent("com.example.uninstallable")
        assertEquals(Intent.ACTION_UNINSTALL_PACKAGE, intent.action)
        assertEquals("package", intent.data?.scheme)
        assertEquals("com.example.uninstallable", intent.data?.schemeSpecificPart)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false))
        assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test fun neverUninstallsLauncherOrNativeSettings() {
        assertFalse(canRequestAppUninstall(context, context.packageName))
        assertFalse(canRequestAppUninstall(context, "mosaic.launcher.settings"))
        assertFalse(canRequestAppUninstall(context, ""))
    }
}
