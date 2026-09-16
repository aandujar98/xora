package com.arcadia.shell.scraper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RaNintendoDiscHashTest {

    @Test
    fun `gamecube rejects discs without the magic word`() {
        val root = createTempDirectory("ra-gc").toFile()
        try {
            val iso = File(root, "not-gc.iso")
            iso.writeBytes(ByteArray(0x100) { 0 })
            assertNull(RaNintendoDiscHash.hashGamecube(FileRaSeekable(iso)))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `wii rejects files without disc or wiiware magic`() {
        val root = createTempDirectory("ra-wii").toFile()
        try {
            val iso = File(root, "not-wii.iso")
            iso.writeBytes(ByteArray(0x40) { 0 })
            assertNull(RaNintendoDiscHash.hashWii(FileRaSeekable(iso)))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `nds streaming reader matches the in-memory digest`() {
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

        val buffered = RaHashRules.hashNintendoDs(rom)
        val streamed = RaHashRules.hashNintendoDs(rom.size.toLong()) { position, length ->
            val start = position.toInt()
            rom.copyOfRange(start, (start + length).coerceAtMost(rom.size))
        }
        assertNotNull(buffered)
        assertArrayEquals(buffered, streamed)
    }
}
