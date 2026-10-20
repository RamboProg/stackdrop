package com.stackdrop.game.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the home-screen icon between the regular and the gold premium one by enabling exactly
 * one of the two launcher aliases in the manifest. Some launchers briefly drop the icon or close
 * the app when this changes, so callers apply it when the app goes to the background.
 */
object LauncherIcon {

    private const val DEFAULT = "com.stackdrop.game.LauncherDefault"
    private const val PREMIUM = "com.stackdrop.game.LauncherPremium"

    fun apply(context: Context, premium: Boolean) {
        // Enable the new entry before disabling the old one so the app always has a launcher icon.
        setEnabled(context, if (premium) PREMIUM else DEFAULT, enabled = true, manifestDefault = !premium)
        setEnabled(context, if (premium) DEFAULT else PREMIUM, enabled = false, manifestDefault = premium)
    }

    private fun setEnabled(context: Context, alias: String, enabled: Boolean, manifestDefault: Boolean) {
        val pm = context.packageManager
        val component = ComponentName(context.packageName, alias)
        val current = when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestDefault
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> false
        }
        if (current == enabled) return
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
