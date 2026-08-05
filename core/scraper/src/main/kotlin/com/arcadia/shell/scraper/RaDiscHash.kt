package com.arcadia.shell.scraper

import java.security.MessageDigest
import java.util.Locale

/**
 * RetroAchievements disc hashes for PS1 / PS2 / PSP / Dreamcast, ported from rcheevos
 * `hash_disc.c` (`rc_hash_psx`, `rc_hash_ps2`, `rc_hash_psp`, `rc_hash_dreamcast`).
 *
 * Formats: `.iso`/`.bin`, `.cue`+bin, `.gdi`+tracks, `.m3u` (first entry), PSP `.pbp` (whole file).
 * CHD / CSO are not supported on-device (need libchdr / inflate).
 */
object RaDiscHash {
    val DISC_HASH_PLATFORMS: Set<String> = setOf("ps1", "ps2", "psp", "dreamcast")

    val UNSUPPORTED_DISC_EXTENSIONS: Set<String> = setOf("chd", "cso", "ecm", "gz", "nrg", "cdi", "mds")

    fun hash(
        platformId: String,
        path: String,
        extension: String,
        openFile: (String) -> RaSeekable?,
    ): ByteArray? {
        val ext = extension.lowercase(Locale.ROOT)
        if (ext in UNSUPPORTED_DISC_EXTENSIONS) return null

        return when (platformId) {
            "ps1" -> hashPsx(path, ext, openFile)
            "ps2" -> hashPs2(path, ext, openFile)
            "psp" -> hashPsp(path, ext, openFile)
            "dreamcast" -> hashDreamcast(path, ext, openFile)
            else -> null
        }
    }

    fun hashPsx(path: String, extension: String, openFile: (String) -> RaSeekable?): ByteArray? {
        val track = RaCdTrack.open(path, extension, openFile, track = 1) ?: return null
        track.use {
            var exe = findPlaystationExecutable(it, bootKey = "BOOT", cdromPrefix = "cdrom:")
            if (exe == null) {
                val psx = RaIso9660.findFileSector(it, "PSX.EXE")
                if (psx != null) exe = ExeRef("PSX.EXE", psx.first, psx.second)
            }
            val ref = exe ?: return null
            var size = ref.size
            val header = it.readSector(ref.sector, 32)
            if (header.size >= 32 && header.toString(Charsets.US_ASCII).startsWith("PS-X EXE")) {
                // Executable size at offset 28 (LE) excludes the 2048-byte header; include it.
                val exeSize = (header[28].toInt() and 0xFF) or
                    ((header[29].toInt() and 0xFF) shl 8) or
                    ((header[30].toInt() and 0xFF) shl 16) or
                    ((header[31].toInt() and 0xFF) shl 24)
                size = exeSize + 2048
            }
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(ref.name.toByteArray(Charsets.US_ASCII))
            md5.update(RaIso9660.readFile(it, ref.sector, size))
            return md5.digest()
        }
    }

    fun hashPs2(path: String, extension: String, openFile: (String) -> RaSeekable?): ByteArray? {
        val track = RaCdTrack.open(path, extension, openFile, track = 1) ?: return null
        track.use {
            val exe = findPlaystationExecutable(it, bootKey = "BOOT2", cdromPrefix = "cdrom0:")
                ?: return null
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(exe.name.toByteArray(Charsets.US_ASCII))
            md5.update(RaIso9660.readFile(it, exe.sector, exe.size))
            return md5.digest()
        }
    }

    fun hashPsp(path: String, extension: String, openFile: (String) -> RaSeekable?): ByteArray? {
        if (extension.equals("pbp", ignoreCase = true)) {
            val src = openFile(path) ?: return null
            src.use {
                val bytes = it.readFully(0, minOf(it.size(), RaIso9660.MAX_HASH_BYTES.toLong()).toInt())
                return MessageDigest.getInstance("MD5").digest(bytes)
            }
        }
        val track = RaCdTrack.open(path, extension, openFile, track = 1) ?: return null
        track.use {
            val sfo = RaIso9660.findFileSector(it, "PSP_GAME\\PARAM.SFO") ?: return null
            val eboot = RaIso9660.findFileSector(it, "PSP_GAME\\SYSDIR\\EBOOT.BIN") ?: return null
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(RaIso9660.readFile(it, sfo.first, sfo.second))
            md5.update(RaIso9660.readFile(it, eboot.first, eboot.second))
            return md5.digest()
        }
    }

