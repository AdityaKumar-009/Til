package com.flivoro.tile8auncher.features

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Android owns the Home choice: TIL can only ask the user to make it the default. */
object DefaultHomeApp {
    fun isSelected(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val selected = context.packageManager.resolveActivity(home, 0)?.activityInfo
        return selected?.packageName == context.packageName
    }

    fun selectionIntent(context: Context, alreadySelected: Boolean): Intent {
        if (!alreadySelected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        // Also lets someone who has already selected TIL change their Home app.
        val homeSettings = Intent(Settings.ACTION_HOME_SETTINGS)
        return if (homeSettings.resolveActivity(context.packageManager) != null) {
            homeSettings
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }
}
