package com.ticketrackr.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * A TicketRackr page opened from support (the status page, a help article), with Back. It shares support's sign-in, so
 * a file opened from it downloads without signing in again.
 */
class TicketRackrPageActivity : ComponentActivity() {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(URL) ?: return finish()
        val origin = intent.getStringExtra(ORIGIN) ?: return finish()
        val words = SupportWords.forLanguage(intent.getStringExtra(LANGUAGE))
        val density = resources.displayMetrics.density

        val back = TextView(this).apply {
            text = "‹  ${words.back}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(0xFF0F172A.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
            isClickable = true
            isFocusable = true
            contentDescription = words.back
            setOnClickListener { finish() }
        }
        val bar = LinearLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(back, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (52 * density).toInt()))
        }
        val line = View(this).apply { setBackgroundColor(0xFFE2E8F0.toInt()) }
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val next = request.url.toString()
                    return when (SupportDestination.of(next, origin)) {
                        SupportDestination.PAGE, SupportDestination.FILE -> false
                        // A link back to support: support is right underneath.
                        SupportDestination.SUPPORT -> {
                            finish()
                            true
                        }
                        SupportDestination.OUTSIDE -> {
                            openOutside(this@TicketRackrPageActivity, next)
                            true
                        }
                    }
                }
            }
            setDownloadListener { file, userAgent, disposition, mimeType, _ -> SupportDownloads.start(this@TicketRackrPageActivity, file, userAgent, disposition, mimeType, words) }
        }
        webView = web
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(bar)
            addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, density.toInt())))
            addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        Screen.fill(this, root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
        web.loadUrl(url)
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    internal companion object {
        private const val URL = "url"
        private const val ORIGIN = "origin"
        private const val LANGUAGE = "language"

        fun open(context: Context, url: String, origin: String, language: String?) {
            val intent = Intent(context, TicketRackrPageActivity::class.java)
                .putExtra(URL, url)
                .putExtra(ORIGIN, origin)
                .putExtra(LANGUAGE, language)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
