package com.rakkez.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StudyTask(
    val id: Long,
    val name: String,
    val minutes: Int,
    val studiedSec: Int = 0,
    val done: Boolean = false,
    val sessions: Int = 0
)

data class LogEntry(val ts: Long, val type: String, val text: String, val sec: Int = 0)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("rakkez", Context.MODE_PRIVATE)

    // التطبيقات الممنوعة (ضيف أو شيل زي ما تحب)
    val NAMES = mapOf(
        "com.facebook.katana" to "فيسبوك", "com.facebook.lite" to "فيسبوك لايت", "com.facebook.orca" to "ماسنجر",
        "com.instagram.android" to "إنستجرام", "com.zhiliaoapp.musically" to "تيك توك", "com.ss.android.ugc.trill" to "تيك توك",
        "com.google.android.youtube" to "يوتيوب", "com.snapchat.android" to "سناب شات",
        "com.twitter.android" to "تويتر", "com.reddit.frontpage" to "ريديت", "com.netflix.mediaclient" to "نتفليكس",
        "com.tencent.ig" to "ببجي", "com.dts.freefireth" to "فري فاير", "com.roblox.client" to "روبلوكس",
        "com.supercell.clashofclans" to "كلاش أوف كلانس", "com.supercell.brawlstars" to "براول ستارز",
        "com.kiloo.subwaysurf" to "صب واي", "com.king.candycrushsaga" to "كاندي كراش"
    )
    val BLOCKED: Set<String> = NAMES.keys

    fun load(c: Context): List<StudyTask> {
        val arr = JSONArray(prefs(c).getString("tasks", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            StudyTask(o.getLong("id"), o.getString("name"), o.getInt("min"), o.getInt("sec"), o.getBoolean("done"), o.optInt("ses", 0))
        }
    }

    fun save(c: Context, tasks: List<StudyTask>) {
        val arr = JSONArray()
        tasks.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name)
                .put("min", it.minutes).put("sec", it.studiedSec).put("done", it.done).put("ses", it.sessions))
        }
        prefs(c).edit().putString("tasks", arr.toString()).apply()
    }

    // ---- التايمر: بيعتمد على وقت البداية، فيكمّل حتى لو خرجت من التطبيق ----
    fun activeId(c: Context) = prefs(c).getLong("activeId", 0L)
    fun activeStart(c: Context) = prefs(c).getLong("activeStart", 0L)

    fun fmt(sec: Int) = "%02d:%02d".format(sec / 60, sec % 60)

    fun stopActive(c: Context) {
        val id = activeId(c)
        if (id != 0L) {
            val el = ((System.currentTimeMillis() - activeStart(c)) / 1000).toInt().coerceAtLeast(0)
            var name = ""
            var added = 0
            save(c, load(c).map {
                if (it.id == id) {
                    name = it.name
                    val ns = minOf(it.studiedSec + el, it.minutes * 60)
                    added = ns - it.studiedSec
                    it.copy(studiedSec = ns)
                } else it
            })
            if (name.isNotEmpty()) log(c, "STOP", "⏸ وقّف \"$name\" بعد ${fmt(added)}", added)
        }
        prefs(c).edit().putLong("activeId", 0L).putLong("activeStart", 0L).apply()
    }

    fun start(c: Context, id: Long) {
        stopActive(c)
        var name = ""
        save(c, load(c).map { if (it.id == id) { name = it.name; it.copy(sessions = it.sessions + 1) } else it })
        prefs(c).edit().putLong("activeId", id).putLong("activeStart", System.currentTimeMillis()).apply()
        if (name.isNotEmpty()) log(c, "START", "▶ بدأ \"$name\"")
    }

    // ---- سجل النشاط ----
    fun loadLog(c: Context): List<LogEntry> {
        val arr = JSONArray(prefs(c).getString("log", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LogEntry(o.getLong("t"), o.getString("y"), o.getString("x"), o.optInt("s", 0))
        }
    }

    @Synchronized
    fun log(c: Context, type: String, text: String, sec: Int = 0) {
        val list = (loadLog(c) + LogEntry(System.currentTimeMillis(), type, text, sec)).takeLast(500)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.ts).put("y", it.type).put("x", it.text).put("s", it.sec)) }
        prefs(c).edit().putString("log", arr.toString()).apply()
        Remote.sync(c)
    }

    fun clearLog(c: Context) = prefs(c).edit().putString("log", "[]").apply()

    // ---- الأخ الكبير ----
    fun isFree(c: Context) = prefs(c).getBoolean("free", false)
    fun setFree(c: Context, v: Boolean) = prefs(c).edit().putBoolean("free", v).apply()
    fun pin(c: Context) = prefs(c).getString("pin", "1234") ?: "1234"
    fun setPin(c: Context, p: String) = prefs(c).edit().putString("pin", p).apply()

    // ---- المتابعة عن بُعد ----
    fun remoteUrl(c: Context) = prefs(c).getString("rurl", "") ?: ""
    fun setRemoteUrl(c: Context, u: String) = prefs(c).edit().putString("rurl", u.trim().trimEnd('/')).apply()
    fun familyCode(c: Context): String {
        val cur = prefs(c).getString("rcode", null)
        if (cur != null) return cur
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        val r = java.security.SecureRandom()
        val code = (1..14).map { chars[r.nextInt(chars.length)] }.joinToString("")
        prefs(c).edit().putString("rcode", code).apply()
        return code
    }

    fun remaining(c: Context) = load(c).count { !it.done }
    fun isLocked(c: Context) = !isFree(c) && remaining(c) > 0
}
