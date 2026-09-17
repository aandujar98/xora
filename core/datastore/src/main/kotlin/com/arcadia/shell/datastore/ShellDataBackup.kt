package com.arcadia.shell.datastore

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup and Restore Data: everything this device has learned about a library that the library
 * itself does not carry — scraped artwork, titles and metadata, playtime, shortcuts, and the
 * layout and settings around them — written to a single zip in Downloads so it can be moved to
 * another device.
 *
 * ROMs are not in it. Neither are cores, BIOS files or save data: those are either large and
 * re-downloadable or the emulator's business rather than the shell's, and a backup nobody can
 * send themselves is not a backup. [BACKED_UP_DIRS] names exactly what travels.
 *
 * Restore replaces what it finds and leaves the rest alone, then the app has to be restarted —
 * the preference store and the database are both open and already hold the old contents in
 * memory. The caller is responsible for saying so.
 */
@Singleton
class ShellDataBackup @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Writes a backup to Downloads and returns the name it was given, or a failure carrying
     * something worth showing a player.
     */
    suspend fun backup(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = "XOrA-backup-${STAMP.format(Date())}.zip"
            val sink = openDownloadsSink(name)
                ?: error("Downloads is not writable on this device.")
            ZipOutputStream(sink.buffered()).use { zip ->
                zip.setLevel(6)
                writeEntries(zip)
            }
            name
        }
    }

    /** Reads a backup written by [backup] back over this device's data. */
    suspend fun restore(source: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(source)
                ?: error("That file could not be opened.")
            var restored = 0
            ZipInputStream(stream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val target = resolveEntry(entry.name)
                    if (target == null) {
                        // An entry naming a path outside the app's own storage is either a
                        // different program's zip or an attempt at one. Skip it silently.
                        zip.closeEntry()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> zip.copyTo(out) }
                    restored++
                    zip.closeEntry()
                }
            }
            if (restored == 0) error("That zip holds no XOrA backup.")
            restored
        }
    }

    private fun writeEntries(zip: ZipOutputStream) {
        // The preference store and the database are the metadata: titles, playtime, shortcuts,
        // every setting and the whole XMB layout.
        preferenceFiles().forEach { file ->
            putFile(zip, "$PREFS_PREFIX/${file.name}", file)
        }
        databaseFiles().forEach { file ->
            putFile(zip, "$DB_PREFIX/${file.name}", file)
        }
        BACKED_UP_DIRS.forEach { dirName ->
            val dir = File(context.filesDir, dirName)
            if (!dir.isDirectory) return@forEach
            dir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relative = file.relativeTo(context.filesDir).invariantPath()
                    putFile(zip, "$FILES_PREFIX/$relative", file)
                }
        }
    }

    private fun putFile(zip: ZipOutputStream, entryName: String, file: File) {
        if (!file.isFile) return
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    /**
     * Maps an entry back to the file it came from, or null when it names anywhere else. Entries
     * are checked against the resolved canonical path rather than the string, so `../` cannot
     * walk out of the app's storage.
     */
    private fun resolveEntry(entryName: String): File? {
        val normalized = entryName.replace('\\', '/')
        val root = when {
            normalized.startsWith("$PREFS_PREFIX/") -> File(context.filesDir, "datastore")
            normalized.startsWith("$DB_PREFIX/") -> databaseDir()
            normalized.startsWith("$FILES_PREFIX/") -> context.filesDir
            else -> return null
        }
        val relative = normalized.substringAfter('/')
        if (relative.isBlank()) return null
        val target = File(root, relative)
        val within = target.canonicalPath.startsWith(root.canonicalFile.path + File.separator)
        return target.takeIf { within }
    }

    private fun preferenceFiles(): List<File> =
        File(context.filesDir, "datastore").listFiles()?.filter { it.isFile }.orEmpty()

    private fun databaseDir(): File = context.getDatabasePath(DB_NAME).parentFile
        ?: File(context.filesDir, "databases")

    /** The database and its write-ahead log, which without each other restore to yesterday. */
    private fun databaseFiles(): List<File> {
        val main = context.getDatabasePath(DB_NAME)
        return listOf(main, File("${main.path}-wal"), File("${main.path}-shm"))
            .filter { it.isFile }
    }

    /**
     * MediaStore, which lets an app write to Downloads without asking for a permission. minSdk is
     * 29, so there is no pre-scoped-storage path to fall back to.
     */
    private fun openDownloadsSink(name: String): OutputStream? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val stream = resolver.openOutputStream(uri) ?: return null
        return PendingDownload(stream) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
        }
    }

    private companion object {
        const val DB_NAME = "arcadia.db"
        const val PREFS_PREFIX = "prefs"
        const val DB_PREFIX = "db"
        const val FILES_PREFIX = "files"

        val STAMP = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)

        /**
         * Directories under `filesDir` that travel with a backup: scraped and hand-picked
         * artwork, manuals, avatars and the theme media the layout points at.
         *
         * Deliberately absent: `cores`, `system`, `saves` and `overlays` (emulator data, large
         * and re-downloadable), and `platform_art_bundled` (it ships in the APK).
         */
        val BACKED_UP_DIRS = listOf(
            "media",
            "manuals",
            "avatars",
            "game_custom_media",
            "platform_art",
            "platform_art_custom",
            "music_art",
            "home_wallpaper",
            "home_bgm",
            "home_shortcut_art",
            "home_folder",
            "home_boot",
            "shop_themes",
        )
    }
}

/** Marks the MediaStore row visible once the zip is completely written, not before. */
private class PendingDownload(
    private val delegate: OutputStream,
    private val onClose: () -> Unit,
) : OutputStream() {
    override fun write(b: Int) = delegate.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
    override fun flush() = delegate.flush()
    override fun close() {
        delegate.close()
        onClose()
    }
}

/** Zip entry names use `/` on every platform, whatever the filesystem separator is here. */
private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')
