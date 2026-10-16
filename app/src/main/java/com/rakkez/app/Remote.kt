package com.rakkez.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

// بيرفع لقطة كاملة (المهام + آخر 100 حدث) على قاعدة بيانات Firebase عشان الأخ الكبير يتابع من أي مكان
object Remote {
    private val ex = Executors.newSingleThreadExecutor()

    fun sync(c: Context) {
        val app = c.applicationContext
        val base = Store.remoteUrl(app)
        if (base.isBlank()) return
        val body = try { snapshot(app) } catch (_: Exception) { return }
        val code = Store.familyCode(app)
        ex.execute {
            try {
                val conn = URL("$base/rakkez/$code.json").openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
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
