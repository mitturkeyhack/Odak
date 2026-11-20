package com.erkamyazilim.odak

import android.content.Context

/** Uygulamanın ayarları. Servis ve ana ekran aynı yerden okur. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("odak", Context.MODE_PRIVATE)

    var master: Boolean
        get() = sp.getBoolean(KEY_MASTER, true)
        set(v) = sp.edit().putBoolean(KEY_MASTER, v).apply()

    fun get(key: String): Boolean = sp.getBoolean(key, key != KEY_LOCK)

    fun set(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    val reels get() = master && get(KEY_REELS)
    val explore get() = master && get(KEY_EXPLORE)
    val dm get() = master && get(KEY_DM)
    val lock get() = get(KEY_LOCK)

    var blockedToday: Int
        get() = if (sp.getLong("blockedDay", 0L) == today()) sp.getInt("blockedCount", 0) else 0
        set(v) = sp.edit().putLong("blockedDay", today()).putInt("blockedCount", v).apply()

    private fun today(): Long = System.currentTimeMillis() / 86_400_000L

    companion object {
        const val KEY_MASTER = "master"
        const val KEY_REELS = "reels"
        const val KEY_EXPLORE = "explore"
        const val KEY_DM = "dm"
        const val KEY_LOCK = "lock"
    }
}
