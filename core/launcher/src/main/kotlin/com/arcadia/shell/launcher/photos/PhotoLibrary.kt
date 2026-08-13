package com.arcadia.shell.launcher.photos

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One picture from the user's device library. Everything on this model is real MediaStore data —
 * the Photo Viewer never fabricates captions, dates, or sources.
 */
data class DevicePhoto(
    /** Stable MediaStore row id, used as lazy-layout key and favorites key. */
    val id: String,
    /** `content://media/...` — resolvable by the shared artwork loader. */
    val contentUri: String,
    val displayName: String,
    /** Capture time in epoch millis ([MediaStore.Images.ImageColumns.DATE_TAKEN], falling back to DATE_ADDED). */
    val dateTakenMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    /** Bucket (album / folder) display name, e.g. "Screenshots" or "Camera". */
    val album: String,
    /** User caption when the row carries one; most photos have none. */
    val caption: String? = null,
)

/** How much of the photo library the shell may read. */
enum class PhotoAccess {
    Denied,
    /** Android 14+ limited-photo selection: only user-picked photos are visible. */
    Partial,
    Full,
}

/**
 * Device photo library over MediaStore.Images.
 *
 * Queries run per open (not up front): Media → Photos is not the first thing opened on boot and
 * the shell should not pay for an image scan it may never show.
 */
@Singleton
class PhotoLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun access(): PhotoAccess {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (granted(Manifest.permission.READ_MEDIA_IMAGES)) return PhotoAccess.Full
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            ) {
                return PhotoAccess.Partial
            }
            return PhotoAccess.Denied
        }
        // Pre-33: all-files access (granted for ROM scanning) or legacy storage read.
        val allFiles = runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        return if (allFiles || granted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            PhotoAccess.Full
        } else {
            PhotoAccess.Denied
        }
    }

    /** Minimum permission set for this Android version, for the runtime request. */
    fun requiredPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    suspend fun photos(limit: Int = PHOTO_LIMIT): List<DevicePhoto> = withContext(Dispatchers.IO) {
        if (access() == PhotoAccess.Denied) return@withContext emptyList()
        // DESCRIPTION is deprecated (but still a real column) on 29+; some OEM providers reject
        // it outright, so a failed query retries without the caption column.
        queryPhotos(includeCaption = true, limit = limit)
            ?: queryPhotos(includeCaption = false, limit = limit)
            ?: emptyList()
    }

    private fun queryPhotos(includeCaption: Boolean, limit: Int): List<DevicePhoto>? {
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.MIME_TYPE)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            if (includeCaption) add(MediaStore.Images.Media.DESCRIPTION)
        }.toTypedArray()

        return runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val captionCol = if (includeCaption) {
                    cursor.getColumnIndex(MediaStore.Images.Media.DESCRIPTION)
                } else {
                    -1
                }
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        val id = cursor.getLong(idCol)
                        val taken = cursor.getLong(takenCol)
                        val added = cursor.getLong(addedCol)
                        add(
                            DevicePhoto(
                                id = id.toString(),
                                contentUri = ContentUris.withAppendedId(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    id,
                                ).toString(),
                                displayName = cursor.getString(nameCol)
                                    ?.takeIf { it.isNotBlank() } ?: "Photo $id",
                                dateTakenMs = if (taken > 0) taken else added * 1000L,
                                width = cursor.getInt(widthCol),
                                height = cursor.getInt(heightCol),
                                mimeType = cursor.getString(mimeCol).orEmpty(),
                                album = cursor.getString(bucketCol)
                                    ?.takeIf { it.isNotBlank() } ?: "Photos",
                                caption = if (captionCol >= 0) {
                                    cursor.getString(captionCol)?.takeIf { it.isNotBlank() }
                                } else {
                                    null
                                },
                            ),
                        )
                    }
                }
            }
        }.getOrNull()
    }

    /**
     * System deletion confirmation for [uri] (API 30+). The returned sender must be launched from
     * the Activity; MediaStore performs the delete only after the user approves.
     */
    fun deleteRequest(uri: Uri): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri),
                ).intentSender
            }.getOrNull()
        } else {
            null
        }

    /**
     * Direct delete for API 29. Returns the recovery sender when the provider demands user
     * consent ([android.app.RecoverableSecurityException]); null sender + false means refusal.
     */
    fun deleteDirect(uri: Uri): DeleteOutcome = runCatching {
        val rows = context.contentResolver.delete(uri, null, null)
        DeleteOutcome(deleted = rows > 0, recoverySender = null)
    }.getOrElse { error ->
        val recoverable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (error as? android.app.RecoverableSecurityException)
                ?.userAction?.actionIntent?.intentSender
        } else {
            null
        }
        DeleteOutcome(deleted = false, recoverySender = recoverable)
    }

    data class DeleteOutcome(val deleted: Boolean, val recoverySender: IntentSender?)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        /** Browse cap — the gallery pages 10 at a time, so thousands of rows are never useful. */
        const val PHOTO_LIMIT = 4000
    }
}
