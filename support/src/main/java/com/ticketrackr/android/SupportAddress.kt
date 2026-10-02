package com.ticketrackr.android

import java.net.URI
import java.net.URLDecoder

/** What to open in support: one request type's form, filled in, in a language. */
data class SupportOptions @JvmOverloads constructor(
    /** Opens the form for one request type, by its key (Settings → Companies → Case types), such as a report. */
    val requestType: String? = null,
    /** Fills in the request's subject. */
    val subject: String? = null,
    /** Fills in the request type's customer-visible fields, by key. */
    val fields: Map<String, String> = emptyMap(),
    /** `en`, `es`, `fr`, `de` or `pt`. The device's language when left out. */
    val language: String? = null,
)

/** A support link that isn't TicketRackr's support page. */
class SupportLinkException : IllegalArgumentException("TicketRackr: getSupportLink must return the support link from POST /v1/support-portal/links.")

/** The address support is shown at (sdks/protocol, section 2). */
object SupportAddress {
    private val FIELD_KEY = Regex("^[a-z][a-z0-9_]{0,63}$")

    /**
     * The support link in embedded mode, with what to open. [closable] adds a Close button; [edges] lets the page keep
     * clear of the status bar and home indicator itself; [load] makes each new link really load.
     */
    @JvmStatic
    @JvmOverloads
    fun frameUrl(link: String, options: SupportOptions = SupportOptions(), closable: Boolean = false, edges: Boolean = false, load: Int = 0): String {
        val uri = supportLink(link)
        val params = mutableListOf("view" to "embed")
        if (closable) params += "closable" to "1"
        if (edges) params += "edges" to "1"
        if (load > 0) params += "load" to load.toString()
        options.language?.takeIf { it.isNotEmpty() }?.let { params += "lang" to it }
        options.requestType?.takeIf { it.isNotEmpty() }?.let { params += "type" to it }
        options.subject?.takeIf { it.isNotEmpty() }?.let { params += "subject" to it }
        for ((key, value) in options.fields.toSortedMap()) if (FIELD_KEY.matches(key)) params += "f.$key" to value.take(500)
        // A later value replaces an earlier one of the same name; the link's own parameters come first.
        val names = params.map { it.first }.toSet()
        val kept = uri.rawQuery?.split('&')?.filter { it.isNotEmpty() && decode(it.substringBefore('=')) !in names }.orEmpty()
        val query = (kept + params.map { "${encode(it.first)}=${encode(it.second)}" }).joinToString("&")
        return "${uri.scheme}://${uri.rawAuthority}${uri.rawPath}?$query#${uri.rawFragment}"
    }

    /** Only TicketRackr's support page, over https (http only while developing on this machine), with its code. */
    private fun supportLink(link: String): URI {
        val uri = try {
            URI(link)
        } catch (error: Exception) {
            throw SupportLinkException()
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val local = host == "localhost" || host == "127.0.0.1"
        if (host == null || !(scheme == "https" || (scheme == "http" && local)) || uri.rawPath != "/support" || uri.rawFragment?.contains("code=") != true) {
            throw SupportLinkException()
        }
        return uri
    }

    // Everything but letters, digits and -._~, so "&", "=", "+" and "#" inside a value stay part of it.
    private fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val char = byte.toInt().toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~") append(char) else append("%%%02X".format(byte.toInt() and 0xff))
        }
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (error: Exception) {
        value
    }
}
