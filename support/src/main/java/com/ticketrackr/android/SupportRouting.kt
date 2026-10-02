package com.ticketrackr.android

import java.net.URI

/** Where a link followed inside support goes (sdks/protocol, section 4). */
enum class SupportDestination {
    /** The support page itself: it stays in support. */
    SUPPORT,

    /** One of support's files (an attachment). */
    FILE,

    /** Another TicketRackr page, like the status page or a help article. */
    PAGE,

    /** Another site, or a mail or phone link. */
    OUTSIDE;

    companion object {
        private val FILE_PATH = Regex("^/api/support/tickets/[A-Za-z0-9_-]+/attachments/[A-Za-z0-9_-]+/download$")

        @JvmStatic
        fun of(url: String, origin: String): SupportDestination {
            if (SupportOrigin.of(url) != origin) return OUTSIDE
            val path = try {
                URI(url).rawPath.orEmpty()
            } catch (error: Exception) {
                return OUTSIDE
            }
            return when {
                path == "/support" -> SUPPORT
                FILE_PATH.matches(path) -> FILE
                else -> PAGE
            }
        }
    }
}

/** Origins: scheme, host and port, as browsers compare them. */
object SupportOrigin {
    /** An address's origin, like `https://ticketrackr.com`, or null for one without a host (`about:blank`, `mailto:`). */
    @JvmStatic
    fun of(url: String?): String? {
        val uri = try {
            URI(url ?: return null)
        } catch (error: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val port = uri.port
        val standard = port == -1 || (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
        return if (standard) "$scheme://$host" else "$scheme://$host:$port"
    }

    /** Whether a message came from the support page. Web views give the page's address or only its origin. */
    @JvmStatic
    fun isSupport(url: String?, origin: String): Boolean = of(url) == origin
}

/** When the session keeps ending (twice in 30 seconds), support stops getting new links and offers Try again. */
class ReconnectGuard @JvmOverloads constructor(private val windowMs: Long = 30_000, private val limit: Int = 2) {
    private val recent = ArrayDeque<Long>()

    /** Whether to get a new link now. */
    @JvmOverloads
    fun allow(nowMs: Long = System.currentTimeMillis()): Boolean {
        while (recent.isNotEmpty() && nowMs - recent.first() > windowMs) recent.removeFirst()
        if (recent.size >= limit) return false
        recent.addLast(nowMs)
        return true
    }
}
