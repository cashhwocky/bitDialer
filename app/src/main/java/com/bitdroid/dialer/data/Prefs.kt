package com.bitdroid.dialer.data

import android.content.Context
import android.content.SharedPreferences

/** Device-protected so the in-call screen can read them before first unlock. */
object Prefs {
    fun get(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences("bitdialer", Context.MODE_PRIVATE)

    /** Faint Material You glow behind the black. On by default in bitDialer. */
    fun monetBackground(context: Context): Boolean = get(context).getBoolean("monet_bg", true)
    fun setMonetBackground(context: Context, on: Boolean) = get(context).edit().putBoolean("monet_bg", on).apply()

    enum class ListStyle { PANEL, CARDS }

    fun listStyle(context: Context): ListStyle =
        runCatching { ListStyle.valueOf(get(context).getString("list_style", "CARDS")!!) }.getOrDefault(ListStyle.CARDS)

    fun setListStyle(context: Context, s: ListStyle) = get(context).edit().putString("list_style", s.name).apply()

    fun vibrateCalls(context: Context): Boolean = get(context).getBoolean("vibrate_calls", true)
    fun setVibrateCalls(context: Context, on: Boolean) = get(context).edit().putBoolean("vibrate_calls", on).apply()

    fun dtmfTones(context: Context): Boolean = get(context).getBoolean("dtmf", true)
    fun setDtmfTones(context: Context, on: Boolean) = get(context).edit().putBoolean("dtmf", on).apply()
}
