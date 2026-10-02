package com.ticketrackr.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The badge while support is closed (sdks/protocol, section 7): what's kept, and when TicketRackr is asked. */
class UnreadBadgeTest {
    private val origin = "https://ticketrackr.com"
    private val token = "trk_unread_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ"
    private val next = "trk_unread_QPONMLKJIHGFEDCBAzyxwvutsrqponmlkjihgfedcba"
    private val expiresAt = 2_000L
    private val now = 1_000L
    private val storage = Memory()
    private val badge = UnreadBadge(storage)

    @Test
    fun aCountReportedBeforeTheTokenIsKeptWithIt() {
        // Support sends the count first, then the token once it has one.
        badge.note(2)
        assertNull(badge.last(now))
        badge.keep(origin, token, expiresAt)
        assertEquals(KeptToken(origin, token, expiresAt, 2), storage.kept)
        assertEquals(2, badge.last(now))
        // A new token replaces the old one; support's counts keep following.
        badge.keep(origin, next, expiresAt)
        badge.note(0)
        assertEquals(KeptToken(origin, next, expiresAt, 0), storage.kept)
    }

    @Test
    fun aTokenInALaterLaunchKeepsTheCountAlreadyKept() {
        storage.kept = KeptToken(origin, token, expiresAt, 4)
        UnreadBadge(storage).keep(origin, next, expiresAt)
        assertEquals(KeptToken(origin, next, expiresAt, 4), storage.kept)
    }

    @Test
    fun anAutomaticCheckWithoutATokenDoesntUseUpTheMinute() {
        assertFalse(badge.automatic(0))
        badge.keep(origin, token, expiresAt)
        assertTrue(badge.automatic(1_000))
        assertFalse(badge.automatic(2_000))
        assertTrue(badge.automatic(61_000))
    }

    @Test
    fun answersShowTheCountKeepTheBadgeOrForgetTheToken() {
        badge.keep(origin, token, expiresAt)
        val question = badge.question(now)!!
        assertEquals(SupportUnread.request(origin, token), question.request)
        assertEquals(5, badge.answered(question, UnreadAnswer.Count(5), now))
        assertEquals(5, badge.answered(badge.question(now)!!, UnreadAnswer.Keep, now))
        assertNull(badge.answered(badge.question(now)!!, UnreadAnswer.Forget, now))
        assertNull(storage.kept)
        // An expired token isn't asked with: it's forgotten.
        badge.keep(origin, token, expiresAt)
        assertNull(badge.last(expiresAt))
        assertNull(badge.question(expiresAt))
        assertNull(storage.kept)
    }

    @Test
    fun anAnswerToAQuestionAskedBeforeAChangeDoesntUndoIt() {
        badge.keep(origin, token, expiresAt)
        // Support's own count is newer than an answer on its way.
        val beforeCount = badge.question(now)!!
        badge.note(1)
        assertEquals(1, badge.answered(beforeCount, UnreadAnswer.Count(5), now))
        // A refusal of the old token leaves the new one.
        val beforeToken = badge.question(now)!!
        badge.keep(origin, next, expiresAt)
        assertEquals(1, badge.answered(beforeToken, UnreadAnswer.Forget, now))
        assertEquals(next, storage.kept?.token)
        // Signed out stays signed out.
        val beforeSignOut = badge.question(now)!!
        badge.signOut()
        assertNull(badge.answered(beforeSignOut, UnreadAnswer.Count(5), now))
        assertNull(storage.kept)
    }

    @Test
    fun signingOutForgetsTheTokenTheCountAndWhatSupportReported() {
        badge.note(2)
        badge.keep(origin, token, expiresAt)
        badge.signOut()
        assertNull(storage.kept)
        assertNull(badge.last(now))
        // The next person's token doesn't bring back the last person's count.
        badge.keep(origin, next, expiresAt)
        assertEquals(KeptToken(origin, next, expiresAt, null), storage.kept)
    }

    private class Memory : UnreadStorage {
        var kept: KeptToken? = null

        override fun read() = kept

        override fun write(kept: KeptToken?) {
            this.kept = kept
        }
    }
}
