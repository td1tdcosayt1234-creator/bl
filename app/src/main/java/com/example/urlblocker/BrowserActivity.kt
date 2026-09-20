package com.example.urlblocker

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.urlblocker.databinding.ActivityBrowserBinding
import java.io.ByteArrayInputStream

class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.web.settings.javaScriptEnabled = true
        binding.web.settings.domStorageEnabled = true
        binding.web.settings.loadWithOverviewMode = true
        binding.web.settings.useWideViewPort = true

        binding.web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progress.progress = newProgress
                binding.progress.visibility =
                    if (newProgress in 1..99) android.view.View.VISIBLE
                    else android.view.View.GONE
            }
        }

        binding.web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                val host = try { android.net.Uri.parse(url).host } catch (_: Exception) { null }
                if (host != null && BlockManager.isBlocked(this@BrowserActivity, host)) {
                    view.stopLoading()
                    view.loadData(blockedHtml(host), "text/html", "utf-8")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return false
                if (BlockManager.isBlocked(this@BrowserActivity, host)) {
                    view.loadData(blockedHtml(host), "text/html", "utf-8")
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host ?: return null
                if (BlockManager.isBlocked(this@BrowserActivity, host)) {
                    val html = blockedHtml(host)
                    return WebResourceResponse("text/html", "utf-8", 200, "OK",
                        mapOf("Content-Type" to "text/html"), ByteArrayInputStream(html.toByteArray()))
                }
                return null
            }
        }

        binding.btnGo.setOnClickListener {
            var u = binding.etBrowserUrl.text?.toString()?.trim() ?: ""
            if (u.isEmpty()) return@setOnClickListener
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            try { binding.web.loadUrl(u) } catch (_: Exception) {}
        }

        binding.web.loadUrl("https://www.google.com")
    }

    private fun blockedHtml(host: String): String {
        return """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
        <body style="font-family:sans-serif;text-align:center;padding:40px;background:#FFF3E0">
        <div style="max-width:420px;margin:auto;border:1px solid #ddd;border-radius:16px;padding:24px;background:#fff">
        <h2 style="color:#C62828">Blocked</h2><p><b>$host</b><br/>URL Blocker diye bondho kora ache.</p>
        </div></body></html>"""
    }

    override fun onDestroy() {
        try {
            binding.web.stopLoading()
            binding.web.destroy()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.web.canGoBack()) binding.web.goBack()
        else super.onBackPressed()
    }
}
