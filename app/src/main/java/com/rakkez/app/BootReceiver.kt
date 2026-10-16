package com.rakkez.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Store.isLocked(c)) {
            try { c.startForegroundService(Intent(c, BlockerService::class.java)) } catch (_: Exception) {}
        }
    }
}
