package com.example.urlblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MyVpnService : VpnService() {

    companion object {
        @Volatile var isRunning = false
    }

    private var tun: ParcelFileDescriptor? = null
    @Volatile private var runLoop = false
    private var reader: ExecutorService? = null
    private var dnsPool: ExecutorService? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startVpn()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        // user system settings theke VPN off korle
        BlockManager.setRunning(this, false)
        stopVpn()
        super.onRevoke()
    }

    private fun startVpn() {
        if (isRunning) return
        BlockManager.loadCache(this)
        createNotification()

        val builder = Builder()
            .setSession("URL Blocker")
            .addAddress("10.0.0.2", 32)
            .addRoute("8.8.8.8", 32)
            .addRoute("8.8.4.4", 32)
            .addRoute("1.1.1.1", 32)
            .addRoute("1.0.0.1", 32)
            .addRoute("9.9.9.9", 32)
            .addRoute("208.67.222.222", 32)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setMtu(1500)
        // BUG FIX: IPv6 bypass atkate IPv4 force, blocking mode default (true)
        try { builder.allowFamily(OsConstants.AF_INET) } catch (_: Exception) {}

        try {
            val pi = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setConfigureIntent(pi)
        } catch (_: Exception) {}

        tun = try { builder.establish() } catch (_: Exception) { null }
        if (tun == null) {
            isRunning = false
            stopSelf()
            return
        }
        isRunning = true
        runLoop = true
        reader = Executors.newSingleThreadExecutor()
        dnsPool = Executors.newFixedThreadPool(4)
        reader?.execute(::loop)
    }

    private fun stopVpn() {
        runLoop = false
        isRunning = false
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        try { reader?.shutdownNow() } catch (_: Exception) {}
        try { dnsPool?.shutdownNow() } catch (_: Exception) {}
        reader = null
        dnsPool = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotification() {
        val chId = "blocker"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                nm.createNotificationChannel(
                    NotificationChannel(chId, "URL Blocker", NotificationManager.IMPORTANCE_LOW)
                )
            } catch (_: Exception) {}
        }
        val n: Notification = NotificationCompat.Builder(this, chId)
            .setContentTitle("URL Blocker ON")
            .setContentText("Blocked site gulo device-wide bondho ache")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
        // BUG FIX: SPECIAL_USE type sudhu Android 14+ e, naile crash
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, n)
            }
        } catch (_: Exception) {
            try { startForeground(1, n) } catch (_: Exception) {}
        }
    }

    private fun loop() {
        val fd = tun ?: return
        val input: FileInputStream
        val output: FileOutputStream
        try {
            input = FileInputStream(fd.fileDescriptor)
            output = FileOutputStream(fd.fileDescriptor)
        } catch (_: Exception) { return }
        val packet = ByteArray(32767)
        while (runLoop) {
            try {
                val len = input.read(packet)
                if (len <= 0) {
                    try { Thread.sleep(10) } catch (_: Exception) { break }
                    continue
                }
                handlePacket(packet.copyOf(len), output)
            } catch (_: Exception) {
                if (!runLoop) break
                try { Thread.sleep(50) } catch (_: Exception) { break }
            }
        }
    }

    private fun handlePacket(pkt: ByteArray, output: FileOutputStream) {
        if (pkt.size < 20) return
        val version = (pkt[0].toInt() shr 4) and 0xF
        if (version != 4) return
        val ihl = (pkt[0].toInt() and 0xF) * 4
        if (ihl < 20 || pkt.size < ihl + 8) return
        val proto = pkt[9].toInt() and 0xFF
        if (proto != 17) return
        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)
        val udpOff = ihl
        val srcPort = ((pkt[udpOff].toInt() and 0xFF) shl 8) or (pkt[udpOff + 1].toInt() and 0xFF)
        val dstPort = ((pkt[udpOff + 2].toInt() and 0xFF) shl 8) or (pkt[udpOff + 3].toInt() and 0xFF)
        if (dstPort != 53) return
        val dnsOff = udpOff + 8
        if (dnsOff >= pkt.size) return
        val dnsQuery = pkt.copyOfRange(dnsOff, pkt.size)
        if (dnsQuery.size < 13) return
        val qname = parseQname(dnsQuery) ?: return

        if (BlockManager.isBlocked(this, qname)) {
            val respDns = buildNxDomain(dnsQuery)
            val resp = buildIpUdpPacket(dstIp, srcIp, 53, srcPort, respDns)
            synchronized(output) { try { output.write(resp) } catch (_: Exception) {} }
        } else {
            val sIp = srcIp.clone()
            val dIp = dstIp.clone()
            val pool = dnsPool ?: return
            try {
                pool.execute {
                    try {
                        val upstreamResp = forwardDns(dnsQuery, "8.8.8.8")
                            ?: forwardDns(dnsQuery, "1.1.1.1") ?: return@execute
                        val resp = buildIpUdpPacket(dIp, sIp, 53, srcPort, upstreamResp)
                        synchronized(output) {
                            try { output.write(resp) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private fun forwardDns(query: ByteArray, server: String): ByteArray? {
        var sock: DatagramSocket? = null
        return try {
            sock = DatagramSocket()
            // BUG FIX: protect fail hole VPN loop hobe, tai check must
            if (!protect(sock)) {
                try { sock.close() } catch (_: Exception) {}
                return null
            }
            sock.soTimeout = 5000
            val addr = InetAddress.getByName(server)
            sock.send(DatagramPacket(query, query.size, addr, 53))
            val buf = ByteArray(4096)
            val p = DatagramPacket(buf, buf.size)
            sock.receive(p)
            p.data.copyOf(p.length)
        } catch (_: Exception) { null } finally {
            try { sock?.close() } catch (_: Exception) {}
        }
    }

    private fun parseQname(dns: ByteArray): String? {
        try {
            if (dns.size < 13) return null
            var pos = 12
            val parts = mutableListOf<String>()
            var jumps = 0
            while (pos < dns.size && jumps < 10) {
                val len = dns[pos].toInt() and 0xFF
                if (len == 0) break
                if ((len and 0xC0) == 0xC0) {
                    // compression pointer, query te thakar kotha na
                    return if (parts.isEmpty()) null else parts.joinToString(".")
                }
                if (len > 63 || pos + 1 + len > dns.size) return null
                parts.add(String(dns, pos + 1, len))
                pos += 1 + len
                jumps++
            }
            if (parts.isEmpty()) return null
            return parts.joinToString(".")
        } catch (_: Exception) { return null }
    }

    private fun buildNxDomain(query: ByteArray): ByteArray {
        val resp = query.copyOf(query.size)
        if (resp.size < 12) return resp
        resp[2] = 0x81.toByte()
        resp[3] = 0x83.toByte()
        resp[6] = 0; resp[7] = 0
        resp[8] = 0; resp[9] = 0
        resp[10] = 0; resp[11] = 0
        return resp
    }

    private fun buildIpUdpPacket(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val ipLen = 20
        val udpLen = 8 + payload.size
        val total = ipLen + udpLen
        val out = ByteArray(total)
        out[0] = 0x45
        out[1] = 0
        out[2] = (total shr 8).toByte()
        out[3] = (total and 0xFF).toByte()
        out[4] = 0; out[5] = 0
        out[6] = 0x40; out[7] = 0
        out[8] = 64
        out[9] = 17
        out[10] = 0; out[11] = 0
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)
        val ipChk = ipChecksum(out, 0, 20)
        out[10] = (ipChk shr 8).toByte()
        out[11] = (ipChk and 0xFF).toByte()
        out[20] = (srcPort shr 8).toByte()
        out[21] = (srcPort and 0xFF).toByte()
        out[22] = (dstPort shr 8).toByte()
        out[23] = (dstPort and 0xFF).toByte()
        out[24] = (udpLen shr 8).toByte()
        out[25] = (udpLen and 0xFF).toByte()
        out[26] = 0; out[27] = 0
        System.arraycopy(payload, 0, out, 28, payload.size)
        return out
    }

    private fun ipChecksum(buf: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len) {
            val w = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            sum += w
            i += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
