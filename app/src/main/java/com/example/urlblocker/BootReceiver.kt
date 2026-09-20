package com.example.urlblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!BlockManager.wasRunning(ctx)) return
        // BUG FIX: background theke Activity open kora Android 10+ e block
        // tai VPN already authorized thakle direct service start, naile kichu korbo na
        try {
            if (VpnService.prepare(ctx) == null) {
                val i = Intent(ctx, MyVpnService::class.java).setAction("START")
                ContextCompat.startForegroundService(ctx, i)
            }
        } catch (_: Exception) {}
    }
}
