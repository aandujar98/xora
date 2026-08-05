package com.arcadia.shell.scraper

import java.io.Closeable
import java.util.Locale
import kotlin.math.min

/**
 * Sector-oriented CD/GD-ROM track reader matching rcheevos `cdreader.c` behaviour for
 * `.iso` / `.bin`, `.cue`+bin, and `.gdi`+tracks.
 */
class RaCdTrack private constructor(
    private val source: RaSeekable,
    private val sectorSize: Int,
    private val sectorHeaderSize: Int,
    private val rawDataSize: Int,
    private val trackFirstSector: Int,
    private val trackPregapSectors: Int,
    private val fileTrackOffset: Long,
) : Closeable {
    /** Absolute sector index of the first usable sector (includes pregap), per rcheevos. */
    val firstTrackSector: Int get() = trackFirstSector + trackPregapSectors

    fun readSector(sector: Int, requestedBytes: Int = 2048): ByteArray {
        if (sector < trackFirstSector) return ByteArray(0)
        var remaining = requestedBytes
        var sectorStart =
            (sector - trackFirstSector).toLong() * sectorSize + sectorHeaderSize + fileTrackOffset
        val out = ByteArray(requestedBytes)
        var written = 0
        while (remaining > rawDataSize) {
            val n = source.readAt(sectorStart, out, written, rawDataSize)
            written += n
            if (n < rawDataSize) return out.copyOf(written)
            sectorStart += sectorSize
            remaining -= rawDataSize
        }
        val n = source.readAt(sectorStart, out, written, remaining)
        written += maxOf(n, 0)
        return if (written == requestedBytes) out else out.copyOf(written)
    }

    override fun close() = source.close()

    companion object {
        const val TRACK_FIRST_DATA = -1
        const val TRACK_LAST = -2
        const val TRACK_LARGEST = -3

        private val SYNC = byteArrayOf(
            0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00,
        )

        fun open(
            path: String,
            extension: String,
            openFile: (String) -> RaSeekable?,
            track: Int = 1,
        ): RaCdTrack? {
            val ext = extension.lowercase(Locale.ROOT)
            return when (ext) {
                "cue" -> openCue(path, openFile, track)
                "gdi" -> openGdi(path, openFile, track)
                "m3u" -> {
                    val first = readPlaylistFirstEntry(path, openFile) ?: return null
                    val childExt = first.substringAfterLast('.', "")
                    open(first, childExt, openFile, track)
                }
                else -> openBinTrack(path, openFile, track)
            }
        }

        private fun readPlaylistFirstEntry(path: String, openFile: (String) -> RaSeekable?): String? {
            val seekable = openFile(path) ?: return null
            seekable.use { src ->
                val text = src.readFully(0, min(src.size(), 64_000L).toInt())
                    .toString(Charsets.UTF_8)
                val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
                    .ifEmpty { path.substringBeforeLast('\\', missingDelimiterValue = "") }
                for (raw in text.lineSequence()) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val entry = line.trim('"')
                    return if (entry.contains('/') || entry.contains('\\') || parent.isEmpty()) {
                        entry
                    } else {
                        "$parent/$entry"
                    }
                }
            }
            return null
        }

        private fun openBinTrack(path: String, openFile: (String) -> RaSeekable?, track: Int): RaCdTrack? {
            if (track > 1) return null
            val source = openFile(path) ?: return null
            val determined = determineSectorSize(source, pregapSectors = 0, fileTrackOffset = 0)
            if (determined != null) {
                return RaCdTrack(
                    source = source,
                    sectorSize = determined.sectorSize,
                    sectorHeaderSize = determined.headerSize,
                    rawDataSize = determined.rawDataSize,
                    trackFirstSector = determined.trackFirstSector,
                    trackPregapSectors = 0,
                    fileTrackOffset = 0,
                )
            }
            val size = source.size()
            val fallback = when {
                size % 2352L == 0L -> Triple(2352, 24, 2048)
                size % 2048L == 0L -> Triple(2048, 0, 2048)
                size % 2336L == 0L -> Triple(2336, 8, 2048)
                else -> {
                    source.close()
                    return null
                }
            }
            return RaCdTrack(
                source = source,
                sectorSize = fallback.first,
                sectorHeaderSize = fallback.second,
                rawDataSize = fallback.third,
                trackFirstSector = 0,
                trackPregapSectors = 0,
                fileTrackOffset = 0,
            )
        }

        private data class SectorLayout(
            val sectorSize: Int,
            val headerSize: Int,
            val rawDataSize: Int,
            val trackFirstSector: Int,
        )

        private fun determineSectorSize(
            source: RaSeekable,
            pregapSectors: Int,
            fileTrackOffset: Long,
        ): SectorLayout? {
            val tocSector = 16L + pregapSectors
            fun trySize(sectorSize: Int): SectorLayout? {
                val header = source.readFully(tocSector * sectorSize + fileTrackOffset, 32)
                if (header.size < 32) return null
                if (sectorSize == 2048) {
                    if (header.size >= 6 && header[1] == 'C'.code.toByte() &&
                        header[2] == 'D'.code.toByte() && header[3] == '0'.code.toByte() &&
                        header[4] == '0'.code.toByte() && header[5] == '1'.code.toByte()
                    ) {
                        return SectorLayout(2048, 0, 2048, 0)
                    }
                    return null
                }
                if (!header.startsWithSync()) return null
                val headerSize = if (header.size >= 30 &&
                    header[25] == 'C'.code.toByte() && header[26] == 'D'.code.toByte() &&
                    header[27] == '0'.code.toByte() && header[28] == '0'.code.toByte() &&
                    header[29] == '1'.code.toByte()
                ) {
                    24
                } else {
                    16
                }
                val trackFirst = msfToSector(header) - tocSector.toInt()
                return SectorLayout(sectorSize, headerSize, 2048, trackFirst)
            }
            return trySize(2352) ?: trySize(2336) ?: trySize(2048)
        }

        private fun ByteArray.startsWithSync(): Boolean {
            if (size < SYNC.size) return false
            for (i in SYNC.indices) if (this[i] != SYNC[i]) return false
            return true
        }

        private fun msfToSector(header: ByteArray): Int {
            fun bcd(b: Byte): Int {
                val v = b.toInt() and 0xFF
                return (v shr 4) * 10 + (v and 0x0F)
            }
            val minutes = bcd(header[12])
            val seconds = bcd(header[13])
            val frames = bcd(header[14])
            return ((minutes * 60) + seconds) * 75 + frames - 150
        }

        private fun modeLayout(mode: String): Triple<Int, Int, Int>? {
            val m = mode.uppercase(Locale.ROOT).trim()
            return when {
                m.startsWith("MODE2/2352") -> Triple(2352, 24, 2048)
                m.startsWith("MODE1/2048") -> Triple(2048, 0, 2048)
                m.startsWith("MODE2/2336") -> Triple(2336, 8, 2048)
                m.startsWith("MODE1/2352") -> Triple(2352, 16, 2048)
                m.startsWith("AUDIO") -> Triple(2352, 0, 2352)
                else -> null
            }
        }

        private fun siblingPath(cuePath: String, binName: String): String {
            val slash = maxOf(cuePath.lastIndexOf('/'), cuePath.lastIndexOf('\\'))
            return if (slash >= 0) cuePath.substring(0, slash + 1) + binName else binName
        }

        private data class CueTrack(
            val id: Int,
            val mode: String,
            val filename: String,
            var firstSector: Int = -1,
            var pregapSectors: Int = -1,
            var sectorCount: Int = 0,
            var fileTrackOffset: Long = 0,
            var fileFirstSector: Int = 0,
            val isData: Boolean,
            val sectorSize: Int,
        )

        private fun openCue(path: String, openFile: (String) -> RaSeekable?, trackRequest: Int): RaCdTrack? {
            val cue = openFile(path) ?: return null
            val text = cue.use { it.readFully(0, min(it.size(), 256_000L).toInt()).toString(Charsets.UTF_8) }

            var want = if (trackRequest == 0) TRACK_LARGEST else trackRequest
            var session = 1
            var current: CueTrack? = null
            var previous: CueTrack? = null
            var largest: CueTrack? = null
            var done = false
            val tracks = mutableListOf<CueTrack>()

            fun commitPreviousFromFile() {
                val prev = previous ?: return
                if (prev.sectorCount == 0 && prev.filename.isNotEmpty()) {
                    val binSize = openFile(siblingPath(path, prev.filename))?.use { it.size() } ?: 0L
                    if (prev.sectorSize > 0) {
                        val fileSectors = (binSize / prev.sectorSize).toInt()
                        prev.sectorCount = fileSectors - prev.firstSector.coerceAtLeast(0)
                    }
                }
                if (want == TRACK_LARGEST && prev.isData &&
                    (largest == null || prev.sectorCount > (largest?.sectorCount ?: 0))
                ) {
                    largest = prev.copy()
                }
            }

            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                val upper = line.uppercase(Locale.ROOT)
                when {
                    upper.startsWith("FILE ") -> {
                        current?.let {
                            previous = it
                            commitPreviousFromFile()
                            tracks += it
                        }
                        val name = parseCueFileName(line) ?: continue
                        val prev = previous
                        current = CueTrack(
                            id = 0,
                            mode = "",
                            filename = name,
                            fileTrackOffset = 0,
                            fileFirstSector = (prev?.fileFirstSector ?: 0) +
                                (prev?.firstSector ?: 0).coerceAtLeast(0) +
                                (prev?.sectorCount ?: 0),
                            isData = false,
                            sectorSize = 0,
                        )
                    }
                    upper.startsWith("TRACK ") -> {
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size < 3) continue
                        val id = parts[1].toIntOrNull() ?: continue
                        val mode = parts[2]
                        val sectorSize = modeLayout(mode)?.first
                            ?: mode.substringAfter('/').toIntOrNull()
                            ?: 2352
                        val cur = current ?: continue
                        if (cur.sectorSize != 0) {
                            previous = cur
                            tracks += cur
                        }
                        current = CueTrack(
                            id = id,
                            mode = mode,
                            filename = cur.filename,
                            fileTrackOffset = cur.fileTrackOffset,
                            fileFirstSector = cur.fileFirstSector,
                            isData = mode.uppercase(Locale.ROOT).startsWith("MODE"),
                            sectorSize = sectorSize,
                        )
                    }
                    upper.startsWith("INDEX ") -> {
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size < 3) continue
                        val index = parts[1].toIntOrNull() ?: continue
                        val msf = parts[2].split(':')
                        if (msf.size != 3) continue
                        val sectorOffset =
                            ((msf[0].toInt() * 60) + msf[1].toInt()) * 75 + msf[2].toInt()
                        val cur = current ?: continue
                        if (cur.firstSector == -1) {
                            cur.firstSector = sectorOffset
                            val prev = previous
                            if (prev != null && prev.filename == cur.filename) {
                                prev.sectorCount = cur.firstSector - prev.firstSector
                                cur.fileTrackOffset =
                                    prev.fileTrackOffset + prev.sectorCount.toLong() * prev.sectorSize
                            }
                            if (want == TRACK_LARGEST && prev != null && prev.isData &&
                                prev.sectorCount > (largest?.sectorCount ?: 0)
                            ) {
                                largest = prev.copy()
                            }
                        }
                        if (index == 1) {
                            cur.pregapSectors = sectorOffset - cur.firstSector
                            when {
                                cur.id == want -> done = true
                                want == TRACK_FIRST_DATA && cur.isData -> {
                                    want = cur.id
                                    done = true
                                }
                                want == TRACK_FIRST_OF_SECOND_SESSION && session == 2 -> {
                                    want = cur.id
                                    done = true
                                }
                            }
                        }
                    }
                    upper.startsWith("REM SESSION ") -> {
                        session = line.substringAfter("SESSION", "").trim().toIntOrNull() ?: session
                    }
                }
                if (done) break
            }
            current?.let { tracks += it; previous = it }

            if (want == TRACK_LARGEST) {
                current?.let { cur ->
                    if (cur.isData && cur.sectorSize > 0) {
                        val binSize = openFile(siblingPath(path, cur.filename))?.use { it.size() } ?: 0L
                        cur.sectorCount =
                            (binSize / cur.sectorSize).toInt() - cur.firstSector.coerceAtLeast(0)
                        if ((largest?.sectorCount ?: 0) > cur.sectorCount) {
                            // keep largest
                        } else {
                            largest = cur
                        }
                    }
                }
                want = largest?.id ?: current?.id ?: return null
            } else if (want == TRACK_LAST) {
                want = current?.id ?: return null
            }

            val selected = tracks.lastOrNull { it.id == want } ?: current?.takeIf { it.id == want }
                ?: return null
            if (selected.filename.isEmpty() || selected.firstSector < 0) return null

            val binPath = siblingPath(path, selected.filename)
            val bin = openFile(binPath) ?: return null
            val layoutFromCue = modeLayout(selected.mode)
            val determined = determineSectorSize(
                bin,
                pregapSectors = selected.pregapSectors.coerceAtLeast(0),
                fileTrackOffset = selected.fileTrackOffset,
            )
            val sectorSize: Int
            val headerSize: Int
            val rawDataSize: Int
            if (determined != null) {
                sectorSize = determined.sectorSize
                headerSize = determined.headerSize
                rawDataSize = determined.rawDataSize
            } else if (layoutFromCue != null) {
                sectorSize = layoutFromCue.first
                headerSize = layoutFromCue.second
                rawDataSize = layoutFromCue.third
            } else {
                bin.close()
                return null
            }

            // rcheevos: track_first_sector = file_first_sector + first_sector (INDEX),
            // and determine_sector_size may overwrite track_first_sector from MSF when sync found.
            val trackFirst = if (determined != null && determined.sectorSize != 2048) {
                determined.trackFirstSector
            } else {
                selected.fileFirstSector + selected.firstSector
            }

            return RaCdTrack(
                source = bin,
                sectorSize = sectorSize,
                sectorHeaderSize = headerSize,
                rawDataSize = rawDataSize,
                trackFirstSector = trackFirst,
                trackPregapSectors = selected.pregapSectors.coerceAtLeast(0),
                fileTrackOffset = selected.fileTrackOffset,
            )
        }

        private const val TRACK_FIRST_OF_SECOND_SESSION = -4

        private fun parseCueFileName(line: String): String? {
            val q1 = line.indexOf('"')
            if (q1 >= 0) {
                val q2 = line.indexOf('"', q1 + 1)
                if (q2 > q1) return line.substring(q1 + 1, q2)
            }
            val parts = line.split(Regex("\\s+"))
            return parts.getOrNull(1)
        }

        private data class GdiTrack(
            val id: Int,
            val lba: Int,
            val type: Int,
            val sectorSize: Int,
            val file: String,
        )

        private fun openGdi(path: String, openFile: (String) -> RaSeekable?, trackRequest: Int): RaCdTrack? {
            val gdi = openFile(path) ?: return null
            val text = gdi.use { it.readFully(0, min(it.size(), 64_000L).toInt()).toString(Charsets.UTF_8) }
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.isEmpty()) return null
            val trackCount = lines.first().toIntOrNull() ?: return null
            var want = when (trackRequest) {
                TRACK_LAST -> trackCount
                0 -> TRACK_LARGEST
                else -> trackRequest
            }

            val parsed = mutableListOf<GdiTrack>()
            for (line in lines.drop(1)) {
                val tokens = tokenizeGdi(line)
                if (tokens.size < 5) continue
                val id = tokens[0].toIntOrNull() ?: continue
                val lba = tokens[1].toIntOrNull() ?: continue
                val type = tokens[2].toIntOrNull() ?: continue
                val sectorSize = tokens[3].toIntOrNull() ?: continue
                val file = tokens[4]
                parsed += GdiTrack(id, lba, type, sectorSize, file)
            }
            if (parsed.isEmpty()) return null

            var selected: GdiTrack? = null
            when (want) {
                TRACK_FIRST_DATA -> selected = parsed.firstOrNull { it.type == 4 }
                TRACK_LARGEST -> {
                    var best: GdiTrack? = null
                    var bestSize = -1L
                    for (t in parsed.filter { it.type == 4 }) {
                        val size = openFile(siblingPath(path, t.file))?.use { it.size() } ?: 0L
                        if (size > bestSize) {
                            bestSize = size
                            best = t
                        }
                    }
                    selected = best
                }
                else -> selected = parsed.firstOrNull { it.id == want }
            }
            val track = selected ?: return null
            val bin = openFile(siblingPath(path, track.file)) ?: return null
            val mode = "MODE1/${track.sectorSize}"
            val layout = modeLayout(mode) ?: Triple(track.sectorSize, 0, 2048)
            val determined = determineSectorSize(bin, pregapSectors = 0, fileTrackOffset = 0)
            return RaCdTrack(
                source = bin,
                sectorSize = determined?.sectorSize ?: layout.first,
                sectorHeaderSize = determined?.headerSize ?: layout.second,
                rawDataSize = determined?.rawDataSize ?: layout.third,
                trackFirstSector = track.lba,
                trackPregapSectors = 0,
                fileTrackOffset = 0,
            )
        }

        private fun tokenizeGdi(line: String): List<String> {
            val out = mutableListOf<String>()
            var i = 0
            while (i < line.length) {
                while (i < line.length && line[i].isWhitespace()) i++
                if (i >= line.length) break
                if (line[i] == '"') {
                    val end = line.indexOf('"', i + 1)
                    if (end < 0) break
                    out += line.substring(i + 1, end)
                    i = end + 1
                } else {
                    val start = i
                    while (i < line.length && !line[i].isWhitespace()) i++
                    out += line.substring(start, i)
                }
            }
            return out
        }
    }
}

