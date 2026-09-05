package com.arcadia.shell.scraper

/**
 * Official XMB system-card art shipped in `assets/platform_art/{platformId}.png`
 * (GitHub release tag `PLATFORMS`).
 *
 * X360.png is omitted — the catalog has no Xbox 360 platform yet.
 */
object BundledPlatformArt {
    const val ASSET_DIR = "platform_art"

    val PLATFORM_IDS: Set<String> = setOf(
        "3ds",
        "dreamcast",
        "gamecube",
        "gba",
        "n64",
        "nds",
        "ps1",
        "ps2",
        "psp",
        "saturn",
        "wii",
        "wiiu",
    )

    fun assetNameFor(platformId: String): String = "$platformId.png"
}
