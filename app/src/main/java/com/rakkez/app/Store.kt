package com.rakkez.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class StudyTask(
    val id: Long,
    val name: String,
    val minutes: Int,
    val studiedSec: Int = 0,
    val done: Boolean = false,
    val pending: Boolean = false, // مهمة ضفتها بنفسك ولسه ماتبعتتش للسيرفر
    val days: Int = 127,          // أيام الأسبوع: بت لكل يوم (0 = الأحد ... 6 = السبت)
    val startMin: Int = 0,        // بتبدأ من الساعة كام (دقايق من نص الليل)
    val once: Boolean = false,    // مهمة لمرة واحدة (بتتمسح تاني يوم)
    val day: Int = 0              // تاريخ اليوم اللي التقدّم بتاعها (yyyyMMdd)
)

data class LogEntry(val t: Long, val type: String, val name: String, val extra: Int, val id: Long)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("rakkez", Context.MODE_PRIVATE)

    // القايمة المبدئية للتطبيقات الممنوعة (بتتعدل من صفحة الأخ الكبير)
    val DEFAULT_BLOCKED = setOf(
        "com.facebook.katana", "com.facebook.lite", "com.facebook.orca",
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
        "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.google.android.youtube", "com.snapchat.android",
        "com.twitter.android", "com.reddit.frontpage", "com.netflix.mediaclient",
        "com.tencent.ig", "com.dts.freefireth", "com.roblox.client",
        "com.supercell.clashofclans", "com.supercell.brawlstars",
        "com.kiloo.subwaysurf", "com.king.candycrushsaga"
    )

    fun blocked(c: Context): Set<String> = prefs(c).getStringSet("blocked", null)?.toSet() ?: DEFAULT_BLOCKED
    fun setBlocked(c: Context, s: Set<String>) = prefs(c).edit().putStringSet("blocked", s).apply()

    // ---------- الأيام والمواعيد ----------
    fun todayStamp(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }
    fun todayDow(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 // 0 = الأحد ... 6 = السبت
    fun todayBit(): Int = 1 shl todayDow()
    fun nowMin(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
    private fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    // الثواني اللي عدّت من وقت البداية، من غير اللي قبل نص الليل
    fun elapsed(start: Long, now: Long): Int =
        ((now - maxOf(start, startOfToday())) / 1000).toInt().coerceAtLeast(0)

    fun isToday(t: StudyTask) = (t.days and todayBit()) != 0
    fun todays(c: Context): List<StudyTask> = load(c).filter { isToday(it) }.sortedBy { it.startMin }
    fun dueCount(c: Context): Int {
        val m = nowMin()
        return todays(c).count { !it.done && it.startMin <= m }
    }

    // ---------- المهام ----------
    // كل يوم جديد: التقدّم بيتصفّر، ومهام "المرة الواحدة" بتتمسح
    fun load(c: Context): List<StudyTask> {
        val today = todayStamp()
        val arr = JSONArray(prefs(c).getString("tasks", "[]"))
        return (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            val saved = o.optInt("day", 0)
            val once = o.optBoolean("once")
            val fresh = saved == 0 || saved == today
            if (once && !fresh) null else StudyTask(
                o.getLong("id"), o.getString("name"), o.getInt("min"),
                if (fresh) o.getInt("sec") else 0, fresh && o.getBoolean("done"),
                o.optBoolean("pending"), o.optInt("days", 127), o.optInt("start", 0), once, today
            )
        }
    }

    fun save(c: Context, tasks: List<StudyTask>) {
        val arr = JSONArray()
        tasks.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name)
                .put("min", it.minutes).put("sec", it.studiedSec).put("done", it.done).put("pending", it.pending)
                .put("days", it.days).put("start", it.startMin).put("once", it.once)
                .put("day", if (it.day == 0) todayStamp() else it.day))
        }
        prefs(c).edit().putString("tasks", arr.toString()).apply()
    }

    // ---------- سجل النشاط ----------
    fun log(c: Context, type: String, name: String = "", extra: Int = 0, id: Long = 0L) {
        val arr = JSONArray(prefs(c).getString("log", "[]"))
        arr.put(JSONObject().put("t", System.currentTimeMillis()).put("type", type)
            .put("name", name).put("extra", extra).put("id", id))
        val out = JSONArray()
        for (i in maxOf(0, arr.length() - 300) until arr.length()) out.put(arr.get(i))
        prefs(c).edit().putString("log", out.toString()).apply()
    }

    fun logs(c: Context): List<LogEntry> {
        val arr = JSONArray(prefs(c).getString("log", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LogEntry(o.getLong("t"), o.getString("type"), o.optString("name"), o.optInt("extra"), o.optLong("id"))
        }
    }

    fun clearLog(c: Context) = prefs(c).edit().putString("log", "[]").apply()

    // ---------- التايمر (بيكمّل حتى لو خرجت من التطبيق) ----------
    fun activeId(c: Context) = prefs(c).getLong("activeId", 0L)
    fun activeStart(c: Context) = prefs(c).getLong("activeStart", 0L)

    fun stopActive(c: Context) {
        val id = activeId(c)
        if (id != 0L) {
            val el = elapsed(activeStart(c), System.currentTimeMillis())
            val tasks = load(c)
            val t = tasks.firstOrNull { it.id == id }
            if (t != null) {
                val n = minOf(t.studiedSec + el, t.minutes * 60)
                if (n > t.studiedSec) log(c, "stop", t.name, n - t.studiedSec, id)
                save(c, tasks.map { if (it.id == id) it.copy(studiedSec = n) else it })
            }
        }
        prefs(c).edit().putLong("activeId", 0L).putLong("activeStart", 0L).apply()
    }

    fun start(c: Context, id: Long) {
        stopActive(c)
        val t = load(c).firstOrNull { it.id == id }
        log(c, "start", t?.name ?: "", 0, id)
        prefs(c).edit().putLong("activeId", id).putLong("activeStart", System.currentTimeMillis()).apply()
    }

    // ---------- الأخ الكبير ----------
    fun isFree(c: Context) = prefs(c).getBoolean("free", false) || prefs(c).getBoolean("freeRemote", false)
    fun isFreeRemote(c: Context) = prefs(c).getBoolean("freeRemote", false)
    fun setFreeRemote(c: Context, v: Boolean) = prefs(c).edit().putBoolean("freeRemote", v).apply()

    // ---------- الربط بصفحة الأخ الكبير ----------
    fun serverUrl(c: Context) = (prefs(c).getString("serverUrl", "") ?: "").trim().trimEnd('/')
    fun deviceKey(c: Context) = (prefs(c).getString("deviceKey", "") ?: "").trim()
    fun setLink(c: Context, url: String, key: String) =
        prefs(c).edit().putString("serverUrl", url.trim()).putString("deviceKey", key.trim()).apply()
    fun epoch(c: Context) = prefs(c).getInt("epoch", 0)
    fun setEpoch(c: Context, e: Int) = prefs(c).edit().putInt("epoch", e).apply()
    fun lastSync(c: Context) = prefs(c).getLong("lastSync", 0L)
    fun setLastSync(c: Context, t: Long) = prefs(c).edit().putLong("lastSync", t).apply()
    fun lastLogSync(c: Context) = prefs(c).getLong("lastLogSync", 0L)
    fun setLastLogSync(c: Context, t: Long) = prefs(c).edit().putLong("lastLogSync", t).apply()
    fun clearActive(c: Context) = prefs(c).edit().putLong("activeId", 0L).putLong("activeStart", 0L).apply()
    fun setFree(c: Context, v: Boolean) = prefs(c).edit().putBoolean("free", v).apply()
    fun pin(c: Context) = prefs(c).getString("pin", "1234") ?: "1234"
    fun setPin(c: Context, p: String) = prefs(c).edit().putString("pin", p).apply()

    // اللي ميعاده جه وماخلصش النهارده
    fun remaining(c: Context) = dueCount(c)
    fun isLocked(c: Context) = !isFree(c) && dueCount(c) > 0
}
