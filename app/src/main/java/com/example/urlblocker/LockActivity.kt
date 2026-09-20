package com.example.urlblocker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.urlblocker.databinding.ActivityLockBinding

class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private var pkg: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pkg = intent.getStringExtra("PKG") ?: ""
        binding.tvLockPkg.text = pkg.ifEmpty { "Locked app" }

        binding.btnUnlock.setOnClickListener {
            val pin = binding.etPin.text?.toString()?.trim() ?: ""
            if (!PinManager.hasPin(this)) {
                finish()
                return@setOnClickListener
            }
            if (PinManager.checkPin(this, pin)) {
                if (pkg.isNotEmpty()) AppLockService.noteUnlock(pkg)
                finish()
            } else {
                binding.etPin.error = "Bhull PIN"
                Toast.makeText(this, "Bhull PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Back chapleo lock khulbe na, Home e pathao (app background e jabe, lock thakbe)
        try {
            val home = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(home)
        } catch (_: Exception) {}
        super.onBackPressed()
    }
}
