package com.arcadia.shell.xoranetwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XoraListeningStatusTest {

    @Test
    fun roundTripsTrackAndArtist() {
        val encoded = encodeXoraListening("Its Going Down", "Persona 3 RELOAD")
        assertEquals("Listening to Its Going Down by Persona 3 RELOAD", encoded)
        assertEquals("Its Going Down" to "Persona 3 RELOAD", parseXoraListening(encoded))
    }

    @Test
    fun keepsTheLastByAsTheSplitSoSongTitlesMayContainBy() {
        val encoded = encodeXoraListening("Stand by Me", "Ben E. King")
        assertEquals("Stand by Me" to "Ben E. King", parseXoraListening(encoded))
    }

    @Test
    fun missingArtistLeavesTheSongAlone() {
        val encoded = encodeXoraListening("Untitled", "")
        assertEquals("Listening to Untitled", encoded)
        assertEquals("Untitled" to "", parseXoraListening(encoded))
    }

    @Test
    fun blankTitleEncodesToNothing() {
        assertEquals("", encodeXoraListening("  ", "Someone"))
    }

    @Test
    fun otherStatusesAreNotListening() {
        assertNull(parseXoraListening("Playing Super Mario 64"))
        assertNull(parseXoraListening("Online"))
        assertNull(parseXoraListening("spicy steak ramen pls KO me rn"))
        assertNull(parseXoraListening("Listening to "))
    }
}
