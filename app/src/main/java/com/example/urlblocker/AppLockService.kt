package com.example.urlblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class AppLockService : AccessibilityService() {

    companion object {
        private const val GRACE_MS = 2 * 60 * 1000L
        private val lastUnlock = mutableMapOf<String, Long>()

        fun noteUnlock(pkg: String) {
            lastUnlock[pkg] = SystemClock.uptimeMillis()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        // nijer app kokhono lock korbo na (MainActivity te PIN gate ache, loop atkate)
        if (pkg == packageName || pkg == "com.android.systemui") return
        val cls = e.className?.toString() ?: ""
        if (cls.contains("LockActivity")) return
        if (!BlockManager.isAppLocked(this, pkg)) return
        if (SystemClock.uptimeMillis() - (lastUnlock[pkg] ?: 0L) < GRACE_MS) return
        try {
            val i = Intent(this, LockActivity::class.java)
                .putExtra("PKG", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}
}