/**
 * ISO-9660 helpers used by PlayStation / PSP / Dreamcast disc hashing (rcheevos `rc_cd_find_file_sector`).
 */
object RaIso9660 {
    fun findFileSector(track: RaCdTrack, path: String): Pair<Int, Int>? {
        var remaining = path
        if (remaining.startsWith('\\')) remaining = remaining.substring(1)
        val slash = remaining.indexOf('\\')
        if (slash >= 0) {
            val dir = remaining.substring(0, slash)
            val rest = remaining.substring(slash + 1)
            val root = dirSectorRoot(track) ?: return null
            val dirSector = findInDirectory(track, root, dir) ?: return null
            return findInDirectoryWalk(track, dirSector.first, dirSector.second, rest)
        }
        val root = dirSectorRoot(track) ?: return null
        return findInDirectory(track, root, remaining)
    }

    private data class DirScan(val startSector: Int, val numSectors: Int)

    private fun dirSectorRoot(track: RaCdTrack): DirScan? {
        val pvd = track.readSector(track.firstTrackSector + 16, 256)
        if (pvd.size < 170) return null
        val sector = (pvd[156 + 2].toInt() and 0xFF) or
            ((pvd[156 + 3].toInt() and 0xFF) shl 8) or
            ((pvd[156 + 4].toInt() and 0xFF) shl 16)
        val logicalBlockSize = (pvd[128].toInt() and 0xFF) or ((pvd[129].toInt() and 0xFF) shl 8)
        val sectionLen = (pvd[156 + 10].toInt() and 0xFF) or
            ((pvd[156 + 11].toInt() and 0xFF) shl 8) or
            ((pvd[156 + 12].toInt() and 0xFF) shl 16) or
            ((pvd[156 + 13].toInt() and 0xFF) shl 24)
        val numSectors = if (logicalBlockSize == 0) 1 else (sectionLen / logicalBlockSize).coerceAtLeast(1)
        return DirScan(sector, numSectors)
    }

