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

    val onBreak get() = System.currentTimeMillis() < breakUntil
    val reels get() = master && get(KEY_REELS) && !onBreak
    val explore get() = master && get(KEY_EXPLORE) && !onBreak
    val dm get() = master && get(KEY_DM) && !onBreak
    val lock get() = get(KEY_LOCK)

    // Zaman kuralları
    var nightOn: Boolean
        get() = sp.getBoolean("nightOn", false)
        set(v) = sp.edit().putBoolean("nightOn", v).apply()
    var nightStart: Int // gece yarısından itibaren dakika
        get() = sp.getInt("nightStart", 23 * 60)
        set(v) = sp.edit().putInt("nightStart", v).apply()
    var nightEnd: Int
        get() = sp.getInt("nightEnd", 7 * 60)
        set(v) = sp.edit().putInt("nightEnd", v).apply()
    var limitOn: Boolean
        get() = sp.getBoolean("limitOn", false)
        set(v) = sp.edit().putBoolean("limitOn", v).apply()
    var limitMin: Int
        get() = sp.getInt("limitMin", 45)
        set(v) = sp.edit().putInt("limitMin", v).apply()
    var breakUntil: Long
        get() = sp.getLong("breakUntil", 0L)
        set(v) = sp.edit().putLong("breakUntil", v).apply()
    var allowUntil: Long
        get() = sp.getLong("allowUntil", 0L)
        set(v) = sp.edit().putLong("allowUntil", v).apply()
    var weekly: Boolean
        get() = sp.getBoolean("weekly", false)
        set(v) = sp.edit().putBoolean("weekly", v).apply()

    /** Takipçi taraması sürüyorsa tarama numarası, yoksa 0. */
    var scanId: Long
        get() = sp.getLong("scanId", 0L)
        set(v) = sp.edit().putLong("scanId", v).apply()

    fun nightActiveNow(): Boolean {
        if (!nightOn) return false
        val c = java.util.Calendar.getInstance()
        val m = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
        val s = nightStart
        val e = nightEnd
        return if (s == e) false else if (s < e) m in s until e else (m >= s || m < e)
    }

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
