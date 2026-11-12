package com.bitdroid.dialer.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Launcher icon choices; each is an activity-alias in the manifest. White is the default. */
object AppIcons {
    data class Choice(val key: String, val label: String, val alias: String, val bg: Long, val darkMark: Boolean)

    val all = listOf(
        Choice("white", "WHITE", ".IconWhite", 0xFFFFFFFF, true),
        Choice("black", "AMOLED", ".IconBlack", 0xFF000000, false),
        Choice("blue", "BLUE", ".IconBlue", 0xFFA8C7FA, true),
        Choice("lilac", "LILAC", ".IconLilac", 0xFFEADDFF, true),
    )

    fun current(context: Context): String = Prefs.get(context).getString("app_icon", "white") ?: "white"

    fun set(context: Context, key: String) {
        val chosen = all.firstOrNull { it.key == key } ?: return
        val pm = context.packageManager
        // Enable the new one first so the app never has zero launcher entries.
        for (c in listOf(chosen) + all.filter { it != chosen }) {
            pm.setComponentEnabledSetting(
                ComponentName(context, "com.bitdroid.dialer" + c.alias),
                if (c == chosen) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        Prefs.get(context).edit().putString("app_icon", key).apply()
    }
}