    private fun findInDirectoryWalk(track: RaCdTrack, sector: Int, size: Int, path: String): Pair<Int, Int>? {
        var remaining = path
        if (remaining.startsWith('\\')) remaining = remaining.substring(1)
        val numSectors = ((size + 2047) / 2048).coerceAtLeast(1)
        val slash = remaining.indexOf('\\')
        return if (slash >= 0) {
            val dir = remaining.substring(0, slash)
            val rest = remaining.substring(slash + 1)
            val found = findInDirectory(track, DirScan(sector, numSectors), dir) ?: return null
            findInDirectoryWalk(track, found.first, found.second, rest)
        } else {
            findInDirectory(track, DirScan(sector, numSectors), remaining)
        }
    }

    private fun findInDirectory(track: RaCdTrack, scan: DirScan, filename: String): Pair<Int, Int>? {
        val want = filename.uppercase(Locale.ROOT)
        var sector = scan.startSector
        var remainingSectors = scan.numSectors
        while (true) {
            val data = track.readSector(sector, 2048)
            if (data.isEmpty()) return null
            var offset = 0
            while (offset < data.size) {
                val length = data[offset].toInt() and 0xFF
                if (length == 0) break
                if (offset + length > data.size) break
                val nameLen = data[offset + 32].toInt() and 0xFF
                if (nameLen > 0 && offset + 33 + nameLen <= data.size) {
                    val rawName = data.copyOfRange(offset + 33, offset + 33 + nameLen)
                        .toString(Charsets.US_ASCII)
                    val base = rawName.substringBefore(';')
                    if (base.equals(want, ignoreCase = true) ||
                        (want.length <= rawName.length &&
                            rawName.regionMatches(0, want, 0, want.length, ignoreCase = true) &&
                            (rawName.length == want.length || rawName.getOrNull(want.length) == ';'))
                    ) {
                        val fileSector = (data[offset + 2].toInt() and 0xFF) or
                            ((data[offset + 3].toInt() and 0xFF) shl 8) or
                            ((data[offset + 4].toInt() and 0xFF) shl 16)
                        val size = (data[offset + 10].toInt() and 0xFF) or
                            ((data[offset + 11].toInt() and 0xFF) shl 8) or
                            ((data[offset + 12].toInt() and 0xFF) shl 16) or
                            ((data[offset + 13].toInt() and 0xFF) shl 24)
                        return fileSector to size
                    }
                }
                offset += length
            }
            if (remainingSectors > 1) {
                remainingSectors--
                sector++
            } else {
                return null
            }
        }
    }

    fun readFile(track: RaCdTrack, sector: Int, size: Int, maxBytes: Int = MAX_HASH_BYTES): ByteArray {
        val limit = min(size, maxBytes)
        val out = ByteArray(limit)
        var remaining = limit
        var current = sector
        var written = 0
        while (remaining > 0) {
            val chunk = track.readSector(current, 2048)
            if (chunk.isEmpty()) break
            val take = min(remaining, chunk.size)
            System.arraycopy(chunk, 0, out, written, take)
            written += take
            remaining -= take
            current++
        }
        return if (written == limit) out else out.copyOf(written)
    }

    const val MAX_HASH_BYTES = 64 * 1024 * 1024
}
