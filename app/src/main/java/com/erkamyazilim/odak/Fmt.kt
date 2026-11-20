package com.erkamyazilim.odak

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private val TR = Locale("tr", "TR")

    /** 4_320_000 ms → "1 sa 12 dk". */
    fun dur(ms: Long): String {
        val min = (ms / 60_000L).toInt()
        val h = min / 60
        val m = min % 60
        return when {
            h > 0 && m > 0 -> "$h sa $m dk"
            h > 0 -> "$h sa"
            else -> "$m dk"
        }
    }

    fun hm(ts: Long): String = SimpleDateFormat("HH:mm", TR).format(Date(ts))
    fun dayMonth(ts: Long): String = SimpleDateFormat("d MMMM", TR).format(Date(ts))
    fun dayMonthTime(ts: Long): String = SimpleDateFormat("d MMMM, HH:mm", TR).format(Date(ts))

    /** 1248 → "1.248". */
    fun num(n: Int): String = String.format(TR, "%,d", n)

    fun hhmm(min: Int): String = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

    val sectionNames = mapOf(
        Db.SEC_FEED to "Ana akış",
        Db.SEC_STORIES to "Hikâyeler",
        Db.SEC_DM to "Mesajlar",
        Db.SEC_EXPLORE to "Keşfet",
        Db.SEC_PROFILE to "Profil",
        Db.SEC_OTHER to "Diğer"
    )
}
