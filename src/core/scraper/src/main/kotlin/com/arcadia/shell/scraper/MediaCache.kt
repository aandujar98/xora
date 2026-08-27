package com.arcadia.shell.scraper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Content-addressed store for downloaded artwork.
 *
 * Naming files after a hash of their source url makes the cache idempotent: re-scraping the same
 * game, or two regional releases that share artwork, resolves to a file that is already on disk and
 * costs nothing. It also means a partially completed scrape can simply be run again.
 */
@Singleton
class MediaCache @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: OkHttpClient,
) {
    private val root = File(context.filesDir, "media")

    /**
     * Returns the local path for [url], downloading it only if it is not already cached.
     *
     * [extensionOverride] exists for media served from a query-string endpoint, where the url
     * carries no usable suffix but the caller knows the real format (ScreenScraper manuals).
     */
    suspend fun fetch(
        url: String,
        headers: Map<String, String> = emptyMap(),
        extensionOverride: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val target = fileFor(url, extensionOverride)
        if (target.isFile && target.length() > 0) return@withContext target.absolutePath

        target.parentFile?.mkdirs()

        // Downloads land in a sibling temp file and are renamed only on success, so an interrupted
        // transfer can never be mistaken for a valid cache entry.
        val temp = File(target.absolutePath + ".part")

        runCatching {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null

                temp.outputStream().use { output -> response.body.byteStream().copyTo(output) }
            }

            if (temp.length() == 0L) {
                temp.delete()
                return@runCatching null
            }

            if (temp.renameTo(target)) target.absolutePath else null
        }.onFailure { temp.delete() }.getOrNull()
    }

    /**
     * Like [fetch], but rejects ScreenScraper's `NOMEDIA` / non-image bodies that still return HTTP
     * 200 from `mediaSysteme.php` and similar binary endpoints.
     */
    suspend fun fetchImage(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String? = withContext(Dispatchers.IO) {
        val target = fileFor(url, extensionOverride = "png")
        if (target.isFile && target.length() > 64L && looksLikeImage(target)) {
            return@withContext target.absolutePath
        }

        target.parentFile?.mkdirs()
        val temp = File(target.absolutePath + ".part")

        runCatching {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (key, value) -> header(key, value) }
                header("User-Agent", "arcadia")
            }.build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                temp.outputStream().use { output -> response.body.byteStream().copyTo(output) }
                if (temp.length() < 64L) {
                    temp.delete()
                    return@runCatching null
                }
                // NOMEDIA / error text payloads are tiny and never image/* .
                if (contentType.isNotBlank() &&
                    !contentType.startsWith("image/") &&
                    !contentType.contains("octet-stream")
                ) {
                    temp.delete()
                    return@runCatching null
                }
                if (!looksLikeImage(temp)) {
                    temp.delete()
                    return@runCatching null
                }
            }

            if (target.exists()) target.delete()
            if (temp.renameTo(target)) target.absolutePath else null
        }.onFailure { temp.delete() }.getOrNull()
    }

    private fun looksLikeImage(file: File): Boolean {
        val header = ByteArray(12)
        val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
        if (read < 4) return false
        // PNG
        if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
        ) {
            return true
        }
        // JPEG
        if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) return true
        // GIF
        if (header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte()) {
            return true
        }
        // WebP (RIFF....WEBP)
        if (header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
            read >= 12 && header[8] == 0x57.toByte() && header[9] == 0x45.toByte()
        ) {
            return true
        }
        return false
    }

    fun clear() {
        runCatching { root.deleteRecursively() }
    }

    fun sizeBytes(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun fileFor(url: String, extensionOverride: String? = null): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val extension = extensionOverride
            ?.lowercase()
            ?.takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) }
            ?: url.substringAfterLast('.', "")
                .takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) }
            ?: "img"

        // Sharding on the first two characters keeps any single directory from growing to tens of
        // thousands of entries, which some filesystems handle badly.
        return File(root, "${digest.take(2)}/$digest.$extension")
    }
}