    fun hashDreamcast(path: String, extension: String, openFile: (String) -> RaSeekable?): ByteArray? {
        fun openWithIp(trackNum: Int): Pair<RaCdTrack, ByteArray>? {
            val track = RaCdTrack.open(path, extension, openFile, track = trackNum) ?: return null
            val ip = track.readSector(track.firstTrackSector, 256)
            if (ip.size < 256 || !ip.startsWithAscii("SEGA SEGAKATANA ")) {
                track.close()
                return null
            }
            return track to ip.copyOf(256)
        }

        // Prefer track 3 (GD-ROM data), else first data track — matches rcheevos.
        var opened = openWithIp(3) ?: openWithIp(RaCdTrack.TRACK_FIRST_DATA) ?: return null
        var track = opened.first
        val meta = opened.second

        var bootLen = 0
        while (bootLen < 16) {
            val ch = meta[96 + bootLen].toInt().and(0xFF).toChar()
            if (ch.isWhitespace()) break
            bootLen++
        }
        if (bootLen == 0) {
            track.close()
            return null
        }
        val bootName = meta.copyOfRange(96, 96 + bootLen).toString(Charsets.US_ASCII)

        var exe = RaIso9660.findFileSector(track, bootName)
        if (exe != null && track.readSector(exe.first, 1).isEmpty()) {
            val last = RaCdTrack.open(path, extension, openFile, track = RaCdTrack.TRACK_LAST)
            if (last != null) {
                track.close()
                track = last
                exe = RaIso9660.findFileSector(track, bootName)
            }
        }

        return try {
            val ref = exe ?: return null
            val md5 = MessageDigest.getInstance("MD5")
            // rcheevos hashes the first 256 bytes of IP.BIN (docs saying 512 are outdated).
            md5.update(meta)
            md5.update(RaIso9660.readFile(track, ref.first, ref.second))
            md5.digest()
        } finally {
            track.close()
        }
    }

    private data class ExeRef(val name: String, val sector: Int, val size: Int)

    private fun findPlaystationExecutable(
        track: RaCdTrack,
        bootKey: String,
        cdromPrefix: String,
    ): ExeRef? {
        val cnf = RaIso9660.findFileSector(track, "SYSTEM.CNF") ?: return null
        val text = RaIso9660.readFile(track, cnf.first, minOf(cnf.second, 2048))
            .toString(Charsets.US_ASCII)
        var i = 0
        while (i < text.length) {
            val keyIndex = text.indexOf(bootKey, i, ignoreCase = false)
            if (keyIndex < 0) break
            var ptr = keyIndex + bootKey.length
            while (ptr < text.length && text[ptr].isWhitespace()) ptr++
            if (ptr >= text.length || text[ptr] != '=') {
                i = keyIndex + 1
                continue
            }
            ptr++
            while (ptr < text.length && text[ptr].isWhitespace()) ptr++
            if (text.regionMatches(ptr, cdromPrefix, 0, cdromPrefix.length, ignoreCase = true)) {
                ptr += cdromPrefix.length
            }
            while (ptr < text.length && text[ptr] == '\\') ptr++
            val start = ptr
            while (ptr < text.length && !text[ptr].isWhitespace() && text[ptr] != ';') ptr++
            val exeName = text.substring(start, ptr)
            if (exeName.isEmpty()) {
                i = keyIndex + 1
                continue
            }
            // ISO lookup uses backslash separators as stored after stripping cdrom: prefix.
            val isoPath = exeName.replace('/', '\\')
            val found = RaIso9660.findFileSector(track, isoPath) ?: return null
            return ExeRef(exeName.replace('/', '\\'), found.first, found.second)
        }
        return null
    }

    private fun ByteArray.startsWithAscii(prefix: String): Boolean {
        if (size < prefix.length) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i].code.toByte()) return false
        }
        return true
    }
}
