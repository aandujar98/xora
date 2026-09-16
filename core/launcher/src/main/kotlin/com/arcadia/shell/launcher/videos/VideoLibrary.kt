package com.arcadia.shell.launcher.videos

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.arcadia.shell.launcher.photos.DeviceMediaFolder
import com.arcadia.shell.launcher.photos.PhotoAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device video library over MediaStore.Video.
 *
 * Queries run when the Videos tab is focused, not on boot — same lazy pattern as Photos and Music.
 */
@Singleton
class VideoLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun access(): PhotoAccess {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (granted(Manifest.permission.READ_MEDIA_VIDEO)) return PhotoAccess.Full
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            ) {
                return PhotoAccess.Partial
            }
            return PhotoAccess.Denied
        }
        val allFiles = runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        return if (allFiles || granted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            PhotoAccess.Full
        } else {
            PhotoAccess.Denied
        }
    }

    fun requiredPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * Camera / Downloads-style video albums, newest first. Capped so the Videos column stays
     * a short XMB list.
     */
    suspend fun folders(limit: Int = FOLDER_LIMIT): List<DeviceMediaFolder> =
        withContext(Dispatchers.IO) {
            if (access() == PhotoAccess.Denied) return@withContext emptyList()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            )
            val seen = LinkedHashMap<String, FolderAcc>()
            runCatching {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_TAKEN} DESC, ${MediaStore.Video.Media.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    var scanned = 0
                    while (cursor.moveToNext() && scanned < VIDEO_SCAN_LIMIT) {
                        scanned++
                        val bucketId = cursor.getString(bucketIdCol)?.takeIf { it.isNotBlank() }
                            ?: continue
                        val existing = seen[bucketId]
                        if (existing != null) {
                            existing.count++
                        } else if (seen.size < limit) {
                            val id = cursor.getLong(idCol)
                            seen[bucketId] = FolderAcc(
                                title = cursor.getString(nameCol)?.takeIf { it.isNotBlank() }
                                    ?: "Videos",
                                coverUri = ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    id,
                                ).toString(),
                                count = 1,
                            )
                        }
                    }
                }
            }
            seen.map { (id, acc) ->
                DeviceMediaFolder(
                    id = id,
                    title = acc.title,
                    itemCount = acc.count,
                    coverUri = acc.coverUri,
                )
            }
        }

    /**
     * The videos inside one folder, newest first — the Videos column drills into this the way
     * Music drills from an album into its tracks.
     */
    suspend fun videos(folderId: String?, limit: Int = VIDEO_LIMIT): List<DeviceVideo> =
        withContext(Dispatchers.IO) {
            if (access() == PhotoAccess.Denied) return@withContext emptyList()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
            )
            val where = folderId?.takeIf { it.isNotBlank() }
                ?.let { "${MediaStore.Video.Media.BUCKET_ID} = ?" }
            val args = folderId?.takeIf { it.isNotBlank() }?.let { arrayOf(it) }
            val out = mutableListOf<DeviceVideo>()
            runCatching {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    where,
                    args,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                    val albumCol =
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    while (cursor.moveToNext() && out.size < limit) {
                        val id = cursor.getLong(idCol)
                        out += DeviceVideo(
                            id = id.toString(),
                            title = cursor.getString(nameCol)?.takeIf { it.isNotBlank() }
                                ?: "Video",
                            uri = ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id,
                            ).toString(),
                            bucketId = cursor.getString(bucketCol).orEmpty(),
                            album = cursor.getString(albumCol).orEmpty(),
                            durationMs = runCatching { cursor.getLong(durationCol) }.getOrDefault(0L),
                            sizeBytes = runCatching { cursor.getLong(sizeCol) }.getOrDefault(0L),
                        )
                    }
                }
            }
            out
        }

    private class FolderAcc(
        val title: String,
        val coverUri: String,
        var count: Int,
    )

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val FOLDER_LIMIT = 32
        const val VIDEO_SCAN_LIMIT = 4000
        const val VIDEO_LIMIT = 500
    }
}

/** One playable video file on the device. */
data class DeviceVideo(
    val id: String,
    val title: String,
    /** MediaStore content uri, handed to whichever player the device has. */
    val uri: String,
    val bucketId: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long,
)
