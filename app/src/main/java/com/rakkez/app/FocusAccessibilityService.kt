package com.rakkez.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

// النظام نفسه بيشغّل الخدمة دي ومش بتتقفل لما التطبيق يتسحب من الأخيرة.
// شغلتها: ترجّع BlockerService لو اتقتلت، وتمنع التطبيق الممنوع لو BlockerService مش شغالة.
class FocusAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastKick = 0L

    private val watchdog = object : Runnable {
        override fun run() {
            ensureBlocker()
            handler.postDelayed(this, 30_000)
        }
    }

    private fun ensureBlocker() {
        try { startForegroundService(Intent(this, BlockerService::class.java)) } catch (_: Exception) {}
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!Store.isLocked(this) || pkg !in Store.blocked(this)) return
        val now = System.currentTimeMillis()
        // لو BlockerService شغالة وسليمة هي اللي بتتصرف
        if (now - BlockerService.beat < 5_000) return
        if (now - lastKick < 1_500) return
        lastKick = now
        ensureBlocker()
        performGlobalAction(GLOBAL_ACTION_HOME)
        try {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (_: Exception) {}
        Store.log(this, "blocked", label(pkg))
    }

    private fun label(pkg: String): String =
        try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
        catch (_: Exception) { pkg }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        super.onDestroy()
    }
}
