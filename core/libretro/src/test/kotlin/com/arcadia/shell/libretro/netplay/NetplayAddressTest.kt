package com.arcadia.shell.libretro.netplay

import org.junit.Assert.assertEquals
import org.junit.Test

class NetplayAddressTest {

    @Test
    fun nudgeWrapsOctet() {
        assertEquals("192.168.1.2", nudgeIpv4("192.168.1.1", 3, 1))
        assertEquals("192.168.1.0", nudgeIpv4("192.168.1.255", 3, 1))
        assertEquals("10.0.0.1", nudgeIpv4("10.0.0.1", 0, 0))
    }

    @Test
    fun parsePadsMissingOctets() {
        val parts = parseIpv4("10.4")
        assertEquals(10, parts[0])
        assertEquals(4, parts[1])
        assertEquals(0, parts[2])
        assertEquals(0, parts[3])
    }

    @Test
    fun parseJoinHostPortSplitsHostAndPort() {
        val parsed = parseJoinHostPort("192.168.1.10:56000", 55435)
        assertEquals("192.168.1.10", parsed.host)
        assertEquals(56000, parsed.port)
    }

    @Test
    fun parseJoinHostPortKeepsBareHost() {
        val parsed = parseJoinHostPort("10.0.0.4", 55435)
        assertEquals("10.0.0.4", parsed.host)
        assertEquals(55435, parsed.port)
    }

    @Test
    fun parseJoinHostPortClearsBlank() {
        val parsed = parseJoinHostPort("  ", 55435)
        assertEquals("", parsed.host)
        assertEquals(55435, parsed.port)
    }

    @Test
    fun formatJoinHostPortOmitsBlankHost() {
        assertEquals("", formatJoinHostPort("  ", 55435))
        assertEquals("192.168.1.10:55435", formatJoinHostPort("192.168.1.10", 55435))
    }
}
