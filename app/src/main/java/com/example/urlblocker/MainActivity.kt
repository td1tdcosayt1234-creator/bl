package com.example.urlblocker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.urlblocker.databinding.ActivityMainBinding
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var authed = false

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) doStartVpn()
        else Toast.makeText(this, "VPN permission na dile full-device block hobe na", Toast.LENGTH_LONG).show()
    }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { doStartVpn() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.listApps.layoutManager = LinearLayoutManager(this)
        BlockManager.loadCache(this)
        BlockManager.seedDefaults(this)

        if (!PinManager.hasPin(this)) {
            askNewPin()
        } else {
            askPin("PIN din", allowCancel = false) { authed = true; refresh() }
        }

        binding.btnAdd.setOnClickListener {
            if (!authed) return@setOnClickListener
            val raw = binding.etUrl.text?.toString()?.trim() ?: ""
            if (raw.isEmpty()) {
                Toast.makeText(this, "Age URL likho", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val norm = BlockManager.normalize(raw)
            if (norm == null) {
                Toast.makeText(this, "Sothik domain din (ex: facebook.com)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (BlockManager.getList(this).contains(norm)) {
                Toast.makeText(this, "Already blocked", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            BlockManager.add(this, raw)
            binding.etUrl.text?.clear()
            refresh()
            Toast.makeText(this, "Blocked: $norm", Toast.LENGTH_SHORT).show()
        }

        binding.btnStart.setOnClickListener { startBlock() }
        binding.btnStop.setOnClickListener {
            askPin("Stop korte PIN din", allowCancel = true) { stopBlock() }
        }
        binding.btnBrowser.setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }
        binding.swFullLock.setOnCheckedChangeListener { _, on ->
            if (!authed) { refresh(); return@setOnCheckedChangeListener }
            if (on == BlockManager.isFullLock(this)) return@setOnCheckedChangeListener
            askPin(if (on) "FULL LOCK ON korte PIN din" else "FULL LOCK OFF korte PIN din", allowCancel = true) {
                applyFullLock(on)
            }
            refresh()
        }
        binding.btnAddApp.setOnClickListener {
            if (!authed) return@setOnClickListener
            val pkg = binding.etApp.text?.toString()?.trim() ?: ""
            if (!BlockManager.addLockedApp(this, pkg)) {
                Toast.makeText(this, "Sothik package din (ex: com.zhiliaoapp.musically)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.etApp.text?.clear()
            refresh()
            Toast.makeText(this, "App locked: $pkg", Toast.LENGTH_SHORT).show()
        }
        binding.btnEnableAppLock.setOnClickListener {
            Toast.makeText(this, "Accessibility list theke 'URL Blocker' ON koro", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {}
        }

        refresh()
    }

    private fun refresh() {
        val list = BlockManager.getList(this).sorted()
        binding.list.adapter = BlockAdapter(list) { host ->
            // BUG FIX: accidental delete atkate confirm dialog
            AlertDialog.Builder(this)
                .setTitle("Delete?")
                .setMessage("$host unblock korbo?")
                .setPositiveButton("Delete") { _, _ ->
                    BlockManager.remove(this, host)
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        val apps = BlockManager.getLockedApps(this).sorted()
        binding.listApps.adapter = BlockAdapter(apps) { pkg ->
            AlertDialog.Builder(this)
                .setTitle("Unlock app?")
                .setMessage("$pkg AppLock theke sorabo?")
                .setPositiveButton("Remove") { _, _ ->
                    BlockManager.removeLockedApp(this, pkg)
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.tvCount.text = list.size.toString()
        val running = MyVpnService.isRunning
        val full = BlockManager.isFullLock(this)
        if (binding.swFullLock.isChecked != full) binding.swFullLock.isChecked = full
        binding.tvStatus.text = if (!running) "Block OFF" else if (full) "FULL LOCK ON" else "Block ON"
        binding.tvSub.text = if (!running) "VPN off • site gulo khola"
            else if (full) "VPN cholche • full mobile net bondho"
            else "VPN cholche • site gulo bondho"
        binding.dotStatus.setBackgroundResource(
            if (running) R.drawable.dot_bg_on else R.drawable.dot_bg_off
        )
        binding.btnStart.isEnabled = !running
        binding.btnStop.isEnabled = running
    }

    override fun onResume() {
        super.onResume()
        if (authed) refresh()
    }

    private fun startBlock() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val prep = try { VpnService.prepare(this) } catch (_: Exception) { null }
        if (prep != null) {
            try { vpnLauncher.launch(prep) } catch (_: Exception) {
                Toast.makeText(this, "VPN open kora gelo na", Toast.LENGTH_SHORT).show()
            }
        } else {
            doStartVpn()
        }
    }

    private fun doStartVpn() {
        try {
            val i = Intent(this, MyVpnService::class.java).setAction("START")
                .putExtra("FULL_LOCK", BlockManager.isFullLock(this))
            ContextCompat.startForegroundService(this, i)
            BlockManager.setRunning(this, true)
            refresh()
            Toast.makeText(this, if (BlockManager.isFullLock(this)) "FULL LOCK ON" else "Block ON", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Start fail: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyFullLock(on: Boolean) {
        BlockManager.setFullLock(this, on)
        // mode change VPN rebuild chara karjokor hoy na, cholle restart dao
        if (MyVpnService.isRunning) {
            try {
                startService(Intent(this, MyVpnService::class.java).setAction("STOP"))
            } catch (_: Exception) {}
            try {
                val i = Intent(this, MyVpnService::class.java).setAction("START")
                    .putExtra("FULL_LOCK", on)
                ContextCompat.startForegroundService(this, i)
            } catch (e: Exception) {
                Toast.makeText(this, "Restart fail: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        refresh()
        Toast.makeText(this, if (on) "FULL LOCK ready (Start chaple karjokor)" else "Full lock off", Toast.LENGTH_SHORT).show()
    }

    private fun stopBlock() {
        try {
            // service already running, normal startService enough
            startService(Intent(this, MyVpnService::class.java).setAction("STOP"))
        } catch (_: Exception) {}
        BlockManager.setRunning(this, false)
        refresh()
    }

    private fun askNewPin() {
        val et = TextInputEditText(this).apply {
            hint = "4-digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("Notun PIN set korun").setView(et)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val pin = et.text?.toString()?.trim() ?: ""
                if (pin.length < 4 || !pin.all { it.isDigit() }) {
                    Toast.makeText(this, "Minimum 4 digit number", Toast.LENGTH_SHORT).show()
                    askNewPin()
                } else {
                    PinManager.setPin(this, pin)
                    authed = true
                    refresh()
                }
            }.show()
    }

    private fun askPin(title: String, allowCancel: Boolean, onOk: () -> Unit) {
        val et = TextInputEditText(this).apply {
            hint = "PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val b = AlertDialog.Builder(this).setTitle(title).setView(et)
            .setCancelable(allowCancel)
            .setPositiveButton("OK", null)
        if (allowCancel) b.setNegativeButton("Cancel", null)
        val d = b.create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (PinManager.checkPin(this, et.text?.toString()?.trim() ?: "")) {
                    d.dismiss()
                    onOk()
                } else {
                    et.error = "Bhull PIN"
                    Toast.makeText(this, "Bhull PIN, abar din", Toast.LENGTH_SHORT).show()
                    // finish korbo na, retry dite thakbe (bug fix)
                }
            }
        }
        d.show()
        if (!allowCancel) d.setOnCancelListener { finish() }
    }

    class BlockAdapter(
        private val items: List<String>,
        private val onDel: (String) -> Unit
    ) : RecyclerView.Adapter<BlockAdapter.H>() {
        class H(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvHost)
            val btn: View = v.findViewById(R.id.btnDel)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): H {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_blocked, p, false)
            return H(v)
        }
        override fun onBindViewHolder(h: H, pos: Int) {
            h.tv.text = items[pos]
            h.btn.setOnClickListener { onDel(items[pos]) }
        }
        override fun getItemCount() = items.size
    }
}
