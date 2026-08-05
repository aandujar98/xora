package com.arcadia.shell.scraper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory

class RaDiscHashTest {

    private fun tempRoot(prefix: String): File =
        createTempDirectory(prefix).toFile()

    @Test
    fun `psx hashes BOOT exe name plus executable bytes`() {
        val root = tempRoot("ra-psx")
        try {
            val iso = File(root, "game.iso")
            val exeName = "SLUS_123.45"
            val exeBytes = psxExe(payloadSize = 2048)
            val cnf = "BOOT = cdrom:\\$exeName;1\r\nTCB = 4\r\n"
            MiniIso.write(
                iso,
                files = mapOf(
                    "SYSTEM.CNF" to cnf.toByteArray(Charsets.US_ASCII),
                    exeName to exeBytes,
                ),
            )

            val digest = RaDiscHash.hashPsx(iso.absolutePath, "iso", ::openFile)
            assertNotNull(digest)

            val expected = MessageDigest.getInstance("MD5")
            expected.update(exeName.toByteArray(Charsets.US_ASCII))
            expected.update(exeBytes)
            assertArrayEquals(expected.digest(), digest)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `ps2 hashes BOOT2 exe name plus elf bytes`() {
        val root = tempRoot("ra-ps2")
        try {
            val iso = File(root, "game.iso")
            val exeName = "SLUS_999.99"
            val elf = byteArrayOf(0x7f, 0x45, 0x4c, 0x46) + ByteArray(100) { 7 }
            val cnf = "BOOT2 = cdrom0:\\$exeName;1\r\n"
            MiniIso.write(
                iso,
                files = mapOf(
                    "SYSTEM.CNF" to cnf.toByteArray(Charsets.US_ASCII),
                    exeName to elf,
                ),
            )

            val digest = RaDiscHash.hashPs2(iso.absolutePath, "iso", ::openFile)
            assertNotNull(digest)

            val expected = MessageDigest.getInstance("MD5")
            expected.update(exeName.toByteArray(Charsets.US_ASCII))
            expected.update(elf)
            assertArrayEquals(expected.digest(), digest)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `psp hashes PARAM SFO then EBOOT`() {
        val root = tempRoot("ra-psp")
        try {
            val iso = File(root, "game.iso")
            val sfo = ByteArray(64) { 0x11 }
            val eboot = ByteArray(128) { 0x22 }
            MiniIso.write(
                iso,
                files = mapOf(
                    "PSP_GAME\\PARAM.SFO" to sfo,
                    "PSP_GAME\\SYSDIR\\EBOOT.BIN" to eboot,
                ),
            )

            val digest = RaDiscHash.hashPsp(iso.absolutePath, "iso", ::openFile)
            assertNotNull(digest)

            val expected = MessageDigest.getInstance("MD5")
            expected.update(sfo)
            expected.update(eboot)
            assertArrayEquals(expected.digest(), digest)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `dreamcast hashes first 256 IP bytes and boot file`() {
        val root = tempRoot("ra-dc")
        try {
            val iso = File(root, "game.iso")
            val bootName = "1ST_READ.BIN"
            val boot = ByteArray(256) { 0x5A }
            val ip = ByteArray(2048) { 0x20 }
            "SEGA SEGAKATANA ".toByteArray(Charsets.US_ASCII).copyInto(ip)
            bootName.toByteArray(Charsets.US_ASCII).copyInto(ip, destinationOffset = 96)

            MiniIso.write(
                iso,
                files = mapOf(bootName to boot),
                sector0 = ip,
            )

            val digest = RaDiscHash.hashDreamcast(iso.absolutePath, "iso", ::openFile)
            assertNotNull(digest)

            val expected = MessageDigest.getInstance("MD5")
            expected.update(ip, 0, 256)
            expected.update(boot)
            assertArrayEquals(expected.digest(), digest)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `chd is unsupported`() {
        assertNull(RaDiscHash.hash("ps1", "game.chd", "chd") { null })
        assertTrue("chd" in RaDiscHash.UNSUPPORTED_DISC_EXTENSIONS)
    }

    private fun openFile(path: String): RaSeekable? =
        File(path).takeIf { it.isFile }?.let { FileRaSeekable(it) }

    private fun psxExe(payloadSize: Int): ByteArray {
        val total = 2048 + payloadSize
        val bytes = ByteArray(total) { 0xAB.toByte() }
        "PS-X EXE".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes[28] = (payloadSize and 0xFF).toByte()
        bytes[29] = ((payloadSize shr 8) and 0xFF).toByte()
        bytes[30] = ((payloadSize shr 16) and 0xFF).toByte()
        bytes[31] = ((payloadSize shr 24) and 0xFF).toByte()
        return bytes
    }
}

/**
 * Minimal cooked (2048) ISO9660 image builder for unit tests.
 * Supports nested paths with `\` separators.
 */
private object MiniIso {
    fun write(file: File, files: Map<String, ByteArray>, sector0: ByteArray? = null) {
        val sectors = mutableListOf<ByteArray>()
        if (sector0 != null) {
            sectors += sector0.copyOf(2048)
        }
        while (sectors.size < 16) sectors += ByteArray(2048)

        data class Node(
            val dirs: LinkedHashMap<String, Node> = LinkedHashMap(),
            val files: LinkedHashMap<String, ByteArray> = LinkedHashMap(),
            var sector: Int = -1,
        )

        val root = Node()
        for ((path, data) in files) {
            val parts = path.split('\\').filter { it.isNotEmpty() }
            var node = root
            for (i in 0 until parts.lastIndex) {
                node = node.dirs.getOrPut(parts[i].uppercase()) { Node() }
            }
            node.files[parts.last().uppercase()] = data
        }

        val pvdSector = 16
        sectors += ByteArray(2048) // PVD
        root.sector = 17
        sectors += ByteArray(2048) // root dir

        fun allocNode(node: Node) {
            for ((_, child) in node.dirs) {
                child.sector = sectors.size
                sectors += ByteArray(2048)
                allocNode(child)
            }
        }
        allocNode(root)

        data class FileLoc(val sector: Int, val size: Int)

        val fileLocs = HashMap<Pair<Node, String>, FileLoc>()
        fun allocFiles(node: Node) {
            for ((name, data) in node.files) {
                val start = sectors.size
                var offset = 0
                if (data.isEmpty()) {
                    fileLocs[node to name] = FileLoc(start, 0)
                    continue
                }
                while (offset < data.size) {
                    val chunk = ByteArray(2048)
                    val n = minOf(2048, data.size - offset)
                    System.arraycopy(data, offset, chunk, 0, n)
                    sectors += chunk
                    offset += n
                }
                fileLocs[node to name] = FileLoc(start, data.size)
            }
            node.dirs.values.forEach(::allocFiles)
        }
        allocFiles(root)

        fun putRecord(buf: ByteArray, offset: Int, name: String, sector: Int, size: Int): Int {
            val nameBytes = name.toByteArray(Charsets.US_ASCII)
            var length = 33 + nameBytes.size
            if (length % 2 != 0) length++
            buf[offset] = length.toByte()
            buf[offset + 2] = (sector and 0xFF).toByte()
            buf[offset + 3] = ((sector shr 8) and 0xFF).toByte()
            buf[offset + 4] = ((sector shr 16) and 0xFF).toByte()
            buf[offset + 10] = (size and 0xFF).toByte()
            buf[offset + 11] = ((size shr 8) and 0xFF).toByte()
            buf[offset + 12] = ((size shr 16) and 0xFF).toByte()
            buf[offset + 13] = ((size shr 24) and 0xFF).toByte()
            buf[offset + 25] = 0x02 // directory flag when name is \u0000/\u0001 or dirs — set below
            buf[offset + 32] = nameBytes.size.toByte()
            nameBytes.copyInto(buf, destinationOffset = offset + 33)
            return offset + length
        }

        fun writeDir(node: Node, parentSector: Int) {
            val buf = sectors[node.sector]
            var off = 0
            off = putRecord(buf, off, "\u0000", node.sector, 2048)
            buf[2 + 25] = 0x02 // rough; flags at record+25 — keep simple, find ignores flags
            off = putRecord(buf, off, "\u0001", parentSector, 2048)
            for ((name, child) in node.dirs) {
                off = putRecord(buf, off, name, child.sector, 2048)
                writeDir(child, node.sector)
            }
            for ((name, _) in node.files) {
                val loc = fileLocs[node to name]!!
                off = putRecord(buf, off, "$name;1", loc.sector, loc.size)
            }
        }
        writeDir(root, root.sector)

        val pvd = sectors[pvdSector]
        pvd[0] = 1
        "CD001".toByteArray(Charsets.US_ASCII).copyInto(pvd, 1)
        pvd[128] = 0
        pvd[129] = 8 // 2048 LE
        pvd[156] = 34
        pvd[158] = (root.sector and 0xFF).toByte()
        pvd[159] = ((root.sector shr 8) and 0xFF).toByte()
        pvd[160] = ((root.sector shr 16) and 0xFF).toByte()
        pvd[166] = (2048 and 0xFF).toByte()
        pvd[167] = ((2048 shr 8) and 0xFF).toByte()

        val out = ByteArray(sectors.size * 2048)
        for (i in sectors.indices) {
            System.arraycopy(sectors[i], 0, out, i * 2048, 2048)
        }
        file.writeBytes(out)
    }
}
