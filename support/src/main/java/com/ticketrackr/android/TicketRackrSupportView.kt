package com.ticketrackr.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * The company's support inside your app: requests and reports with their forms, the conversation, files, the AI
 * assistant and surveys. It fills the view, so place it inside your screen's system bars, or open support with
 * [TicketRackr.openSupport], which does that for you. TicketRackr pages opened from it (the status page, help articles)
 * show on a screen with Back, files go to the system's downloads, and other sites, mail and phone links open in their
 * own apps. Call [destroy] when you're done with it.
 *
 * File uploads use the system's picker, which needs the view to be in a ComponentActivity (AppCompatActivity is one).
 */
class TicketRackrSupportView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    /** What support tells your app. */
    var listener: SupportListener? = null

    private var getSupportLink: SupportLinkProvider? = null
    private var options = SupportOptions()
    private var closable = false
    private var words = SupportWords.forLanguage(null)
    private var webView: WebView? = null
    private var origin: String? = null
    private var loads = 0
    private var request = 0
    private var choosers = 0
    private var reconnect = ReconnectGuard()
    private var files: ValueCallback<Array<Uri>>? = null
    private val main = Handler(Looper.getMainLooper())
    private val status = LinearLayout(context)
    private val spinner = ProgressBar(context)
    private val message = TextView(context)
    private val retry = Button(context)

    init {
        setBackgroundColor(Color.WHITE)
        val padding = (24 * resources.displayMetrics.density).toInt()
        status.orientation = LinearLayout.VERTICAL
        status.gravity = Gravity.CENTER
        status.setPadding(padding, padding, padding, padding)
        message.gravity = Gravity.CENTER
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        message.setTextColor(0xFF334155.toInt())
        retry.isAllCaps = false
        retry.setOnClickListener {
            reconnect = ReconnectGuard()
            open()
        }
        status.addView(spinner)
        status.addView(message)
        status.addView(retry)
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * Shows support.
     *
     * @param getSupportLink gets a new support link from your server.
     * @param options what to open: a request type's form, filled in, in a language.
     * @param closable show a Close button, for support on a screen of its own; [SupportListener.onClose] hears it.
     */
    @JvmOverloads
    fun show(getSupportLink: SupportLinkProvider, options: SupportOptions = SupportOptions(), closable: Boolean = false) {
        this.getSupportLink = getSupportLink
        this.options = options
        this.closable = closable
        words = SupportWords.forLanguage(options.language)
        retry.text = words.retry
        reconnect = ReconnectGuard()
        open()
    }

    /** Releases the web view. Call it when support goes away for good, such as in your Activity's onDestroy. */
    fun destroy() {
        request += 1
        files?.onReceiveValue(null)
        files = null
        webView?.let {
            removeView(it)
            it.destroy()
        }
        webView = null
        origin = null
    }

    /** Gets a new link and shows it. */
    private fun open() {
        val provider = getSupportLink ?: return
        show(loading = true)
        val ticket = ++request
        try {
            provider.getSupportLink(object : SupportLinkCallback {
                override fun onLink(url: String) {
                    main.post { if (ticket == request) load(url) }
                }

                override fun onError(error: Throwable) {
                    main.post { if (ticket == request) failed(error) }
                }
            })
        } catch (error: Exception) {
            failed(error)
        }
    }

    private fun load(link: String) {
        // Android places support inside the system bars (see TicketRackr.openSupport), so the page needn't keep clear of
        // them itself (edges=0): the window's edges Android tells pages aren't this view's own.
        val url = try {
            SupportAddress.frameUrl(link, options, closable, edges = false, load = ++loads)
        } catch (error: Exception) {
            return failed(error)
        }
        val origin = SupportOrigin.of(url) ?: return failed(SupportLinkException())
        val web = webView?.takeIf { this.origin == origin } ?: newWebView(origin)
        this.origin = origin
        web.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(origin: String): WebView {
        webView?.let {
            removeView(it)
            it.destroy()
        }
        val web = WebView(context)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        // The page tells the app what happened through window.TicketRackrSupportBridge (sdks/protocol, section 3).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, BRIDGE, setOf(origin)) { _, message, sourceOrigin, isMainFrame, _ ->
                if (isMainFrame && SupportOrigin.isSupport(sourceOrigin.toString(), origin)) message.data?.let(::received)
            }
        } else {
            web.addJavascriptInterface(Bridge(), BRIDGE)
        }
        web.webViewClient = Client()
        web.webChromeClient = Chrome()
        web.setDownloadListener { url, userAgent, disposition, mimeType, _ -> SupportDownloads.start(context, url, userAgent, disposition, mimeType, words) }
        web.visibility = INVISIBLE
        addView(web, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        webView = web
        return web
    }

    private fun received(data: String) {
        when (val event = SupportEvent.read(data) ?: return) {
            SupportEvent.Ready -> {
                show()
                listener?.onReady()
            }
            is SupportEvent.Unread -> listener?.onUnreadChange(event.count)
            SupportEvent.Close -> listener?.onClose()
            SupportEvent.SessionEnded -> if (reconnect.allow()) open() else show(failed = true)
        }
    }

    private fun show(loading: Boolean = false, failed: Boolean = false) {
        status.visibility = if (loading || failed) VISIBLE else GONE
        spinner.visibility = if (loading) VISIBLE else GONE
        message.text = if (failed) words.failed else words.loading
        retry.visibility = if (failed) VISIBLE else GONE
        webView?.visibility = if (loading || failed) INVISIBLE else VISIBLE
    }

    private fun failed(error: Throwable) {
        Log.e(TAG, "Support couldn't open.", error)
        show(failed = true)
    }

    /** Whether a link from the page was opened outside it: TicketRackr pages on a screen with Back, others in their apps. */
    private fun routed(url: String): Boolean {
        val origin = origin ?: return false
        return when (SupportDestination.of(url, origin)) {
            // A file stays: the web view hands it to the system's downloads, and support stays as it is.
            SupportDestination.SUPPORT, SupportDestination.FILE -> false
            SupportDestination.PAGE -> {
                TicketRackrPageActivity.open(context, url, origin, options.language)
                true
            }
            SupportDestination.OUTSIDE -> {
                openOutside(context, url)
                true
            }
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Frames inside the page load as they are.
            if (!request.isForMainFrame) return false
            val url = request.url.toString()
            return url != "about:blank" && routed(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (view == webView) show()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            // Only the page itself; a file turning into a download stops its navigation, which isn't a failure.
            val origin = origin ?: return
            if (view != webView || !request.isForMainFrame || SupportDestination.of(request.url.toString(), origin) == SupportDestination.FILE) return
            failed(IllegalStateException("${error.errorCode} ${error.description}"))
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Android ended the page to save memory: Try again starts a new one.
            if (view == webView) {
                removeView(view)
                view.destroy()
                webView = null
                this@TicketRackrSupportView.origin = null
                show(failed = true)
            }
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            files?.onReceiveValue(null)
            files = null
            val activity = context.findActivity() as? ComponentActivity
            if (activity == null) {
                Log.w(TAG, "File uploads need support in a ComponentActivity.")
                callback.onReceiveValue(null)
                return true
            }
            files = callback
            lateinit var launcher: ActivityResultLauncher<Intent>
            launcher = activity.activityResultRegistry.register("ticketrackr-files-${++choosers}", ActivityResultContracts.StartActivityForResult()) { result ->
                files?.onReceiveValue(chosen(result.resultCode, result.data))
                files = null
                launcher.unregister()
            }
            val intent = params.createIntent()
            if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            try {
                launcher.launch(intent)
            } catch (error: ActivityNotFoundException) {
                files?.onReceiveValue(null)
                files = null
                launcher.unregister()
            }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // Support doesn't use the camera or microphone from the page.
            request.deny()
        }
    }

    /** Without web message listeners (an older WebView), the page calls this by name; only the support page counts. */
    inner class Bridge {
        @JavascriptInterface
        fun postMessage(data: String) {
            main.post {
                val origin = origin ?: return@post
                if (SupportOrigin.isSupport(webView?.url, origin)) received(data)
            }
        }
    }

    private companion object {
        const val TAG = "TicketRackr"
        const val BRIDGE = "TicketRackrSupportBridge"

        fun chosen(resultCode: Int, data: Intent?): Array<Uri>? {
            if (resultCode != Activity.RESULT_OK || data == null) return null
            data.clipData?.let { clip -> return Array(clip.itemCount) { clip.getItemAt(it).uri } }
            return data.data?.let { arrayOf(it) }
        }

        fun Context.findActivity(): Activity? {
            var current: Context = this
            while (current is ContextWrapper) {
                if (current is Activity) return current
                current = current.baseContext
            }
            return null
        }
    }
}

/** Another site, or a mail or phone link, in the app that opens it. */
internal fun openOutside(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        Log.w("TicketRackr", "Nothing on this device opens $url.")
    }
}
