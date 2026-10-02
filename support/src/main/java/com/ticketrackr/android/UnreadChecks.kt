package com.ticketrackr.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The badge's token and count for the whole app, kept in private SharedPreferences across launches, and its questions
 * to TicketRackr: off the main thread, one at a time, answered on the main thread (sdks/protocol, section 7).
 */
internal object UnreadChecks {
    private const val FILE = "com.ticketrackr.android.unread"
    private const val ORIGIN = "origin"
    private const val TOKEN = "token"
    private const val EXPIRES_AT = "expiresAt"
    private const val COUNT = "count"
    private const val TIMEOUT_MS = 10_000

    @Volatile
    private var badge: UnreadBadge? = null
    private val main by lazy { Handler(Looper.getMainLooper()) }

    // Its thread ends when there's nothing left to ask.
    private val asking by lazy {
        ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { Thread(it, "TicketRackr unread").apply { isDaemon = true } }
    }

    /** Where the token and count are kept. Every change to them shows here, for the Help buttons to follow. */
    fun preferences(context: Context): SharedPreferences = (context.applicationContext ?: context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The app's one badge. */
    fun badge(context: Context): UnreadBadge = badge ?: synchronized(this) {
        badge ?: UnreadBadge(Kept(preferences(context))).also { badge = it }
    }

    /** Asks TicketRackr now; [callback] hears on the main thread: the count, or null when it isn't known. */
    fun count(context: Context, callback: UnreadCountCallback) {
        val badge = badge(context)
        val question = badge.question()
        if (question == null) {
            main.post { callback.onUnreadCount(null) }
            return
        }
        asking.execute {
            val answer = ask(question.request)
            main.post { callback.onUnreadCount(badge.answered(question, answer)) }
        }
    }

    /**
     * An automatic check (a Help button appearing, the app coming back): at most once a minute for the whole app, and
     * only when there's a token to ask with. [callback] isn't called when it doesn't ask.
     */
    fun refresh(context: Context, callback: UnreadCountCallback) {
        if (badge(context).automatic(SystemClock.elapsedRealtime())) count(context, callback)
    }

    private fun ask(request: UnreadRequest): UnreadAnswer = try {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            // The token goes to TicketRackr only, never on to wherever a redirect points.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", request.authorization)
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            SupportUnread.answer(status, if (status == 200) connection.inputStream.bufferedReader().use { it.readText() } else null)
        } finally {
            connection.disconnect()
        }
    } catch (error: Exception) {
        // No connection, or no answer in time: the badge stays as it was.
        UnreadAnswer.Keep
    }

    /** The kept token in SharedPreferences: one for the app. */
    private class Kept(private val preferences: SharedPreferences) : UnreadStorage {
        override fun read(): KeptToken? {
            val origin = preferences.getString(ORIGIN, null) ?: return null
            val token = preferences.getString(TOKEN, null) ?: return null
            return KeptToken(origin, token, preferences.getLong(EXPIRES_AT, 0), preferences.getInt(COUNT, -1).takeIf { it >= 0 })
        }

        override fun write(kept: KeptToken?) {
            val editor = preferences.edit()
            if (kept == null) {
                // One by one: before Android 11, listeners don't hear about keys removed with clear().
                editor.remove(ORIGIN).remove(TOKEN).remove(EXPIRES_AT).remove(COUNT)
            } else {
                editor.putString(ORIGIN, kept.origin).putString(TOKEN, kept.token).putLong(EXPIRES_AT, kept.expiresAt)
                if (kept.count != null) editor.putInt(COUNT, kept.count) else editor.remove(COUNT)
            }
            editor.apply()
        }
    }
}
