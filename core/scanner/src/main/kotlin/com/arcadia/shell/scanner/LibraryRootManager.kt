package com.arcadia.shell.scanner

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.arcadia.shell.database.dao.GameDao
import com.arcadia.shell.database.repository.LibraryRootRepository
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.RootKind
import com.arcadia.shell.model.StorageDocumentIds
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRootManager @Inject constructor(
    private val storageAccess: StorageAccess,
    private val rootRepository: LibraryRootRepository,
    private val gameDao: GameDao,
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

        val absolute = directory.absolutePath
        // Drop overlapping SAF roots so the same folder is not indexed twice.
        removeOverlappingSafRoots(absolute)

        val root = LibraryRoot(
            id = stableId(absolute),
            location = absolute,
            kind = RootKind.Filesystem,
            label = directory.name.ifBlank { absolute },
            forcedPlatformId = forcedPlatformId,
            recursive = recursive,
        )
        rootRepository.add(root)
        return Result.success(root)
    }

    /**
     * Registers a tree returned by the document picker. The persistable permission is taken before
     * the root is stored, so a root can never be persisted in an unreadable state.
     *
     * When all-files access is already granted and the tree maps to a real directory, the root is
     * stored as [RootKind.Filesystem] instead — XOrA Launcher and XOrA Emulator then share one
     * path-based library identity.
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

        val resolvedPath = resolveTreePath(treeUri)
        if (storageAccess.hasAllFilesAccess && resolvedPath != null) {
            return addFilesystemRoot(
                path = resolvedPath,
                forcedPlatformId = forcedPlatformId,
                recursive = recursive,
            )
        }

        // Avoid a second SAF root that covers the same tree document id.
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        if (treeDocumentId != null) {
            for (existing in rootRepository.getRoots()) {
                if (existing.kind != RootKind.SafTree) continue
                val existingId = runCatching {
                    DocumentsContract.getTreeDocumentId(existing.location.toUri())
                }.getOrNull() ?: continue
                if (StorageDocumentIds.normalizeDocumentId(existingId) ==
                    StorageDocumentIds.normalizeDocumentId(treeDocumentId)
                ) {
                    return Result.success(existing)
                }
            }
            // If a filesystem root already covers this tree, keep the filesystem one.
            val treePath = StorageDocumentIds.pathForDocumentId(treeDocumentId)
            if (treePath != null) {
                val covering = rootRepository.getRoots().firstOrNull { root ->
                    root.kind == RootKind.Filesystem && pathsOverlap(root.location, treePath)
                }
                if (covering != null) {
                    return Result.success(covering)
                }
            }
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
        gameDao.deleteByRootId(root.id)
        rootRepository.remove(root.id)
    }

    /** Suggested starting points shown when the user has not configured anything yet. */
    fun suggestedRoots(): List<StorageVolumeRoot> = storageAccess.storageVolumeRoots()

    private suspend fun removeOverlappingSafRoots(filesystemPath: String) {
        val roots = rootRepository.getRoots()
        for (root in roots) {
            if (root.kind != RootKind.SafTree) continue
            val treePath = resolveTreePath(root.location.toUri()) ?: continue
            if (pathsOverlap(filesystemPath, treePath)) {
                remove(root)
            }
        }
    }

    private fun resolveTreePath(treeUri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        val path = StorageDocumentIds.pathForDocumentId(documentId) ?: return null
        return path.takeIf { File(it).isDirectory }
    }

    private fun pathsOverlap(a: String, b: String): Boolean {
        val left = a.replace('\\', '/').trimEnd('/')
        val right = b.replace('\\', '/').trimEnd('/')
        return left == right || left.startsWith("$right/") || right.startsWith("$left/")
    }

    private fun stableId(location: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(location.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
