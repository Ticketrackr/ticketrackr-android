package com.ticketrackr.android

import org.json.JSONObject
import org.json.JSONTokener

/** What the support page tells the app (sdks/protocol, section 3). Events only, never customer data. */
sealed class SupportEvent {
    /** Support has loaded and signed in. */
    object Ready : SupportEvent()

    /** The customer pressed Close. */
    object Close : SupportEvent()

    /** The session expired or was revoked: support needs a new link. */
    object SessionEnded : SupportEvent()

    /** The customer's unread replies, whenever the number changes. */
    data class Unread(val count: Int) : SupportEvent()

    /**
     * A token that reads the customer's unread replies while support is closed (sdks/protocol, section 7), until
     * [expiresAt], in milliseconds since 1970.
     */
    data class UnreadToken(val token: String, val expiresAt: Long) : SupportEvent() {
        // The token is a credential, so it stays out of logs.
        override fun toString() = "UnreadToken(expiresAt=$expiresAt)"
    }

    companion object {
        private val TOKEN = Regex("^trk_unread_[A-Za-z0-9_-]{43}$")

        /** The event in a message from the page (a JSON string), or null for anything else. */
        @JvmStatic
        fun read(data: String): SupportEvent? {
            val message = jsonObject(data) ?: return null
            if (message.opt("source") != "ticketrackr-support") return null
            return when (message.opt("event")) {
                "ready" -> Ready
                "close" -> Close
                "session-ended" -> SessionEnded
                "unread" -> jsonCount(message.opt("count"))?.let { Unread(it) }
                "unread-token" -> {
                    val token = (message.opt("token") as? String)?.takeIf { TOKEN.matches(it) }
                    val expiresAt = jsonTime(message.opt("expiresAt"))
                    if (token != null && expiresAt != null) UnreadToken(token, expiresAt) else null
                }
                else -> null
            }
        }
    }
}

/** The JSON object in [text], or null for anything else (invalid JSON, an array, a number). */
internal fun jsonObject(text: String?): JSONObject? {
    if (text == null) return null
    return try {
        JSONTokener(text).nextValue() as? JSONObject
    } catch (error: Exception) {
        null
    }
}

/** A whole number of zero or more; not text, a fraction or true/false. */
internal fun jsonCount(value: Any?): Int? = when (value) {
    is Int -> value.takeIf { it >= 0 }
    is Long -> value.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
    is Double -> value.takeIf { it >= 0 && it == Math.floor(it) && it <= Int.MAX_VALUE }?.toInt()
    else -> null
}

/** A whole number above zero, like a time in milliseconds since 1970; not text, a fraction or true/false. */
internal fun jsonTime(value: Any?): Long? = when (value) {
    is Int -> value.takeIf { it > 0 }?.toLong()
    is Long -> value.takeIf { it > 0 }
    is Double -> value.takeIf { it > 0 && it == Math.floor(it) && it <= Long.MAX_VALUE }?.toLong()
    else -> null
}
