package com.arcadia.shell.model

/**
 * Container formats that commonly wrap a single cartridge ROM.
 *
 * These extensions are never exclusive to one system (arcade sets use them too), so a scan can only
 * attribute an archive when a folder name or forced root identifies the platform. Scraping opens
 * the inner file for hashing; launch recipes treat the archive itself as the bootable path.
 */
object RomArchives {
    val extensions: Set<String> = setOf("zip", "7z")

    fun contains(extension: String): Boolean = extension.lowercase() in extensions
}
