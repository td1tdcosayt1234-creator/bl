package com.example.urlblocker

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

object PinManager {
    private const val PREFS = "pin_prefs"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"

    fun hasPin(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .contains(KEY_HASH)
    }

    fun setPin(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        val hash = sha256(salt + pin)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash)
            .apply()
    }

    fun checkPin(ctx: Context, pin: String): Boolean {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val salt = p.getString(KEY_SALT, null) ?: return false
        val hash = p.getString(KEY_HASH, null) ?: return false
        return sha256(salt + pin) == hash
    }

    private fun sha256(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
