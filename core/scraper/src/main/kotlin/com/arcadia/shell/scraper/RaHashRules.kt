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
     */
    fun hashNintendoDs(rom: ByteArray): ByteArray? {
        if (rom.size < 512) return null

        var offset = 0
        // SuperCard 512-byte prefix
        if (rom.size > 512 &&
            rom[0] == 0x2E.toByte() && rom[1] == 0x00.toByte() &&
            rom[2] == 0x00.toByte() && rom[3] == 0xEA.toByte() &&
            rom[0xB0] == 0x44.toByte() && rom[0xB1] == 0x46.toByte() &&
            rom[0xB2] == 0x96.toByte() && rom[0xB3] == 0x00.toByte()
        ) {
            offset = 512
        }
        if (rom.size < offset + 512) return null

        fun u32(at: Int): Int {
            val i = offset + at
            return (rom[i].toInt() and 0xFF) or
                ((rom[i + 1].toInt() and 0xFF) shl 8) or
                ((rom[i + 2].toInt() and 0xFF) shl 16) or
                ((rom[i + 3].toInt() and 0xFF) shl 24)
        }

        val arm9Addr = u32(0x20)
        val arm9Size = u32(0x2C)
        val arm7Addr = u32(0x30)
        val arm7Size = u32(0x3C)
        val iconAddr = u32(0x68)

        if (arm9Size.toLong() + arm7Size.toLong() > 16L * 1024 * 1024) return null
        if (arm9Size < 0 || arm7Size < 0) return null

        val arm9Start = offset + arm9Addr
        val arm7Start = offset + arm7Addr
        val iconStart = offset + iconAddr
        if (arm9Start < 0 || arm7Start < 0 || iconStart < 0) return null
        if (arm9Start + arm9Size > rom.size) return null
        if (arm7Start + arm7Size > rom.size) return null

        val md5 = java.security.MessageDigest.getInstance("MD5")
        md5.update(rom, offset, NDS_HEADER_HASH_BYTES)
        if (arm9Size > 0) md5.update(rom, arm9Start, arm9Size)
        if (arm7Size > 0) md5.update(rom, arm7Start, arm7Size)

        val icon = ByteArray(NDS_ICON_BYTES)
        val available = (rom.size - iconStart).coerceAtLeast(0).coerceAtMost(NDS_ICON_BYTES)
        if (available > 0) {
            System.arraycopy(rom, iconStart, icon, 0, available)
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
        "gamecube", "wii", "wiiu", "3ds", "switch",
        "pcenginecd", "neogeocd", "jaguarcd",
    )

    val N64_EXTENSIONS: Set<String> = setOf("z64", "v64", "n64", "ndd")
    val NDS_EXTENSIONS: Set<String> = setOf("nds", "dsi", "ids")
    val NDS_PLATFORMS: Set<String> = setOf("nds")
}
