package com.rakkez.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

// المتابعة والتحكم عن بُعد عن طريق Firebase Realtime Database:
//  - التطبيق بيرفع لقطة (مهام + سجل) على rakkez/<code>
//  - صفحة الأخ الكبير بتبعت أوامر على rakkez/<code>/cmd والتطبيق بينفذها ويمسحها
object Remote {
    private val ex = Executors.newSingleThreadExecutor()

    private fun http(method: String, url: String, body: String? = null): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code in 200..299) conn.inputStream.bufferedReader().readText() else null
        } finally {
            conn.disconnect()
        }
    }

    fun sync(c: Context) {
        val app = c.applicationContext
        val base = Store.remoteUrl(app)
        if (base.isBlank()) return
        val body = try { snapshot(app) } catch (_: Exception) { return }
        val code = Store.familyCode(app)
        ex.execute {
            try { http("PUT", "$base/rakkez/$code.json", body) } catch (_: Exception) {}
        }
    }

    // بيجيب الأوامر الجديدة وينفذها (بيتنادى كل كام ثانية من BlockerService)
    fun poll(c: Context) {
        val app = c.applicationContext
        val base = Store.remoteUrl(app)
        if (base.isBlank()) return
        val code = Store.familyCode(app)
        ex.execute {
            try {
                val txt = http("GET", "$base/rakkez/$code/cmd.json") ?: return@execute
                if (txt.isBlank() || txt.trim() == "null") return@execute
                val o = JSONObject(txt)
                val keys = mutableListOf<String>()
                o.keys().forEach { keys.add(it) }
                keys.sort()
                var changed = false
                for (k in keys) {
                    val cmd = o.optJSONObject(k)
                    if (cmd != null && apply(app, cmd)) changed = true
                    http("DELETE", "$base/rakkez/$code/cmd/$k.json")
                }
                if (changed) sync(app)
            } catch (_: Exception) {}
        }
    }

    private fun apply(c: Context, cmd: JSONObject): Boolean {
        when (cmd.optString("type")) {
            "free" -> {
                val v = cmd.optBoolean("value", false)
                Store.setFree(c, v)
                Store.log(c, "REMOTE", if (v) "🌐 الأخ الكبير ألغى القفل" else "🌐 الأخ الكبير رجّع القفل")
            }
            "add" -> {
                val name = cmd.optString("name").trim()
                val min = cmd.optInt("min", 0)
                if (name.isEmpty() || min < 1) return false
                Store.save(c, Store.load(c) + StudyTask(System.currentTimeMillis(), name, min))
                Store.log(c, "ADD", "➕ الأخ الكبير أضاف مهمة \"$name\"")
            }
            "delete" -> {
                val id = cmd.optLong("id", 0L)
                val t = Store.load(c).firstOrNull { it.id == id } ?: return false
                if (Store.activeId(c) == id) Store.stopActive(c)
                Store.save(c, Store.load(c).filter { it.id != id })
                Store.log(c, "DELETE", "🗑 الأخ الكبير حذف مهمة \"${t.name}\"")
            }
            "done" -> {
                val id = cmd.optLong("id", 0L)
                val t = Store.load(c).firstOrNull { it.id == id } ?: return false
                if (Store.activeId(c) == id) Store.stopActive(c)
                Store.save(c, Store.load(c).map { if (it.id == id) it.copy(done = true) else it })
                Store.log(c, "DONE", "✅ الأخ الكبير علّم \"${t.name}\" خلصت")
            }
            "reset" -> {
                Store.stopActive(c)
                Store.save(c, Store.load(c).map { it.copy(studiedSec = 0, done = false, sessions = 0) })
                Store.log(c, "RESET", "🔄 الأخ الكبير صفّر وقت المهام")
            }
            else -> return false
        }
        return true
    }

    private fun snapshot(c: Context): String {
        val tasks = JSONArray()
        Store.load(c).forEach {
            tasks.put(JSONObject().put("id", it.id).put("name", it.name).put("min", it.minutes)
                .put("sec", it.studiedSec).put("done", it.done).put("ses", it.sessions))
        }
        val log = JSONArray()
        Store.loadLog(c).takeLast(100).forEach {
            log.put(JSONObject().put("t", it.ts).put("y", it.type).put("x", it.text).put("s", it.sec))
        }
        val o = JSONObject()
            .put("ts", System.currentTimeMillis())
            .put("free", Store.isFree(c))
            .put("tasks", tasks)
            .put("log", log)
        val id = Store.activeId(c)
        if (id != 0L) {
            val t = Store.load(c).firstOrNull { it.id == id }
            if (t != null) o.put("active", JSONObject().put("name", t.name).put("start", Store.activeStart(c)))
        }
        return o.toString()
    }
}
