package com.arcadia.shell.model

enum class LaunchDisplayPreference {
    /** Follow the global setting for the platform or folder. */
    Inherit,

    /** Always boot on the display currently showing the grid. */
    GridScreen,

    /** Always boot on the other display, if there is one. */
    OtherScreen,
}

enum class ScrapeState {
    Pending,
    Matched,
    NoMatch,
    Failed,
}

data class Game(
    val id: String,
    val title: String,
    val sortKey: String,
    val platformId: String,
    val fileName: String,
    /** Real path, present only for roots indexed with all-files access. */
    val filePath: String?,
    /** SAF document uri, present only for roots indexed through the document picker. */
    val documentUri: String?,
    val sizeBytes: Long,
    val favorite: Boolean = false,
    val playCount: Int = 0,
    val playTimeMs: Long = 0,
    val lastPlayedAt: Long? = null,
    val heroImagePath: String? = null,
    val logoImagePath: String? = null,
    val boxArtPath: String? = null,
    /**
     * Imported hover clip (Select → ROM options). If unset, the shell also looks next to the ROM
     * for `Game name.mp3` / `.wav` — see [RomSoundBiteLocator].
     */
    val soundBitePath: String? = null,
    /**
     * Encoded trailer reference ([TrailerRefs]): direct media URI/path or `youtube:` id.
     * Null when unknown or when a lookup found nothing (see [trailerResolved]).
     */
    val trailerUrl: String? = null,
    /** True after a trailer lookup finished, whether or not [trailerUrl] is set. */
    val trailerResolved: Boolean = false,
    val playerIdOverride: String? = null,
    val launchDisplayPreference: LaunchDisplayPreference = LaunchDisplayPreference.Inherit,
    val scrapeState: ScrapeState = ScrapeState.Pending,
    /** RetroAchievements / ScreenScraper hashes; null until a hash pass has run. */
    val crc32: String? = null,
    val md5: String? = null,
    val sha1: String? = null,
) {
    val platform: GamePlatform get() = PlatformCatalog.requireById(platformId)

    /** True for rows that represent an installed Android package rather than a ROM. */
    val isAndroidApp: Boolean get() = platformId == GamePlatform.Android.id

    /** Artwork to show in the grid, preferring box art and degrading to whatever exists. */
    val gridArt: String? get() = boxArtPath ?: heroImagePath ?: logoImagePath

    val hasArtwork: Boolean
        get() = boxArtPath != null || heroImagePath != null || logoImagePath != null

    /**
     * Whether the shell may take over the second screen with a companion panel while this title
     * runs. DS / 3DS / Wii U emulators paint their own bottom screen, and installed Android apps
     * have neither scraped metadata nor a manual to show.
     */
    val supportsCompanionScreen: Boolean
        get() = !isAndroidApp &&
            platformId != GamePlatform.Unknown.id &&
            !platform.usesSecondScreenForGameplay
}
