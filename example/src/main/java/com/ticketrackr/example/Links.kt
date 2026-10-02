package com.ticketrackr.example

import android.content.Intent
import com.ticketrackr.android.SupportLinkProvider
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import org.json.JSONObject

/**
 * Asks your server for a support link: it makes a customer session and link for the signed-in customer and returns
 * `{"url": …}` (see the README). This example asks TicketRackr running on a computer (reached through adb reverse).
 */
fun supportLinks(intent: Intent): SupportLinkProvider {
    val endpoint = intent.getStringExtra("supportLinkUrl") ?: "http://localhost:4390/support-link"
    return SupportLinkProvider { callback ->
        thread {
            try {
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.outputStream.use { it.write("{}".toByteArray()) }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                callback.onLink(JSONObject(body).getString("url"))
            } catch (error: Exception) {
                callback.onError(error)
            }
        }
    }
}
