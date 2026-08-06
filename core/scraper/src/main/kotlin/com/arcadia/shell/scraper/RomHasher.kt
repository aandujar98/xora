package com.arcadia.shell.scraper

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.RomArchives
import com.arcadia.shell.model.TitleCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class RomHashes(
    val crc32: String,
    val md5: String,
    val sha1: String,
    val hashedBytes: Long,
)

/**
 * Computes the three hashes ROM databases are keyed on.
 *
 * RetroAchievements identification rules (headers, arcade filenames, N64 endianness, NDS custom
 * hash, disc ISO9660 hashes, archives) are applied so MD5 lookups against `dorequest.php?r=gameid`
 * succeed for common ROMs.
 */
@Singleton
class RomHasher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fileAccess by lazy { RomFileAccess(context) }

    suspend fun hash(game: Game): RomHashes? = withContext(Dispatchers.IO) {
        if (game.sizeBytes > MAX_HASHABLE_BYTES) {
            Log.w(TAG, "Skip hash ${game.fileName}: size ${game.sizeBytes} exceeds cap")
            return@withContext null
        }

        val extension = TitleCleaner.extensionOf(game.fileName)
        val platformId = game.platformId

        if (platformId in RaHashRules.FILENAME_HASH_PLATFORMS) {
            val base = game.fileName.substringBeforeLast('.')
            val hashes = hashBytes(base.toByteArray(Charsets.UTF_8))
            Log.i(TAG, "RA filename-hash $platformId '$base' md5=${hashes.md5}")
            return@withContext hashes
        }

        if (platformId in RaHashRules.UNSUPPORTED_CUSTOM_HASH_PLATFORMS) {
            Log.i(TAG, "RA custom disc/encrypted hash not implemented for $platformId (${game.fileName})")
            return@withContext null
        }

        if (extension == "7z") {
            Log.i(TAG, "7z not supported for hashing: ${game.fileName}")
            return@withContext null
        }

        if (platformId in RaHashRules.DISC_HASH_PLATFORMS) {
            return@withContext hashDisc(game, extension, platformId)
        }

        runCatching {
            openStream(game)?.use { raw ->
                BufferedInputStream(raw, BUFFER_SIZE).use { stream ->
                    val hashes = when {
                        extension == "zip" || extension in RomArchives.extensions ->
                            hashZip(stream, platformId)
                        else -> {
                            // SAF / stale DB rows sometimes report size 0; SNES/PCE header
                            // stripping needs the real length or RA MD5s miss every linked dump.
                            val sizeBytes = resolveSizeBytes(game).takeIf { it > 0 }
                                ?: game.sizeBytes
                            hashContent(stream, extension, sizeBytes, platformId)
                        }
                    }
                    if (hashes != null) {
                        Log.i(
                            TAG,
                            "Hashed ${game.fileName} platform=$platformId " +
                                "bytes=${hashes.hashedBytes} md5=${hashes.md5}",
                        )
                    } else {
                        Log.w(TAG, "No hash produced for ${game.fileName} ($platformId)")
                    }
                    hashes
                }
            }
        }.onFailure {
            Log.e(TAG, "Hash failed for ${game.fileName}: ${it.message}", it)
        }.getOrNull()
    }

    /**
     * Prefer a live file/PFD length over the DB [Game.sizeBytes]. Stale scan sizes break SNES/PCE
     * header stripping and produce MD5s that miss RetroAchievements while the emulator (which
     * always uses [File.length]) still matches.
     */
    private fun resolveSizeBytes(game: Game): Long {
        val path = game.filePath
        if (path != null) {
            val length = File(path).length()
            if (length > 0L) return length
        }
        val uri = game.documentUri
        if (uri != null) {
            val fromPfd = runCatching {
                context.contentResolver.openFileDescriptor(uri.toUri(), "r")?.use { it.statSize }
            }.getOrNull()?.takeIf { it > 0L }
            if (fromPfd != null) return fromPfd
        }
        return game.sizeBytes.takeIf { it > 0L } ?: 0L
    }

    private fun hashDisc(game: Game, extension: String, platformId: String): RomHashes? {
        if (extension in RaDiscHash.UNSUPPORTED_DISC_EXTENSIONS) {
            Log.i(TAG, "Disc container .$extension not hashable on-device for $platformId")
            return null
        }
        val openPath = game.filePath ?: game.documentUri ?: run {
            Log.w(TAG, "No path/URI for disc hash: ${game.fileName}")
            return null
        }
        val openFile = fileAccess.openResolver(game.filePath, game.documentUri)
        return runCatching {
            val md5 = RaDiscHash.hash(platformId, openPath, extension, openFile)
            if (md5 == null) {
                Log.w(TAG, "Disc hash failed for ${game.fileName} ($platformId)")
                return@runCatching null
            }
            // CRC/SHA-1 stay best-effort for ScreenScraper on smaller images only; disc MD5 is
            // the RA identifier and must not wait on a multi-GB full-file scan.
            val whole = if (game.sizeBytes in 1..DISC_WHOLE_FILE_HASH_CAP) {
                runCatching {
                    openStream(game)?.use {
                        hashStream(BufferedInputStream(it, BUFFER_SIZE), skipBytes = 0)
                    }
                }.getOrNull()
            } else {
                null
            }
            val hashes = RomHashes(
                crc32 = whole?.crc32 ?: "00000000",
                md5 = md5.toHex(),
                sha1 = whole?.sha1 ?: "0".repeat(40),
                hashedBytes = whole?.hashedBytes ?: game.sizeBytes,
            )
            Log.i(
                TAG,
                "Disc-hashed ${game.fileName} platform=$platformId md5=${hashes.md5}",
            )
            hashes
        }.onFailure {
            Log.e(TAG, "Disc hash failed for ${game.fileName}: ${it.message}", it)
        }.getOrNull()
    }

    private fun openStream(game: Game): InputStream? {
        val path = game.filePath
        if (path != null) {
            val file = File(path)
            if (file.isFile) return file.inputStream()
        }

        val uri = game.documentUri ?: return null
        val parsed = uri.toUri()
        // Prefer FileDescriptor — some SAF providers fail openInputStream but allow PFD.
        val pfd = runCatching {
            context.contentResolver.openFileDescriptor(parsed, "r")
        }.getOrNull()
        if (pfd != null) {
            return object : java.io.FileInputStream(pfd.fileDescriptor) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        pfd.close()
                    }
                }
            }
        }
        return runCatching { context.contentResolver.openInputStream(parsed) }.getOrNull()
    }

    private fun hashZip(stream: InputStream, platformId: String): RomHashes? {
        var bestPreferred: Pair<RomHashes, Long>? = null
        var bestAny: Pair<RomHashes, Long>? = null
        val preferredExts = preferredRomExtensions(platformId)

        ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue

                val bytes = zip.readBytes()
                if (bytes.isEmpty()) continue
                val extension = TitleCleaner.extensionOf(entry.name)
                if (extension == "7z" || extension == "zip") continue

                val hashes = hashContent(
                    input = ByteArrayInputStream(bytes),
                    extension = extension,
                    sizeBytes = bytes.size.toLong(),
                    platformId = platformId,
                ) ?: continue

                val size = hashes.hashedBytes
                val any = bestAny
                if (any == null || size > any.second) bestAny = hashes to size

                if (extension in preferredExts) {
                    val pref = bestPreferred
                    if (pref == null || size > pref.second) bestPreferred = hashes to size
                }
            }
        }

        return (bestPreferred ?: bestAny)?.first
    }

    private fun hashContent(
        input: InputStream,
        extension: String,
        sizeBytes: Long,
        platformId: String,
    ): RomHashes? {
        val needsFullBuffer =
            platformId in RaHashRules.NDS_PLATFORMS ||
                extension in RaHashRules.NDS_EXTENSIONS ||
                extension in RaHashRules.N64_EXTENSIONS ||
                platformId == "n64"

        if (needsFullBuffer) {
            val raw = input.readBytes()
            if (raw.isEmpty()) return null

            if (platformId in RaHashRules.NDS_PLATFORMS || extension in RaHashRules.NDS_EXTENSIONS) {
                return hashNds(raw)
            }
            if (platformId == "n64" || extension in RaHashRules.N64_EXTENSIONS) {
                return hashN64(raw)
            }
        }

        return hashWithHeaderRules(input, extension, sizeBytes, platformId)
    }

    private fun hashWithHeaderRules(
        input: InputStream,
        extension: String,
        sizeBytes: Long,
        platformId: String,
    ): RomHashes {
        val buffered = if (input is BufferedInputStream) {
            input
        } else {
            BufferedInputStream(input, BUFFER_SIZE)
        }
        buffered.mark(HEADER_PEEK)
        val peek = ByteArray(HEADER_PEEK)
        val peeked = buffered.read(peek).coerceAtLeast(0)
        buffered.reset()

        // Mislabelled N64 dumps (e.g. .bin) still need endian conversion when magic matches.
        if (peeked > 0 && RaHashRules.isN64Rom(peek[0]) &&
            (platformId == "n64" || extension in RaHashRules.N64_EXTENSIONS)
        ) {
            return hashN64(buffered.readBytes())
        }

        val skip = RaHashRules.headerSizeFor(extension, sizeBytes, peek, peeked, platformId)
        return hashStream(buffered, skipBytes = skip)
    }

    private fun hashN64(raw: ByteArray): RomHashes {
        if (raw.isEmpty() || !RaHashRules.isN64Rom(raw[0])) {
            Log.w(TAG, "N64 ROM magic not recognised (first=0x${raw.firstOrNull()?.toUByte()})")
            return hashBytes(raw)
        }
        val converted = RaHashRules.toBigEndianN64(raw)
        return hashBytes(converted)
    }

    private fun hashNds(raw: ByteArray): RomHashes? {
        val digest = RaHashRules.hashNintendoDs(raw) ?: run {
            Log.w(TAG, "NDS custom hash failed (size=${raw.size})")
            return null
        }
        // RA MD5 is the custom digest; CRC/SHA-1 stay whole-file for ScreenScraper.
        val whole = hashBytes(raw)
        return RomHashes(
            crc32 = whole.crc32,
            md5 = digest.toHex(),
            sha1 = whole.sha1,
            hashedBytes = raw.size.toLong(),
        )
    }

    private fun hashBytes(bytes: ByteArray): RomHashes {
        val crc = CRC32()
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        crc.update(bytes)
        md5.update(bytes)
        sha1.update(bytes)
        return RomHashes(
            crc32 = "%08X".format(crc.value),
            md5 = md5.digest().toHex(),
            sha1 = sha1.digest().toHex(),
            hashedBytes = bytes.size.toLong(),
        )
    }

    private fun hashStream(
        input: InputStream,
        skipBytes: Int,
        closeInput: Boolean = true,
    ): RomHashes {
        val crc = CRC32()
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")

        var remainingToSkip = skipBytes.toLong()
        var hashed = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        try {
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break

                var offset = 0
                var length = read

                if (remainingToSkip > 0) {
                    val skipNow = minOf(remainingToSkip, length.toLong()).toInt()
                    offset += skipNow
                    length -= skipNow
                    remainingToSkip -= skipNow
                    if (length == 0) continue
                }

                crc.update(buffer, offset, length)
                md5.update(buffer, offset, length)
                sha1.update(buffer, offset, length)
                hashed += length
            }
        } finally {
            if (closeInput) input.close()
        }

        return RomHashes(
            crc32 = "%08X".format(crc.value),
            md5 = md5.digest().toHex(),
            sha1 = sha1.digest().toHex(),
            hashedBytes = hashed,
        )
    }

    private fun preferredRomExtensions(platformId: String): Set<String> {
        val platform = runCatching {
            com.arcadia.shell.model.PlatformCatalog.requireById(platformId)
        }.getOrNull() ?: return emptySet()
        return platform.extensions.filterNot { it in RomArchives.extensions }.toSet()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }

    private companion object {
        const val TAG = "RomHasher"
        const val BUFFER_SIZE = 1 shl 16
        const val HEADER_PEEK = 256
        const val MAX_HASHABLE_BYTES = 4L * 1024 * 1024 * 1024
        /** Cap for optional whole-file CRC/SHA alongside disc MD5. */
        const val DISC_WHOLE_FILE_HASH_CAP = 256L * 1024 * 1024
    }
}
