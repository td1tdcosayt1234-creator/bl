package com.example.urlblocker

import android.content.Context

object BlockManager {
    private const val PREFS = "block_prefs"
    private const val KEY_SET = "hosts"
    private const val KEY_RUNNING = "was_running"

    @Volatile private var cached: Set<String>? = null

    fun loadCache(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cached = HashSet(p.getStringSet(KEY_SET, emptySet()) ?: emptySet())
    }

    private fun getCachedOrLoad(ctx: Context): Set<String> {
        return cached ?: run {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val s: Set<String> = HashSet(p.getStringSet(KEY_SET, emptySet()) ?: emptySet())
            cached = s
            s
        }
    }

    fun invalidate() { cached = null }

    fun getList(ctx: Context): MutableSet<String> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return HashSet(p.getStringSet(KEY_SET, emptySet()) ?: emptySet())
    }

    fun add(ctx: Context, rawInput: String): Boolean {
        val host = normalize(rawInput) ?: return false
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = HashSet(p.getStringSet(KEY_SET, emptySet()) ?: emptySet())
        set.add(host)
        p.edit().putStringSet(KEY_SET, set).apply()
        cached = HashSet(set)
        return true
    }

    fun remove(ctx: Context, host: String) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = HashSet(p.getStringSet(KEY_SET, emptySet()) ?: emptySet())
        set.remove(host)
        p.edit().putStringSet(KEY_SET, set).apply()
        cached = HashSet(set)
    }

    fun setRunning(ctx: Context, running: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RUNNING, running).apply()
    }

    fun wasRunning(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RUNNING, false)
    }

    fun normalize(rawInput: String): String? {
        var s = rawInput.trim().lowercase()
        if (s.isEmpty()) return null
        if (s.startsWith("http://")) s = s.removePrefix("http://")
        if (s.startsWith("https://")) s = s.removePrefix("https://")
        s = s.substringBefore("/").substringBefore("?").substringBefore("#")
        s = s.substringBefore(":").trim()
        // BUG FIX: sudhu www. strip korbo, m. strip korle over-block hoy
        // ex: m.example.com input -> m. Soho rakhbo, matching e subdomain check ache
        if (s.startsWith("www.")) s = s.removePrefix("www.")
        if (s.isEmpty() || !s.contains(".")) return null
        if (!s.matches(Regex("^[a-z0-9.-]+$"))) return null
        if (s.startsWith(".") || s.endsWith(".") || s.startsWith("-") || s.endsWith("-")) return null
        if (s.contains("..")) return null
        if (s.length > 253) return null
        return s
    }

    fun normalizeHost(host: String): String {
        var s = host.trim().lowercase().trimEnd('.')
        if (s.startsWith("www.")) s = s.removePrefix("www.")
        return s
    }

    fun isBlocked(ctx: Context, queryHost: String): Boolean {
        val q = normalizeHost(queryHost)
        if (q.isEmpty()) return false
        val list = getCachedOrLoad(ctx)
        for (b in list) {
            if (q == b || q.endsWith(".$b")) return true
        }
        return false
    }
}
