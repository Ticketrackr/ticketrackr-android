package com.ticketrackr.android

import java.net.URI
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The protocol cases every TicketRackr SDK passes (sdks/protocol/conformance.json, copied here). */
class ConformanceTest {
    private val cases = JSONObject(javaClass.classLoader!!.getResource("conformance.json")!!.readText())

    @Test
    fun theAddressSupportIsShownAt() {
        for (item in objects("frameUrl")) {
            val name = item.getString("name")
            val options = item.getJSONObject("options")
            val fields = options.optJSONObject("fields")?.let { json -> json.keys().asSequence().associateWith { json.getString(it) } }.orEmpty()
            val url = SupportAddress.frameUrl(
                item.getString("link"),
                SupportOptions(
                    options.optString("requestType").ifEmpty { null },
                    options.optString("subject").ifEmpty { null },
                    fields,
                    options.optString("language").ifEmpty { null },
                    options.optString("ticket").ifEmpty { null },
                ),
                closable = options.optBoolean("closable"),
                edges = options.optBoolean("edges"),
                load = options.optInt("load"),
            )
            val expect = item.getJSONObject("expect")
            val uri = URI(url)
            val port = if (uri.port == -1) "" else ":${uri.port}"
            assertEquals(name, expect.getString("base"), "${uri.scheme}://${uri.host}$port${uri.path}")
            assertEquals(name, expect.getString("fragment"), uri.fragment)
            val params = uri.rawQuery.split('&').associate { pair ->
                URLDecoder.decode(pair.substringBefore('='), "UTF-8") to URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
            }
            val expected = expect.getJSONObject("params").let { json -> json.keys().asSequence().associateWith { json.getString(it) } }
            assertEquals(name, expected, params)
        }
    }

    @Test
    fun onlyTicketRackrSupportLinksAreShown() {
        val links = cases.getJSONArray("invalidLinks")
        for (index in 0 until links.length()) {
            val link = links.getString(index)
            try {
                SupportAddress.frameUrl(link)
                fail("accepted $link")
            } catch (error: SupportLinkException) {
                assertTrue(link, error.message!!.contains("getSupportLink"))
            }
        }
    }

    @Test
    fun onlyThePagesOwnEventsAreRead() {
        for (item in objects("events")) {
            val data = item.getString("data")
            val expect = item.optJSONObject("expect")
            val expected = if (expect == null) null else when (val event = expect.getString("event")) {
                "ready" -> SupportEvent.Ready
                "close" -> SupportEvent.Close
                "session-ended" -> SupportEvent.SessionEnded
                "unread" -> SupportEvent.Unread(expect.getInt("count"))
                "unread-token" -> SupportEvent.UnreadToken(expect.getString("token"), expect.getLong("expiresAt"))
                else -> throw AssertionError("an event this SDK doesn't know: $event")
            }
            assertEquals(data, expected, SupportEvent.read(data))
        }
    }

    @Test
    fun messagesAreMatchedByOrigin() {
        for (item in objects("origins")) {
            assertEquals(item.getString("url"), item.getBoolean("expect"), SupportOrigin.isSupport(item.getString("url"), item.getString("origin")))
        }
    }

    @Test
    fun whereLinksGo() {
        for (item in objects("destinations")) {
            assertEquals(item.getString("url"), item.getString("expect"), SupportDestination.of(item.getString("url"), item.getString("origin")).name.lowercase())
        }
    }

    @Test
    fun aSessionThatKeepsEndingIsntReconnectedForever() {
        val reconnect = cases.getJSONObject("reconnect")
        val guard = ReconnectGuard(reconnect.getLong("windowMs"), reconnect.getInt("limit"))
        for (call in array(reconnect.getJSONArray("calls"))) {
            assertEquals("at ${call.getLong("at")}", call.getBoolean("expect"), guard.allow(call.getLong("at")))
        }
    }

    @Test
    fun theUnreadCountIsAskedForWithTheKeptToken() {
        for (item in array(unread.getJSONArray("requests"))) {
            val origin = item.getString("origin")
            val request = SupportUnread.request(origin, item.getString("token"))
            val expect = item.getJSONObject("expect")
            assertEquals(origin, expect.getString("url"), request.url)
            assertEquals(origin, expect.getString("authorization"), request.authorization)
        }
    }

    @Test
    fun anAnswerShowsTheCountForgetsTheTokenOrKeepsTheBadge() {
        for (item in array(unread.getJSONArray("answers"))) {
            val status = item.getInt("status")
            val body = item.getString("body")
            val expect = item.getJSONObject("expect")
            val expected = when (val result = expect.getString("result")) {
                "count" -> UnreadAnswer.Count(expect.getInt("count"))
                "forget" -> UnreadAnswer.Forget
                "keep" -> UnreadAnswer.Keep
                else -> throw AssertionError("an answer this SDK doesn't know: $result")
            }
            assertEquals("$status $body", expected, SupportUnread.answer(status, body))
        }
    }

    @Test
    fun automaticChecksAreAtMostOneAMinute() {
        val json = unread.getJSONObject("guard")
        val guard = UnreadGuard(json.getLong("intervalMs"))
        for (call in array(json.getJSONArray("calls"))) {
            assertEquals("at ${call.getLong("at")}", call.getBoolean("expect"), guard.allow(call.getLong("at")))
        }
    }

    @Test
    fun anExpiredTokenIsntUsed() {
        for (item in array(unread.getJSONArray("expiry"))) {
            val expiresAt = item.getLong("expiresAt")
            val now = item.getLong("now")
            assertEquals("$now, expiring at $expiresAt", item.getBoolean("expect"), SupportUnread.isUsable(expiresAt, now))
        }
    }

    @Test
    fun wordsFollowTheLanguage() {
        assertEquals("Ayuda", SupportWords.forLanguage("es").help)
        assertEquals("Voltar", SupportWords.forLanguage("pt-BR").back)
        assertEquals("Help", SupportWords.forLanguage("ja").help)
    }

    private val unread get() = cases.getJSONObject("unread")

    private fun objects(key: String) = array(cases.getJSONArray(key))

    private fun array(json: JSONArray) = (0 until json.length()).map { json.getJSONObject(it) }
}
