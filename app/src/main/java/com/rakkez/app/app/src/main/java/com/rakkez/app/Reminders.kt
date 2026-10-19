package com.rakkez.app

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

// تنبيهات مواعيد المهام: قبل الميعاد بـ 5 دقايق، وعند الميعاد
object Reminders {
    const val CHANNEL = "reminders"
    const val LEAD_MIN = 5

    private fun midnight(offsetDays: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, offsetDays)
        return c.timeInMillis
    }

    // أقرب تنبيه جاي خلال 8 أيام (أو null لو مفيش)
    private fun nextAt(c: Context): Long? {
        val tasks = Store.load(c).filter { it.startMin > 0 }
        if (tasks.isEmpty()) return null
        val now = System.currentTimeMillis()
        var best = Long.MAX_VALUE
        for (off in 0..7) {
            val base = midnight(off)
            val cal = Calendar.getInstance()
            cal.timeInMillis = base
            val bit = 1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1)
            for (t in tasks) {
                if ((t.days and bit) == 0) continue
                if (off == 0 && t.done) continue
                for (m in intArrayOf(t.startMin - LEAD_MIN, t.startMin)) {
                    if (m < 0) continue
                    val at = base + m * 60_000L
                    if (at > now + 1_000 && at < best) best = at
                }
            }
        }
        return if (best == Long.MAX_VALUE) null else best
    }

    private fun pending(c: Context, at: Long): PendingIntent =
        PendingIntent.getBroadcast(
            c, 4242, Intent(c, ReminderReceiver::class.java).putExtra("at", at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // بتتنادي كل ما المهام تتغيّر أو التطبيق يشتغل: بتضبط منبّه واحد على أقرب ميعاد
    fun schedule(c: Context) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = nextAt(c)
            if (at == null) { am.cancel(pending(c, 0L)); return }
            val pi = pending(c, at)
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: Exception) {}
    }

    fun notify(c: Context, id: Int, title: String, text: String) {
        try {
            val nm = c.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL, "مواعيد المذاكرة", NotificationManager.IMPORTANCE_HIGH)
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
            val open = PendingIntent.getActivity(
                c, id, Intent(c, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(c, CHANNEL)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(id, n)
        } catch (_: Exception) {}
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val at = i.getLongExtra("at", 0L)
        if (at > 0) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = at
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val bit = 1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1)
            val ts = Store.load(c).filter { !it.done && (it.days and bit) != 0 && it.startMin > 0 }
            ts.filter { it.startMin == m }.forEach {
                Reminders.notify(
                    c, (it.id % 100000).toInt(), "⏰ جه ميعاد المذاكرة",
                    "${it.name} · ${it.minutes} دقيقة. الموبايل هيتقفل لحد ما تخلّصها"
                )
            }
            ts.filter { it.startMin - Reminders.LEAD_MIN == m }.forEach {
                Reminders.notify(
                    c, (it.id % 100000).toInt() + 100000, "⏳ فاضل ${Reminders.LEAD_MIN} دقايق",
                    "${it.name} هتبدأ الساعة ${fmtClock(it.startMin)}"
                )
            }
        }
        Reminders.schedule(c)
    }
}
