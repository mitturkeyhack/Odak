package com.erkamyazilim.odak

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Her pazar 20:00'de haftalık özet bildirimi. */
object Report {
    private const val CHANNEL = "weekly"

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 7, Intent(context, WeeklyReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(context: Context) {
        val c = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis() + 60_000L) c.add(Calendar.WEEK_OF_YEAR, 1)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pending(context))
        } catch (_: Exception) {
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context))
    }

    fun notify(context: Context) {
        val db = Db.get(context)
        val thisWeek = db.usageTotalRange(Db.daysAgo(6), Db.today())
        val lastWeek = db.usageTotalRange(Db.daysAgo(13), Db.daysAgo(7))
        val blocked = db.counter(Db.daysAgo(6), Db.today(), Db.CNT_BLOCK_DM) +
            db.counter(Db.daysAgo(6), Db.today(), Db.CNT_BLOCK_EXPLORE) +
            db.counter(Db.daysAgo(6), Db.today(), Db.CNT_BLOCK_TAB)
        val diff = thisWeek - lastWeek
        val cmp = when {
            lastWeek == 0L -> ""
            diff < 0 -> ", geçen haftadan ${Fmt.dur(-diff)} az"
            diff > 0 -> ", geçen haftadan ${Fmt.dur(diff)} fazla"
            else -> ", geçen haftayla aynı"
        }
        val text = "Bu hafta Instagram'da ${Fmt.dur(thisWeek)} kaldın$cmp. Reels $blocked kez engellendi."

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Haftalık rapor", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 8, Intent(context, MainActivity::class.java).putExtra("tab", 1),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_target)
            .setContentTitle("Odak · Haftalık rapor")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(1, n)
        } catch (_: SecurityException) {
        }
    }
}

class WeeklyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = Prefs(context)
        if (!prefs.weekly) return
        try {
            Report.notify(context)
        } catch (_: Exception) {
        }
        Report.schedule(context)
    }
}
