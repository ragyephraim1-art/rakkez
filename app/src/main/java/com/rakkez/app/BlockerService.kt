package com.rakkez.app

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BlockerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var current: String? = null
    private var lastQuery = 0L
    private var lastSync = 0L
    private var lastPoll = 0L

    private val check = object : Runnable {
        override fun run() {
            val locked = Store.isLocked(this@BlockerService)
            val remote = Store.remoteUrl(this@BlockerService).isNotBlank()
            if (!locked && !remote) {
                hideOverlay()
                stopSelf()
                return
            }
            val t = System.currentTimeMillis()
            if (remote && t - lastPoll > 8_000) { lastPoll = t; Remote.poll(this@BlockerService) }
            if (remote && t - lastSync > 120_000) { lastSync = t; Remote.sync(this@BlockerService) }
            if (!locked) {
                hideOverlay()
                handler.postDelayed(this, 2000)
                return
            }
            updateCurrent()
            val pkg = current
            if (pkg != null && pkg in Store.BLOCKED) showOverlay() else hideOverlay()
            handler.postDelayed(this, 500)
        }
    }

    // بنتابع التطبيق اللي اتفتح آخر مرة من أحداث النظام
    private fun updateCurrent() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val from = if (lastQuery == 0L) now - 60_000 else lastQuery - 1000
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) current = e.packageName
        }
        lastQuery = now
    }

    private fun showOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        val left = Store.remaining(this)
        val appName = Store.NAMES[current] ?: current ?: ""
        Store.log(this, "BLOCK", "🚫 حاول يفتح $appName")
        fun tv(t: String, size: Float, color: Int, pad: Int = 0) = TextView(this).apply {
            text = t; textSize = size; setTextColor(color); gravity = Gravity.CENTER; setPadding(0, pad, 0, pad)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(0xFF0F2A2B.toInt())
            setPadding(64, 64, 64, 64)
            addView(tv("📚", 64f, 0xFFFFFFFF.toInt()))
            addView(tv("ارجع ذاكر!", 34f, 0xFFFFB703.toInt(), 12))
            addView(tv("فاضل لك $left مهمة النهارده.\nخلّصهم والموبايل يتفتح كله 💪", 18f, 0xFFEAF2EF.toInt(), 16))
            addView(Button(this@BlockerService).apply {
                text = "افتح ركّز"
                setOnClickListener {
                    hideOverlay()
                    startActivity(Intent(this@BlockerService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            })
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(root, lp)
            overlay = root
        } catch (_: Exception) {}
    }

    private fun hideOverlay() {
        overlay?.let {
            try { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("focus", "ركّز", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "focus")
            .setContentTitle("ركّز شغال 💪")
            .setContentText("خلّص مهامك وكل حاجة هتتفتح")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
        startForeground(1, n)
        handler.removeCallbacks(check)
        handler.post(check)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
