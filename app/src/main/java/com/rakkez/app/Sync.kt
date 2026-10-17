package com.rakkez.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Sync {
    // بيتنادي من Thread (مش من الشاشة). بيبعت التقدم والسجل ويستلم المهام من الأخ الكبير.
    @Synchronized
    fun run(c: Context): Boolean {
        val base = Store.serverUrl(c)
        val key = Store.deviceKey(c)
        if (base.isBlank() || key.isBlank()) return false
        try {
            val now = System.currentTimeMillis()
            val activeId = Store.activeId(c)
            val activeStart = Store.activeStart(c)

            val ta = JSONArray()
            Store.load(c).forEach {
                val el = if (it.id == activeId) ((now - activeStart) / 1000).toInt().coerceAtLeast(0) else 0
                ta.put(JSONObject().put("id", it.id)
                    .put("sec", minOf(it.studiedSec + el, it.minutes * 60)).put("done", it.done))
            }
            val since = Store.lastLogSync(c)
            val newLogs = Store.logs(c).filter { it.t > since }
            val la = JSONArray()
            newLogs.forEach {
                la.put(JSONObject().put("t", it.t).put("type", it.type)
                    .put("name", it.name).put("extra", it.extra).put("id", it.id))
            }
            val body = JSONObject().put("key", key).put("epoch", Store.epoch(c))
                .put("tasks", ta).put("log", la)
                .put("active", if (activeId != 0L) JSONObject().put("id", activeId).put("start", activeStart) else JSONObject.NULL)

            val conn = URL("$base/api/sync").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode != 200) { conn.disconnect(); return false }
            val txt = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            apply(c, JSONObject(txt))
            if (newLogs.isNotEmpty()) Store.setLastLogSync(c, newLogs.maxOf { it.t })
            Store.setLastSync(c, System.currentTimeMillis())
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun apply(c: Context, r: JSONObject) {
        val srvEpoch = r.optInt("epoch")
        val reset = srvEpoch != Store.epoch(c)
        val local = Store.load(c).associateBy { it.id }
        val arr = r.getJSONArray("tasks")
        val merged = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val id = o.getLong("id")
            val min = o.getInt("min")
            val lt = local[id]
            val sec = if (reset) o.getInt("sec")
                      else minOf(maxOf(o.getInt("sec"), lt?.studiedSec ?: 0), min * 60)
            val done = if (reset) o.getBoolean("done") else (o.getBoolean("done") || lt?.done == true)
            StudyTask(id, o.getString("name"), min, sec, done)
        }
        if (reset) { Store.clearActive(c); Store.setEpoch(c, srvEpoch) }
        Store.save(c, merged)
        Store.setFreeRemote(c, r.optBoolean("free"))
    }
}
