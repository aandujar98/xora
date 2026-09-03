package com.arcadia.shell.scraper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class RaHashRulesTest {

    @Test
    fun `nes strips iNES header only when magic present`() {
        val withHeader = RaHashRules.NES_MAGIC + ByteArray(32)
        assertEquals(
            16,
            RaHashRules.headerSizeFor("nes", withHeader.size.toLong(), withHeader, withHeader.size),
        )

        val bare = ByteArray(40) { 0x42 }
        assertEquals(
            0,
            RaHashRules.headerSizeFor("nes", bare.size.toLong(), bare, bare.size),
        )
    }

    @Test
    fun `nes magic wins over wrong extension`() {
        val withHeader = RaHashRules.NES_MAGIC + ByteArray(32) { 1 }
        assertEquals(
            16,
            RaHashRules.headerSizeFor("unf", withHeader.size.toLong(), withHeader, withHeader.size),
        )
    }

    @Test
    fun `snes uses 8KB block heuristic not 1KB`() {
        // 3584 = 3*1024 + 512 — old 1KB heuristic would false-positive; RA wants % 8192 == 512.
        assertEquals(0, RaHashRules.headerSizeFor("sfc", 3584L, ByteArray(16), 16))

        val headed = 8192L + 512L
        assertEquals(512, RaHashRules.headerSizeFor("sfc", headed, ByteArray(16), 16))
    }

    @Test
    fun `n64 byte swaps round-trip to big endian order`() {
        val be = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40)
        val v64 = RaHashRules.swapEveryTwo(be)
        assertArrayEquals(byteArrayOf(0x37, 0x80.toByte(), 0x40, 0x12), v64)
        assertArrayEquals(be, RaHashRules.swapEveryTwo(v64))

        val n64 = RaHashRules.swapEveryFour(be)
        assertArrayEquals(byteArrayOf(0x40, 0x12, 0x37, 0x80.toByte()), n64)
        assertArrayEquals(be, RaHashRules.swapEveryFour(n64))
    }

    @Test
    fun `n64 endian detected from magic not extension`() {
        assertEquals(RaHashRules.N64Endian.Z64, RaHashRules.detectN64Endian(0x80.toByte()))
        assertEquals(RaHashRules.N64Endian.V64, RaHashRules.detectN64Endian(0x37))
        assertEquals(RaHashRules.N64Endian.N64, RaHashRules.detectN64Endian(0x40))
        assertTrue(RaHashRules.isN64Rom(0x80.toByte()))
        assertFalse(RaHashRules.isN64Rom(0x00))
    }

    @Test
    fun `n64 toBigEndian normalizes v64 and n64`() {
        val z64 = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40, 1, 2, 3, 4)
        val v64 = RaHashRules.swapEveryTwo(z64)
        val n64 = RaHashRules.swapEveryFour(z64)
        assertArrayEquals(z64, RaHashRules.toBigEndianN64(v64))
        assertArrayEquals(z64, RaHashRules.toBigEndianN64(n64))
        assertArrayEquals(z64, RaHashRules.toBigEndianN64(z64))
    }

    @Test
    fun `pce header uses 512-bit size check like rcheevos`() {
        assertEquals(512, RaHashRules.pceHeaderSize(131_072L + 512L))
        assertEquals(0, RaHashRules.pceHeaderSize(131_072L))
        assertEquals(512, RaHashRules.pceHeaderSize(3584L)) // size & 512
    }

    @Test
    fun `nds custom hash matches rcheevos structure`() {
        // Minimal synthetic NDS image: header + arm9 + arm7 + icon at declared offsets.
        val arm9Addr = 0x200
        val arm9Size = 64
        val arm7Addr = 0x300
        val arm7Size = 32
        val iconAddr = 0x400
        val rom = ByteArray(iconAddr + RaHashRules.NDS_ICON_BYTES) { 0 }

        fun putU32(at: Int, value: Int) {
            rom[at] = (value and 0xFF).toByte()
            rom[at + 1] = ((value shr 8) and 0xFF).toByte()
            rom[at + 2] = ((value shr 16) and 0xFF).toByte()
            rom[at + 3] = ((value shr 24) and 0xFF).toByte()
        }
        putU32(0x20, arm9Addr)
        putU32(0x2C, arm9Size)
        putU32(0x30, arm7Addr)
        putU32(0x3C, arm7Size)
        putU32(0x68, iconAddr)

        for (i in 0 until arm9Size) rom[arm9Addr + i] = 0xA9.toByte()
        for (i in 0 until arm7Size) rom[arm7Addr + i] = 0xA7.toByte()
        for (i in 0 until 16) rom[iconAddr + i] = 0x1C.toByte()

        val digest = RaHashRules.hashNintendoDs(rom)
        assertNotNull(digest)
        assertEquals(16, digest!!.size)

        // Independently recompute expected MD5.
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(rom, 0, RaHashRules.NDS_HEADER_HASH_BYTES)
        md5.update(rom, arm9Addr, arm9Size)
        md5.update(rom, arm7Addr, arm7Size)
        val icon = ByteArray(RaHashRules.NDS_ICON_BYTES)
        System.arraycopy(rom, iconAddr, icon, 0, 16)
        md5.update(icon)
        assertArrayEquals(md5.digest(), digest)
    }

    @Test
    fun `hex encoding uses unsigned bytes`() {
        // Guard against signed-byte "%02x" expanding to 8 hex digits per byte.
        val bytes = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val hex = bytes.joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }
        assertEquals("007f80ff", hex)
        assertEquals(8, hex.length)
    }
}
