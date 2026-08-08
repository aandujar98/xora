package com.arcadia.shell.launcher.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** An album or playlist rung in the Music browser. */
data class MusicAlbum(
    val id: String,
    val title: String,
    val artist: String,
    /** Album art uri, resolvable by the shared artwork loader. */
    val artUri: String?,
    val trackCount: Int,
    val isPlaylist: Boolean = false,
)

/** One song under an album or playlist. */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val albumArtUri: String?,
    val durationMs: Long,
    /** Playable content uri — used once the media player lands. */
    val contentUri: String,
)

/**
 * On-device music read through MediaStore.
 *
 * Deliberately read-only: the Music category browses albums, playlists and songs today, and the
 * player that consumes [MusicTrack.contentUri] arrives later. Queries return empty rather than
 * throwing when audio access has not been granted, so the XMB still opens.
 */
@Singleton
class MusicLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
) {
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
                    artUri = albumArtUri(id),
                    trackCount = cursor.getInt(countCol),
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun tracks(albumId: String): List<MusicTrack> = withContext(Dispatchers.IO) {
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
            albumArtUri = albumArtUri(getLong(albumIdCol)),
            durationMs = getLong(durationCol),
            contentUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id,
            ).toString(),
        )
    }

    private fun albumArtUri(albumId: Long): String =
        ContentUris.withAppendedId(ALBUM_ART_BASE, albumId).toString()

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
    }
}
