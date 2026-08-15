package com.arcadia.shell.xoranetwork

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraNetplayInvitesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val invite = sampleInvite()
        val encoded = XoraNetplayInvites.encodeValue(invite, json)
        val parsed = XoraNetplayInvites.parseValue(encoded, json)
        assertEquals(listOf(invite), parsed)
    }

    @Test
    fun outboxJsonFiltersToSelfAndDropsStale() {
        val now = 1_000_000L
        val forSelf = sampleInvite(to = "pal", createdAtMs = now - 1_000L)
        val forOther = sampleInvite(to = "other", createdAtMs = now - 1_000L)
        val stale = sampleInvite(to = "pal", createdAtMs = now - XoraNetplayInvites.TTL_MS - 1)
        val encoded = XoraNetplayInvites.encodeOutbox(listOf(forSelf, forOther, stale), json)
        val parsed = XoraNetplayInvites.parseValue(encoded, json)
        val kept = XoraNetplayInvites.addressedTo(parsed, "PAL", now)
        assertEquals(listOf(forSelf), kept)
    }

    @Test
    fun mergeOutboxKeepsOneInvitePerFriend() {
        val now = 50_000L
        val first = sampleInvite(to = "pal", code = "AAAAAA", createdAtMs = now - 2_000L)
        val second = sampleInvite(to = "pal", code = "BBBBBB", createdAtMs = now)
        val other = sampleInvite(to = "sam", code = "CCCCCC", createdAtMs = now)
        val merged = XoraNetplayInvites.mergeOutbox(listOf(first, other), second, now)
        assertEquals(listOf(second, other), merged)
    }

    @Test
    fun recipientKeyIsStableAndLowercase() {
        assertEquals("to2:pal", XoraNetplayInvites.recipientKey(" Pal "))
    }

    @Test
    fun joinableCodeRejectsShortOrBlank() {
        assertTrue(XoraNetplayInvites.hasJoinableCode(sampleInvite(code = "ABC234")))
        assertFalse(XoraNetplayInvites.hasJoinableCode(sampleInvite(code = "ABC")))
        assertFalse(XoraNetplayInvites.hasJoinableCode(sampleInvite(code = "")))
    }

    private fun sampleInvite(
        to: String = "pal",
        code: String = "ABC234",
        createdAtMs: Long = 10L,
    ) = XoraNetplayInviteRecord(
        code = code,
        toUsername = to,
        gameTitle = "Sonic",
        platformId = "genesis",
        coreName = "genesis_plus_gx",
        fromUsername = "host",
        fromDisplayName = "Host",
        createdAtMs = createdAtMs,
    )
}
