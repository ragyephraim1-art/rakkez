package com.rakkez.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StudyTask(
    val id: Long,
    val name: String,
    val minutes: Int,
    val studiedSec: Int = 0,
    val done: Boolean = false
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

    // ---------- المهام ----------
    fun load(c: Context): List<StudyTask> {
        val arr = JSONArray(prefs(c).getString("tasks", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            StudyTask(o.getLong("id"), o.getString("name"), o.getInt("min"), o.getInt("sec"), o.getBoolean("done"))
        }
    }

    fun save(c: Context, tasks: List<StudyTask>) {
        val arr = JSONArray()
        tasks.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name)
                .put("min", it.minutes).put("sec", it.studiedSec).put("done", it.done))
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
            val el = ((System.currentTimeMillis() - activeStart(c)) / 1000).toInt().coerceAtLeast(0)
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

    fun remaining(c: Context) = load(c).count { !it.done }
    fun isLocked(c: Context) = !isFree(c) && remaining(c) > 0
}
