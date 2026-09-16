package com.arcadia.shell.scraper

import java.security.MessageDigest
import java.util.Locale

/**
 * RetroAchievements GameCube / Wii hashes, ported from rcheevos `hash_disc.c`
 * (`rc_hash_gamecube`, `rc_hash_wii`, `rc_hash_wii_disc`, `rc_hash_nintendo_disc_partition`).
 *
 * ISO / GCM / WBFS are hashed on-device. RVZ / WIA stay skipped — those containers store
 * decrypted Wii clusters and need a hash-tree rebuild we do not implement yet.
 */
object RaNintendoDiscHash {
    val PLATFORMS: Set<String> = setOf("gamecube", "wii")

    val UNSUPPORTED_EXTENSIONS: Set<String> = setOf("rvz", "wia", "ciso", "gcz", "nkit")

    private val WII_MAGIC = byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte())
    private val GAMECUBE_MAGIC = byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D)
    private const val MAIN_HEADER_SIZE = 0x80
    private const val REGION_CODE_ADDRESS = 0x4E000L
    private const val CLUSTER_DATA_SIZE = 0x7C00
    private const val CLUSTER_STRIDE = 0x8000
    private const val MAX_CLUSTER_COUNT = 1024
    private const val BASE_HEADER_SIZE = 0x2440
    private const val MAX_HEADER_SIZE = 1024 * 1024
    private const val MAX_CHUNK_SIZE = 1024 * 1024

    fun hash(
        platformId: String,
        path: String,
        extension: String,
        openFile: (String) -> RaSeekable?,
    ): ByteArray? {
        val ext = extension.lowercase(Locale.ROOT)
        if (ext in UNSUPPORTED_EXTENSIONS) return null
        val src = openFile(path) ?: return null
        src.use { seekable ->
            return when (platformId) {
                "gamecube" -> hashGamecube(seekable)
                "wii" -> hashWii(seekable)
                else -> null
            }
        }
    }

    fun hashGamecube(src: RaSeekable): ByteArray? {
        val magic = src.readFully(0x1C, 4)
        if (!magic.contentEquals(GAMECUBE_MAGIC)) return null
        val md5 = MessageDigest.getInstance("MD5")
        if (!hashPartition(md5, src, partOffset = 0L, wiiShift = 0)) return null
        return md5.digest()
    }

    fun hashWii(src: RaSeekable): ByteArray? {
        val base = discDataOffset(src)
        val magic = src.readFully(base + 0x18, 4)
        if (magic.contentEquals(WII_MAGIC)) {
            return hashWiiDisc(src, base)
        }
        val wiiware = src.readFully(0x04, 4)
        if (wiiware.size == 4 &&
            wiiware[0] == 'I'.code.toByte() &&
            wiiware[1] == 's'.code.toByte() &&
            wiiware[2] == 0x00.toByte() &&
            wiiware[3] == 0x00.toByte()
        ) {
            return hashWiiWare(src)
        }
        return null
    }

    /**
     * WBFS stores the disc a whole WBFS sector in. Raw ISO / GCM start at 0.
     */
    private fun discDataOffset(src: RaSeekable): Long {
        val magic = src.readFully(0, 4)
        if (magic.size == 4 &&
            magic[0] == 'W'.code.toByte() &&
            magic[1] == 'B'.code.toByte() &&
            magic[2] == 'F'.code.toByte() &&
            magic[3] == 'S'.code.toByte()
        ) {
            val sizes = src.readFully(8, 2)
            if (sizes.size >= 2) {
                val wbfsSecShift = sizes[1].toInt() and 0xFF
                if (wbfsSecShift in 8..31) {
                    val offset = 1L shl wbfsSecShift
                    if (offset < src.size()) return offset
                }
            }
        }
        return 0L
    }

    private fun hashWiiDisc(src: RaSeekable, base: Long): ByteArray? {
        val md5 = MessageDigest.getInstance("MD5")
        val encrypted = src.readFully(base + 0x61, 1).firstOrNull()?.toInt() == 0

        val mainHeader = src.readFully(base, MAIN_HEADER_SIZE)
        if (mainHeader.size < MAIN_HEADER_SIZE) return null
        md5.update(mainHeader)

        val region = src.readFully(base + REGION_CODE_ADDRESS, 4)
        if (region.size == 4) md5.update(region)

        val info = IntArray(8)
        var totalPartitions = 0
        val infoBytes = src.readFully(base + 0x40000, 32)
        if (infoBytes.size < 32) return null
        for (i in 0 until 8) {
            info[i] = be32(infoBytes, i * 4)
            if (i % 2 == 0) totalPartitions += info[i]
        }
        if (totalPartitions <= 0 || totalPartitions > 64) return null

        val table = IntArray(totalPartitions * 2)
        var kx = 0
        for (jx in 0 until 8 step 2) {
            val count = info[jx]
            if (count <= 0) continue
            val tableOff = base + (info[jx + 1].toLong() shl 2)
            val rows = src.readFully(tableOff, count * 8)
            if (rows.size < count * 8) return null
            for (ix in 0 until count) {
                table[kx++] = be32(rows, ix * 8)
                table[kx++] = be32(rows, ix * 8 + 4)
            }
        }

        val clusterBuf = ByteArray(CLUSTER_DATA_SIZE)
        for (jx in 0 until totalPartitions * 2 step 2) {
            if (table[jx + 1] == 1) continue // skip Update partition
            val partTableOff = base + (table[jx].toLong() shl 2)

            val tmdHead = src.readFully(partTableOff + 0x2A4, 8)
            if (tmdHead.size < 8) return null
            var tmdSize = be32(tmdHead, 0)
            val tmdOffset = be32(tmdHead, 4).toLong() shl 2
            if (tmdSize > CLUSTER_DATA_SIZE) tmdSize = CLUSTER_DATA_SIZE
            if (tmdSize > 0) {
                val tmd = src.readFully(partTableOff + tmdOffset, tmdSize)
                md5.update(tmd)
            }

            val partHead = src.readFully(partTableOff + 0x2B8, 8)
            if (partHead.size < 8) return null
            // rcheevos treats this as an absolute file offset (not partition-relative).
            val partOffset = base + (be32(partHead, 0).toLong() shl 2)
            val partSize = be32(partHead, 4).toLong() shl 2

            if (encrypted) {
                val clusterCount = minOf(MAX_CLUSTER_COUNT.toLong(), partSize / CLUSTER_STRIDE).toInt()
                for (ix in 0 until clusterCount) {
                    val at = partOffset + ix.toLong() * CLUSTER_STRIDE + 0x400
                    val n = src.readAt(at, clusterBuf, 0, CLUSTER_DATA_SIZE)
                    if (n <= 0) break
                    md5.update(clusterBuf, 0, n)
                }
            } else if (!hashPartition(md5, src, partOffset = partOffset, wiiShift = 2)) {
                return null
            }
        }
        return md5.digest()
    }

    private fun hashPartition(
        md5: MessageDigest,
        src: RaSeekable,
        partOffset: Long,
        wiiShift: Int,
    ): Boolean {
        val sizeBytes = src.readFully(partOffset + BASE_HEADER_SIZE + 0x14, 8)
        if (sizeBytes.size < 8) return false
        val apploaderHeader = 0x20
        val apploaderBody = be32(sizeBytes, 0)
        val apploaderTrailer = be32(sizeBytes, 4)
        var headerSize = BASE_HEADER_SIZE + apploaderHeader + apploaderBody + apploaderTrailer
        if (headerSize > MAX_HEADER_SIZE) headerSize = MAX_HEADER_SIZE
        if (headerSize <= 0) return false

        val header = src.readFully(partOffset, headerSize)
        if (header.size < 0x424) return false
        md5.update(header)

        val dolOffset = be32(header, 0x420).toLong() shl wiiShift
        val addr = src.readFully(partOffset + dolOffset, 0xD8)
        if (addr.size < 0xD8) return false

        val chunk = ByteArray(MAX_CHUNK_SIZE)
        for (ix in 0 until 18) {
            val segOffset = be32(addr, ix * 4).toLong() shl wiiShift
            val segSize = be32(addr, 0x90 + ix * 4).toLong() shl wiiShift
            if (segSize <= 0L) continue
            var remaining = segSize
            var pos = partOffset + segOffset
            while (remaining > 0) {
                val n = minOf(remaining, MAX_CHUNK_SIZE.toLong()).toInt()
                val read = src.readAt(pos, chunk, 0, n)
                if (read <= 0) break
                md5.update(chunk, 0, read)
                pos += read
                remaining -= read
            }
        }
        return true
    }

    private fun hashWiiWare(src: RaSeekable): ByteArray? {
        fun aligned(value: Int): Int = (value + 0x3F) and 0x3F.inv()
        val sizes = src.readFully(0x08, 16)
        if (sizes.size < 16) return null
        val cert = aligned(be32(sizes, 0))
        val ticket = aligned(be32(sizes, 8))
        var tmdSize = aligned(be32(sizes, 12))
        if (tmdSize > MAX_CHUNK_SIZE) tmdSize = MAX_CHUNK_SIZE
        val tmdStart = 0x40 + cert + ticket
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(src.readFully(tmdStart.toLong(), tmdSize))

        val countBytes = src.readFully(tmdStart.toLong() + 0x1DE, 2)
        if (countBytes.size < 2) return null
        val contentCount = ((countBytes[0].toInt() and 0xFF) shl 8) or (countBytes[1].toInt() and 0xFF)
        var contentAddr = tmdStart + tmdSize
        for (ix in 0 until contentCount) {
            val sizeBytes = src.readFully(tmdStart.toLong() + 0x1E4 + 8 + ix * 0x24, 8)
            if (sizeBytes.size < 8) return null
            val contentSize = if (
                sizeBytes[0] == 0.toByte() && sizeBytes[1] == 0.toByte() &&
                sizeBytes[2] == 0.toByte() && sizeBytes[3] == 0.toByte()
            ) {
                (be32(sizeBytes, 4) + 0x0F) and 0x0F.inv()
            } else {
                MAX_CHUNK_SIZE
            }
            val bufferSize = minOf(contentSize, MAX_CHUNK_SIZE)
            md5.update(src.readFully(contentAddr.toLong(), bufferSize))
            contentAddr = aligned(contentAddr + contentSize)
        }
        return md5.digest()
    }

    private fun be32(bytes: ByteArray, index: Int): Int {
        if (index + 3 >= bytes.size) return 0
        return ((bytes[index].toInt() and 0xFF) shl 24) or
            ((bytes[index + 1].toInt() and 0xFF) shl 16) or
            ((bytes[index + 2].toInt() and 0xFF) shl 8) or
            (bytes[index + 3].toInt() and 0xFF)
    }
}
