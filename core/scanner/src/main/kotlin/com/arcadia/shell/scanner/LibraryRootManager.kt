package com.arcadia.shell.scanner

import android.net.Uri
import com.arcadia.shell.database.repository.LibraryRootRepository
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.RootKind
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRootManager @Inject constructor(
    private val storageAccess: StorageAccess,
    private val rootRepository: LibraryRootRepository,
) {
    fun observeRoots(): Flow<List<LibraryRoot>> = rootRepository.observeRoots()

    suspend fun addFilesystemRoot(
        path: String,
        forcedPlatformId: String? = null,
        recursive: Boolean = true,
    ): Result<LibraryRoot> {
        if (!storageAccess.hasAllFilesAccess) {
            return Result.failure(IllegalStateException("All-files access has not been granted"))
        }
        val directory = File(path)
        if (!directory.isDirectory) {
            return Result.failure(IllegalArgumentException("Not a directory: $path"))
        }

        val root = LibraryRoot(
            id = stableId(directory.absolutePath),
            location = directory.absolutePath,
            kind = RootKind.Filesystem,
            label = directory.name.ifBlank { directory.absolutePath },
            forcedPlatformId = forcedPlatformId,
            recursive = recursive,
        )
        rootRepository.add(root)
        return Result.success(root)
    }

    /**
     * Registers a tree returned by the document picker. The persistable permission is taken before
     * the root is stored, so a root can never be persisted in an unreadable state.
     */
    suspend fun addSafRoot(
        treeUri: Uri,
        forcedPlatformId: String? = null,
        recursive: Boolean = true,
    ): Result<LibraryRoot> {
        storageAccess.persistTreePermission(treeUri)
        if (!storageAccess.hasTreePermission(treeUri)) {
            return Result.failure(IllegalStateException("Could not persist access to $treeUri"))
        }

        val root = LibraryRoot(
            id = stableId(treeUri.toString()),
            location = treeUri.toString(),
            kind = RootKind.SafTree,
            label = storageAccess.treeDisplayName(treeUri),
            forcedPlatformId = forcedPlatformId,
            recursive = recursive,
        )
        rootRepository.add(root)
        return Result.success(root)
    }

    suspend fun remove(root: LibraryRoot) {
        if (root.kind == RootKind.SafTree) {
            runCatching { storageAccess.releaseTreePermission(Uri.parse(root.location)) }
        }
        rootRepository.remove(root.id)
    }

    /** Suggested starting points shown when the user has not configured anything yet. */
    fun suggestedRoots(): List<StorageVolumeRoot> = storageAccess.storageVolumeRoots()

    private fun stableId(location: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(location.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
