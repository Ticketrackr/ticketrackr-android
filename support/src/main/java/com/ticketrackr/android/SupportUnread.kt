package com.ticketrackr.android

/**
 * The Help button's badge while support is closed (sdks/protocol, section 7). Each time support opens it hands the app
 * a token that reads the customer's unread replies, and the SDK asks TicketRackr with it: no support link, no session.
 */
object SupportUnread {
    /** How to ask with a kept [token], at its support page's [origin]. */
    @JvmStatic
    fun request(origin: String, token: String): UnreadRequest = UnreadRequest("${origin.trimEnd('/')}/api/support/unread", "Bearer $token")

    /** What TicketRackr's answer (its HTTP status and body) means for the badge. */
    @JvmStatic
    fun answer(status: Int, body: String?): UnreadAnswer = when (status) {
        200 -> jsonCount(jsonObject(body)?.opt("unread"))?.let { UnreadAnswer.Count(it) } ?: UnreadAnswer.Keep
        401, 403 -> UnreadAnswer.Forget
        else -> UnreadAnswer.Keep
    }

    /** Whether a kept token is still used: until [expiresAt], in milliseconds since 1970. */
    @JvmStatic
    @JvmOverloads
    fun isUsable(expiresAt: Long, nowMs: Long = System.currentTimeMillis()): Boolean = nowMs < expiresAt
}

/** `GET` [url] with the header `Authorization: <authorization>`. */
data class UnreadRequest(val url: String, val authorization: String) {
    // The header holds the token, so it stays out of logs.
    override fun toString() = "UnreadRequest(url=$url)"
}

/** What an answer about the unread count means. */
sealed class UnreadAnswer {
    /** Show this many on the badge (none for 0). */
    data class Count(val count: Int) : UnreadAnswer()

    /** The token was refused (401 or 403): forget it, and the badge shows nothing until support opens again. */
    object Forget : UnreadAnswer()

    /** Anything else (another status, a bad body, no connection): keep the badge as it was and ask again next time. */
    object Keep : UnreadAnswer()
}

/** Automatic checks of the unread count: at most one a minute, the first always. */
class UnreadGuard @JvmOverloads constructor(private val intervalMs: Long = 60_000) {
    private var last: Long? = null

    /** Whether to check now. */
    @JvmOverloads
    fun allow(nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = last
        if (last != null && nowMs - last < intervalMs) return false
        this.last = nowMs
        return true
    }
}

/** A kept token: its support page's origin, when it expires (milliseconds since 1970), and the last count it got. */
internal data class KeptToken(val origin: String, val token: String, val expiresAt: Long, val count: Int?)

/** Where the badge keeps its token across launches: SharedPreferences in an app. */
internal interface UnreadStorage {
    fun read(): KeptToken?

    fun write(kept: KeptToken?)
}

/** A question for TicketRackr, and which version of what was kept it was asked from. */
internal class UnreadQuestion(val request: UnreadRequest, val change: Int)

/**
 * The badge for the whole app: the latest token support handed over, the last count, and the one guard every automatic
 * check shares.
 */
internal class UnreadBadge(private val storage: UnreadStorage, private val guard: UnreadGuard = UnreadGuard()) {
    // The count support last reported, even before its token arrived: it sends the count first.
    private var reported: Int? = null

    // Each change counts, so the answer to a question asked before one (support's own count, a new token, signing out)
    // is dropped instead of undoing it.
    private var changes = 0

    /** Keeps the token from an `unread-token` event, replacing any earlier one, with the count support reported. */
    @Synchronized
    fun keep(origin: String, token: String, expiresAt: Long) {
        write(KeptToken(origin, token, expiresAt, reported ?: storage.read()?.count))
    }

    /** The count support reported while open. */
    @Synchronized
    fun note(count: Int) {
        reported = count
        storage.read()?.let { write(it.copy(count = count)) }
    }

    /** The last count known, without asking: null without a token that can still be used. */
    @Synchronized
    fun last(nowMs: Long = System.currentTimeMillis()): Int? = storage.read()?.takeIf { SupportUnread.isUsable(it.expiresAt, nowMs) }?.count

    /** What to ask TicketRackr now, or null with nothing to ask with. An expired token is forgotten. */
    @Synchronized
    fun question(nowMs: Long = System.currentTimeMillis()): UnreadQuestion? {
        val kept = storage.read() ?: return null
        if (!SupportUnread.isUsable(kept.expiresAt, nowMs)) {
            write(null)
            return null
        }
        return UnreadQuestion(SupportUnread.request(kept.origin, kept.token), changes)
    }

    /** Applies TicketRackr's [answer], unless what was kept changed since the [question]; then the count to show. */
    @Synchronized
    fun answered(question: UnreadQuestion, answer: UnreadAnswer, nowMs: Long = System.currentTimeMillis()): Int? {
        if (question.change == changes) {
            when (answer) {
                is UnreadAnswer.Count -> storage.read()?.let { write(it.copy(count = answer.count)) }
                UnreadAnswer.Forget -> write(null)
                UnreadAnswer.Keep -> Unit
            }
        }
        return last(nowMs)
    }

    /**
     * Whether an automatic check (the Help button appearing, the app coming back) asks now: at most once a minute in
     * all, counting only checks with a token to ask with.
     */
    @Synchronized
    fun automatic(nowMs: Long): Boolean = storage.read() != null && guard.allow(nowMs)

    /** Forgets the token, the count and what support reported: the app's user signed out. */
    @Synchronized
    fun signOut() {
        reported = null
        write(null)
    }

    private fun write(kept: KeptToken?) {
        changes += 1
        storage.write(kept)
    }
}
