package com.arcadia.shell.launcher.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.arcadia.shell.datastore.ShellPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Where a rung's music comes from. */
enum class MusicSource {
    /** MediaStore / folder audio on this device. */
    Device,
    /** A linked Spotify account, played through Spotify's own player. */
    Spotify,
}

/** An album or playlist rung in the Music browser. */
data class MusicAlbum(
    val id: String,
    val title: String,
    val artist: String,
    /** Album art uri, resolvable by the shared artwork loader. */
    val artUri: String?,
    val trackCount: Int,
    val isPlaylist: Boolean = false,
    val source: MusicSource = MusicSource.Device,
    /** Playback context for remote sources, e.g. `spotify:playlist:…`. */
    val remoteUri: String? = null,
)

/** One song under an album or playlist. */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val albumArtUri: String?,
    val durationMs: Long,
    /** Playable content uri — MediaStore or file:// for a chosen folder. */
    val contentUri: String,
    val source: MusicSource = MusicSource.Device,
    /** Playback context this track belongs to, so Spotify can keep queue order. */
    val contextUri: String? = null,
)

/**
 * On-device music library.
 *
 * Default: all music indexed by MediaStore. When Settings picks a music folder path, that folder
 * (and its children) become the only source — scanned from the filesystem so the path always
 * matches what the user chose.
 */
@Singleton
class MusicLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: ShellPreferences,
) {
    private val embeddedArtRoot = File(context.filesDir, "music_art/embedded")
    private val folderCoverCache = mutableMapOf<String, String?>()
    fun hasAudioAccess(): Boolean = ContextCompat.checkSelfPermission(
        context,
        audioPermission(),
    ) == PackageManager.PERMISSION_GRANTED

    fun audioPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    suspend fun albums(): List<MusicAlbum> = withContext(Dispatchers.IO) {
        val folder = musicFolderPath()
        if (folder != null) return@withContext folderAlbums(folder)
        if (!hasAudioAccess()) return@withContext emptyList()
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Albums.ALBUM} ASC",
            ).useRows { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
                val countCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
                val id = cursor.getLong(idCol)
                MusicAlbum(
                    id = id.toString(),
                    title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                    artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() } ?: "Unknown artist",
                    artUri = albumArtUriIfPresent(id),
                    trackCount = cursor.getInt(countCol),
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun tracks(albumId: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        val folder = musicFolderPath()
        if (folder != null) {
            return@withContext folderTracks(folder).filter { albumKey(it) == albumId }
        }
        if (!hasAudioAccess()) return@withContext emptyList()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ALBUM_ID} = ?",
                arrayOf(albumId),
                "${MediaStore.Audio.Media.TRACK} ASC, ${MediaStore.Audio.Media.TITLE} ASC",
            ).useRows { cursor -> cursor.readTrack() }
        }.getOrDefault(emptyList())
    }

    /** Flat song list used when no album is drilled into. */
    suspend fun allTracks(limit: Int = ALL_TRACKS_LIMIT): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            val folder = musicFolderPath()
            if (folder != null) return@withContext folderTracks(folder).take(limit)
            if (!hasAudioAccess()) return@withContext emptyList()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
            )
            runCatching {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC",
                ).useRows { cursor -> cursor.readTrack() }.take(limit)
            }.getOrDefault(emptyList())
        }

    private suspend fun musicFolderPath(): String? {
        val path = preferences.settings.first().musicLibraryPath?.trim().orEmpty()
        if (path.isBlank()) return null
        val dir = File(path)
        return if (dir.isDirectory) dir.absolutePath else null
    }

    private fun folderAlbums(rootPath: String): List<MusicAlbum> {
        val tracks = folderTracks(rootPath)
        return tracks
            .groupBy { albumKey(it) }
            .map { (key, songs) ->
                val first = songs.first()
                MusicAlbum(
                    id = key,
                    title = first.albumTitle.ifBlank { "Unknown album" },
                    artist = songs.map { it.artist }.distinct()
                        .singleOrNull()
                        ?: "Various artists",
                    artUri = songs.firstNotNullOfOrNull { it.albumArtUri },
                    trackCount = songs.size,
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    private fun folderTracks(rootPath: String): List<MusicTrack> {
        val root = File(rootPath)
        if (!root.isDirectory) return emptyList()
        folderCoverCache.clear()
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            .mapNotNull { file -> readFileTrack(file) }
            .sortedBy { it.title.lowercase() }
            .toList()
    }

    private fun readFileTrack(file: File): MusicTrack? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
                ?: file.parentFile?.name
                ?: "Unknown album"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val embedded = retriever.embeddedPicture?.let { bytes -> cacheEmbeddedArt(file, bytes) }
            val folderCover = file.parentFile?.let { coverForDirectory(it) }
            MusicTrack(
                id = "file:${file.absolutePath}",
                title = title,
                artist = artist,
                albumTitle = album,
                albumArtUri = embedded ?: folderCover,
                durationMs = duration,
                contentUri = Uri.fromFile(file).toString(),
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun coverForDirectory(dir: File): String? {
        val key = dir.absolutePath
        if (key in folderCoverCache) return folderCoverCache[key]
        val found = MusicFolderArt.findCover(dir)
        folderCoverCache[key] = found
        return found
    }

    private fun cacheEmbeddedArt(file: File, bytes: ByteArray): String? {
        if (bytes.size < 64) return null
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("${file.absolutePath}:${file.lastModified()}:${bytes.size}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val extension = MusicFolderArt.extensionForImage(bytes)
        val target = File(embeddedArtRoot, "${digest.take(2)}/$digest.$extension")
        if (target.isFile && target.length() > 0L) return target.absolutePath
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            target.absolutePath
        }.getOrNull()
    }

    private fun albumKey(track: MusicTrack): String =
        "folder:${track.albumTitle.lowercase().trim()}"

    private fun Cursor.readTrack(): MusicTrack {
        val idCol = getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdCol = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durationCol = getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val id = getLong(idCol)
        return MusicTrack(
            id = id.toString(),
            title = getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown track",
            artist = getString(artistCol)?.takeIf { it.isNotBlank() } ?: "Unknown artist",
            albumTitle = getString(albumCol).orEmpty(),
            albumArtUri = albumArtUriIfPresent(getLong(albumIdCol)),
            durationMs = getLong(durationCol),
            contentUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id,
            ).toString(),
        )
    }

    /**
     * MediaStore always mints an album-art URI, even when no image exists. Opening the stream
     * is the only reliable check — a missing cover would otherwise paint a broken tile and skip
     * the network scrape that could fill it.
     */
    private fun albumArtUriIfPresent(albumId: Long): String? {
        val uri = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)
        val readable = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(4)
                stream.read(header) >= 2
            } == true
        }.getOrDefault(false)
        return uri.toString().takeIf { readable }
    }

    private inline fun <T> Cursor?.useRows(read: (Cursor) -> T): List<T> {
        this ?: return emptyList()
        return use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(read(cursor))
                }
            }
        }
    }

    companion object {
        private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")
        /** Songs rung is a browse list, not a full library dump. */
        private const val ALL_TRACKS_LIMIT = 500
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "alac",
        )
    }
}
