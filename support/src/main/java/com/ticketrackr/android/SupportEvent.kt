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

    companion object {
        /** The event in a message from the page (a JSON string), or null for anything else. */
        @JvmStatic
        fun read(data: String): SupportEvent? {
            val message = try {
                JSONTokener(data).nextValue() as? JSONObject
            } catch (error: Exception) {
                null
            } ?: return null
            if (message.opt("source") != "ticketrackr-support") return null
            return when (message.opt("event")) {
                "ready" -> Ready
                "close" -> Close
                "session-ended" -> SessionEnded
                "unread" -> count(message.opt("count"))?.let { Unread(it) }
                else -> null
            }
        }

        // A whole number of zero or more; not text, a fraction or true/false.
        private fun count(value: Any?): Int? = when (value) {
            is Int -> value.takeIf { it >= 0 }
            is Long -> value.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
            is Double -> value.takeIf { it >= 0 && it == Math.floor(it) && it <= Int.MAX_VALUE }?.toInt()
            else -> null
        }
    }
}
