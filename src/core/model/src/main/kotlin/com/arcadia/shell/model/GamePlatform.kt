package com.arcadia.shell.model

/**
 * A console or computer system the shell can present. [extensions] and [folderAliases] drive
 * scanning, [screenScraperSystemId] drives metadata lookups.
 *
 * Extensions overlap heavily across platforms (`iso`, `cue`, `chd`, `zip` are shared by a dozen
 * systems), so extension matching alone can never identify a platform. Folder naming is the
 * reliable signal and takes priority during a scan. [RomArchives] are attributed only via folder
 * name or a forced library root.
 */
data class GamePlatform(
    val id: String,
    val displayName: String,
    val shortName: String,
    val extensions: Set<String>,
    val folderAliases: Set<String>,
    val screenScraperSystemId: Int? = null,
    /**
     * True for systems where the second screen is part of the game itself (DS, 3DS, Wii U). Their
     * emulators drive both panels, so the shell must never draw companion content on the second
     * display while one of these is running.
     */
    val usesSecondScreenForGameplay: Boolean = false,
) {
    /** True when an extension belongs to this platform and to no other in the catalog. */
    fun ownsExtensionExclusively(extension: String): Boolean =
        extension in extensions && extension in PlatformCatalog.exclusiveExtensions

    companion object {
        val Unknown = GamePlatform(
            id = "unknown",
            displayName = "Unsorted",
            shortName = "???",
            extensions = emptySet(),
            folderAliases = emptySet(),
        )

        /**
         * Installed Android packages shown under the Apps tab. Kept out of [PlatformCatalog.platforms]
         * so ROM scanning and console settings never treat it as a real system.
         */
        val Android = GamePlatform(
            id = "android",
            displayName = "Android",
            shortName = "AND",
            extensions = emptySet(),
            folderAliases = emptySet(),
        )
    }
}
