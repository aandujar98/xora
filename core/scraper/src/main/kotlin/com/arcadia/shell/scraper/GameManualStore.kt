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
 * On-disk home for scraped game manuals, addressed by game id.
 *
 * Deliberately not a column on the game row. The database is built with a destructive migration
 * fallback, so adding a field to store a path would wipe every user's library — favourites, play
 * counts and all — on the next upgrade. Keying files by game id instead makes the filesystem the
 * index: a manual exists for a game exactly when its file does, which is also what makes a
 * half-finished scrape safe to re-run.
 *
 * Separate from [MediaCache] because manuals are nothing like artwork. They are tens of megabytes
 * rather than tens of kilobytes, they are almost never shared between two games, and the user opts
 * into them, so they should not be swept away with the thumbnail cache.
 */
@Singleton
class GameManualStore @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: OkHttpClient,
) {
    private val root = File(context.filesDir, "manuals")

    /** Local path to this game's manual, or null when none has been downloaded. */
    fun find(gameId: String): String? {
        val key = keyFor(gameId)
        val shard = File(root, key.take(SHARD_LENGTH))
        val files = shard.listFiles() ?: return null
        return files.firstOrNull { file ->
            file.isFile && file.length() > 0 && file.nameWithoutExtension == key
        }?.absolutePath
    }

    fun has(gameId: String): Boolean = find(gameId) != null

    /**
     * Returns the stored manual for [gameId], downloading from [url] only when there is not one
     * already. Null means the download failed or produced nothing usable, which is a normal outcome
     * for a game ScreenScraper has no manual for.
     */
    suspend fun findOrDownload(
        gameId: String,
        url: String,
        format: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        find(gameId)?.let { return@withContext it }

        val target = fileFor(gameId, format, url)
        target.parentFile?.mkdirs()

        // Same temp-then-rename discipline as MediaCache: an interrupted transfer must never be
        // left behind looking like a complete manual, because its presence is the only record that
        // one was downloaded.
        val temp = File(target.absolutePath + ".part")

        runCatching {
            val request = Request.Builder().url(url).build()
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

    fun remove(gameId: String) {
        find(gameId)?.let { runCatching { File(it).delete() } }
    }

    fun clear() {
        runCatching { root.deleteRecursively() }
    }

    fun sizeBytes(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun count(): Int =
        root.walkTopDown().count { it.isFile && it.extension != "part" }

    private fun fileFor(gameId: String, format: String?, url: String): File {
        val key = keyFor(gameId)
        return File(root, "${key.take(SHARD_LENGTH)}/$key.${extensionFor(format, url)}")
    }

    /**
     * Game ids come from file paths, so they carry separators and characters no filesystem accepts;
     * hashing sidesteps that and keeps the name a fixed length.
     */
    private fun keyFor(gameId: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(gameId.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun extensionFor(format: String?, url: String): String {
        val declared = format?.lowercase()?.takeIf { it.isUsableExtension() }
        val fromUrl = url.substringAfterLast('.', "").lowercase().takeIf { it.isUsableExtension() }
        return declared ?: fromUrl ?: DEFAULT_FORMAT
    }

    private fun String.isUsableExtension(): Boolean =
        length in 2..4 && all(Char::isLetterOrDigit)

    private companion object {
        const val DEFAULT_FORMAT = "pdf"
        const val SHARD_LENGTH = 2
    }
}
