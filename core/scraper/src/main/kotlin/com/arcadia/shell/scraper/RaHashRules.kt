package com.arcadia.shell.scraper

/**
 * Pure RetroAchievements cartridge-hash helpers (no Android dependencies) so unit tests can
 * lock the header / endianness / NDS rules against rcheevos.
 *
 * Disc systems (PS1/PS2/PSP/Dreamcast) are implemented in [RaDiscHash] / [RaCdTrack].
 */
object RaHashRules {
    const val INES_HEADER = 16
    const val LYNX_HEADER = 64
    const val ATARI_7800_HEADER = 128
    const val SNES_COPIER_HEADER = 512
    const val PCE_HEADER = 512
    const val SNES_BLOCK = 8192L
    const val PCE_BLOCK = 131_072L
    const val NDS_HEADER_HASH_BYTES = 0x160
    const val NDS_ICON_BYTES = 0xA00

    val NES_MAGIC = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) // NES\x1a
    val FDS_MAGIC = byteArrayOf(0x46, 0x44, 0x53, 0x1A) // FDS\x1a
    val LYNX_MAGIC = byteArrayOf(0x4C, 0x59, 0x4E, 0x58, 0x00) // LYNX\0
    val ATARI_7800_MAGIC = byteArrayOf(0x01, 0x41, 0x54, 0x41, 0x52, 0x49, 0x37, 0x38, 0x30, 0x30)

    /** N64 big-endian / native (.z64) magic first byte. */
    const val N64_Z64_BYTE = 0x80.toByte()
    /** N64 byte-swapped (.v64). */
    const val N64_V64_BYTE = 0x37.toByte()
    /** N64 little-endian (.n64). */
    const val N64_N64_BYTE = 0x40.toByte()

    enum class N64Endian { Z64, V64, N64, Raw }

    /**
     * Header bytes to skip before hashing, matching rcheevos cartridge rules.
     *
     * Magic signatures win over extension so misnamed dumps (e.g. `.unf` with an iNES header)
     * still match. Size heuristics cover SNES copier and PCE headers.
     */
    fun headerSizeFor(
        extension: String,
        sizeBytes: Long,
        peek: ByteArray,
        peeked: Int,
        platformId: String? = null,
    ): Int {
        if (hasMagic(peek, peeked, NES_MAGIC) || hasMagic(peek, peeked, FDS_MAGIC)) {
            return INES_HEADER
        }
        if (hasMagic(peek, peeked, LYNX_MAGIC)) return LYNX_HEADER
        if (hasMagic(peek, peeked, ATARI_7800_MAGIC)) return ATARI_7800_HEADER

        val ext = extension.lowercase()
        when (ext) {
            "smc", "sfc", "swc", "fig", "bs" -> return snesHeaderSize(sizeBytes)
            "pce", "sgx" -> return pceHeaderSize(sizeBytes)
        }

        return when (platformId) {
            "snes" -> snesHeaderSize(sizeBytes)
            "pcengine", "tg16" -> pceHeaderSize(sizeBytes)
            else -> 0
        }
    }

    /** rcheevos: size % 8192 == 512 → strip 512-byte copier header. */
    fun snesHeaderSize(sizeBytes: Long): Int =
        if (sizeBytes > 0 && sizeBytes % SNES_BLOCK == SNES_COPIER_HEADER.toLong()) {
            SNES_COPIER_HEADER
        } else {
            0
        }

    /**
     * Docs describe 128KB alignment; rcheevos uses `size & 512`. Both agree for normal dumps;
     * the bitwise check also covers odd sizes the core accepts.
     */
    fun pceHeaderSize(sizeBytes: Long): Int =
        if (sizeBytes > 0 && (sizeBytes and PCE_HEADER.toLong()) != 0L) PCE_HEADER else 0

    fun hasMagic(peek: ByteArray, peeked: Int, magic: ByteArray): Boolean {
        if (peeked < magic.size) return false
        return magic.indices.all { peek[it] == magic[it] }
    }

    /** Detect N64 byte order from the first byte (rcheevos), not the file extension. */
    fun detectN64Endian(firstByte: Byte): N64Endian = when (firstByte) {
        N64_Z64_BYTE -> N64Endian.Z64
        N64_V64_BYTE -> N64Endian.V64
        N64_N64_BYTE -> N64Endian.N64
        0xE8.toByte(), 0x22.toByte() -> N64Endian.Raw // ndd
        else -> N64Endian.Z64 // fall through; caller may reject
    }

    fun isN64Rom(firstByte: Byte): Boolean = when (firstByte) {
        N64_Z64_BYTE, N64_V64_BYTE, N64_N64_BYTE, 0xE8.toByte(), 0x22.toByte() -> true
        else -> false
    }

    fun toBigEndianN64(raw: ByteArray): ByteArray {
        if (raw.isEmpty()) return raw
        return when (detectN64Endian(raw[0])) {
            N64Endian.V64 -> swapEveryTwo(raw)
            N64Endian.N64 -> swapEveryFour(raw)
            N64Endian.Z64, N64Endian.Raw -> raw
        }
    }

    fun swapEveryTwo(bytes: ByteArray): ByteArray {
        val out = bytes.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val a = out[i]
            out[i] = out[i + 1]
            out[i + 1] = a
            i += 2
        }
        return out
    }

    fun swapEveryFour(bytes: ByteArray): ByteArray {
        val out = bytes.copyOf()
        var i = 0
        while (i + 3 < out.size) {
            val a = out[i]
            val b = out[i + 1]
            val c = out[i + 2]
            val d = out[i + 3]
            out[i] = d
            out[i + 1] = c
            out[i + 2] = b
            out[i + 3] = a
            i += 4
        }
        return out
    }

    /**
     * Nintendo DS / DSi custom hash: 0x160 header + ARM9 + ARM7 + 0xA00 icon/title.
     * See rcheevos `rc_hash_nintendo_ds`.
     *
     * Reads only the slices RA needs so a 128–512MB dump does not have to sit in the heap.
     */
    fun hashNintendoDs(rom: ByteArray): ByteArray? =
        hashNintendoDs(rom.size.toLong()) { position, length ->
            val start = position.toInt().coerceAtLeast(0)
            if (start >= rom.size || length <= 0) {
                ByteArray(0)
            } else {
                rom.copyOfRange(start, (start + length).coerceAtMost(rom.size))
            }
        }

    fun hashNintendoDs(
        fileSize: Long,
        read: (position: Long, length: Int) -> ByteArray,
    ): ByteArray? {
        if (fileSize < 512L) return null

        fun slice(position: Long, length: Int): ByteArray {
            if (length <= 0 || position >= fileSize) return ByteArray(0)
            val want = minOf(length.toLong(), fileSize - position).toInt()
            return read(position, want)
        }

        var offset = 0L
        val prefix = slice(0, 0xB4)
        // SuperCard 512-byte prefix
        if (fileSize > 512L &&
            prefix.size > 0xB3 &&
            prefix[0] == 0x2E.toByte() && prefix[1] == 0x00.toByte() &&
            prefix[2] == 0x00.toByte() && prefix[3] == 0xEA.toByte() &&
            prefix[0xB0] == 0x44.toByte() && prefix[0xB1] == 0x46.toByte() &&
            prefix[0xB2] == 0x96.toByte() && prefix[0xB3] == 0x00.toByte()
        ) {
            offset = 512L
        }
        if (fileSize < offset + 512L) return null

        fun u32(at: Int): Long {
            val bytes = slice(offset + at, 4)
            if (bytes.size < 4) return -1L
            return (bytes[0].toLong() and 0xFF) or
                ((bytes[1].toLong() and 0xFF) shl 8) or
                ((bytes[2].toLong() and 0xFF) shl 16) or
                ((bytes[3].toLong() and 0xFF) shl 24)
        }

        val arm9Addr = u32(0x20)
        val arm9Size = u32(0x2C)
        val arm7Addr = u32(0x30)
        val arm7Size = u32(0x3C)
        val iconAddr = u32(0x68)
        if (arm9Addr < 0L || arm9Size < 0L || arm7Addr < 0L || arm7Size < 0L || iconAddr < 0L) {
            return null
        }
        if (arm9Size + arm7Size > 16L * 1024 * 1024) return null

        val arm9Start = offset + arm9Addr
        val arm7Start = offset + arm7Addr
        val iconStart = offset + iconAddr
        if (arm9Start + arm9Size > fileSize) return null
        if (arm7Start + arm7Size > fileSize) return null

        val md5 = java.security.MessageDigest.getInstance("MD5")
        val header = slice(offset, NDS_HEADER_HASH_BYTES)
        if (header.size < NDS_HEADER_HASH_BYTES) return null
        md5.update(header)
        if (arm9Size > 0) md5.update(slice(arm9Start, arm9Size.toInt()))
        if (arm7Size > 0) md5.update(slice(arm7Start, arm7Size.toInt()))

        val icon = ByteArray(NDS_ICON_BYTES)
        val available = (fileSize - iconStart).coerceAtLeast(0L).coerceAtMost(NDS_ICON_BYTES.toLong()).toInt()
        if (available > 0) {
            val iconBytes = slice(iconStart, available)
            System.arraycopy(iconBytes, 0, icon, 0, iconBytes.size.coerceAtMost(NDS_ICON_BYTES))
        }
        md5.update(icon)
        return md5.digest()
    }

    /** Platforms whose RA hash is the MAME/FBNeo basename, not file bytes. */
    val FILENAME_HASH_PLATFORMS: Set<String> = setOf("arcade", "neogeo")

    /**
     * Disc systems with implemented RA custom hashes (ISO9660 / IP.BIN style).
     * See [RaDiscHash].
     */
    val DISC_HASH_PLATFORMS: Set<String> = RaDiscHash.DISC_HASH_PLATFORMS

    /**
     * Disc / encrypted formats that need sector or filesystem hashing we do not implement yet.
     * Hashing the whole file produces confident wrong MD5s and noisy "no set" messages.
     */
    val UNSUPPORTED_CUSTOM_HASH_PLATFORMS: Set<String> = setOf(
        "saturn", "segacd",
        "wiiu", "3ds", "switch",
        "pcenginecd", "neogeocd", "jaguarcd",
    )

    val N64_EXTENSIONS: Set<String> = setOf("z64", "v64", "n64", "ndd")
    val NDS_EXTENSIONS: Set<String> = setOf("nds", "dsi", "ids")
    val NDS_PLATFORMS: Set<String> = setOf("nds")
}
