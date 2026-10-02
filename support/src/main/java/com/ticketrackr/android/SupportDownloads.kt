package com.ticketrackr.android

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast

/** A file opened in support goes to the system's downloads, with a notification to open it from. */
internal object SupportDownloads {
    fun start(context: Context, url: String, userAgent: String?, disposition: String?, mimeType: String?, words: SupportWords) {
        try {
            val name = URLUtil.guessFileName(url, disposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            mimeType?.takeIf { it.isNotEmpty() }?.let { request.setMimeType(it) }
            userAgent?.let { request.addRequestHeader("User-Agent", it) }
            // The page's sign-in, for the file's own address (support's file links also work without it).
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            if (Build.VERSION.SDK_INT >= 29) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            } else {
                // Before Android 10, Downloads needs a storage permission; the app's own folder doesn't.
                request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
            }
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(context, words.downloading, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Log.e("TicketRackr", "The file couldn't be downloaded.", error)
        }
    }
}
