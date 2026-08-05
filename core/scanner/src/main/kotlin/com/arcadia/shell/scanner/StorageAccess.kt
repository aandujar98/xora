package com.arcadia.shell.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StorageVolumeRoot(
    val label: String,
    val path: String,
    val isRemovable: Boolean,
)

/**
 * Gatekeeper for the two very different ways this app can read a ROM library.
 *
 * All-files access is the preferred mode and not merely a convenience: several popular emulators
 * (Dolphin, DuckStation) and the in-process XOrA Emulator (Libretro) accept only a real
 * filesystem path in their launch path, which the Storage Access Framework fundamentally cannot
 * produce. SAF remains as a reduced-capability fallback that can still drive the document-uri
 * emulators such as PPSSPP and AetherSX2.
 */
@Singleton
class StorageAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val hasAllFilesAccess: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * On API 30+ this deep-links straight to this app's toggle. Some OEM builds do not implement
     * the per-app screen, so the caller must be ready for the generic list instead.
     */
    fun allFilesAccessIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                "package:${context.packageName}".toUri(),
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        }

    fun openDocumentTreeIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }

    /**
     * Without this the granted tree is forgotten when the process dies, and the library would
     * become unreadable on next launch.
     */
    fun persistTreePermission(treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun releaseTreePermission(treeUri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun hasTreePermission(treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }

    fun treeDisplayName(treeUri: Uri): String =
        DocumentsContract.getTreeDocumentId(treeUri)
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
            ?: treeUri.lastPathSegment
            ?: treeUri.toString()

    /**
     * Discovers mounted volumes by walking up from this app's per-volume private directories.
     * `getExternalFilesDirs` is the only approach that reports removable cards consistently across
     * API 29 through 37 without relying on hidden StorageManager methods.
     */
    fun storageVolumeRoots(): List<StorageVolumeRoot> {
        val volumes = context.getExternalFilesDirs(null)
            .filterNotNull()
            .mapNotNull { dir ->
                val path = dir.absolutePath.substringBefore("/Android/")
                    .takeIf { it.isNotBlank() && it != dir.absolutePath }
                path?.let { File(it) }
            }
            .filter { it.exists() }
            .distinctBy { it.absolutePath }

        val primary = Environment.getExternalStorageDirectory()?.absolutePath

        return volumes.map { dir ->
            StorageVolumeRoot(
                label = if (dir.absolutePath == primary) "Internal storage" else dir.name,
                path = dir.absolutePath,
                isRemovable = dir.absolutePath != primary,
            )
        }
    }
}
