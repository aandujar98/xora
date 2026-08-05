package com.arcadia.shell.scanner

data class ScannedFile(
    val name: String,
    /** Real path, or null when the file was reached through the Storage Access Framework. */
    val filePath: String?,
    /** SAF document uri, or null when the file was reached through the filesystem. */
    val documentUri: String?,
    val sizeBytes: Long,
    val lastModified: Long,
    /**
     * Directory names from the library root down to this file's parent. Platform detection reads
     * this from the deepest folder outwards, so `roms/nintendo/snes/game.sfc` resolves to SNES
     * rather than being confused by the broader "nintendo" folder above it.
     */
    val folderChain: List<String>,
) {
    val location: String get() = filePath ?: documentUri.orEmpty()
}
