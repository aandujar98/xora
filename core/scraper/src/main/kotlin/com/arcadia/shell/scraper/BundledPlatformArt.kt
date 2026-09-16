package com.arcadia.shell.scraper

/**
 * Official XMB system-card art shipped in `assets/platform_art/{platformId}.png`
 * (GitHub release tags `PLATFORMS`, then `Platform-set-2`).
 *
 * Xbox360.png (from `Platform-set-2`) is omitted — the catalog has no Xbox 360 platform yet.
 * GameBoyAdvance.png (also from `Platform-set-2`) is omitted too: unlike the rest of that drop it
 * arrived as a 1024x1024 grayscale image, not the usual 462x248 card, so it reads as a mismatched
 * or wrong asset rather than a real replacement for the existing "gba" card.
 */
object BundledPlatformArt {
    const val ASSET_DIR = "platform_art"

    val PLATFORM_IDS: Set<String> = setOf(
        "3ds",
        "android",
        "atari2600",
        "dreamcast",
        "gamecube",
        "gba",
        "gbc",
        "genesis",
        "n64",
        "nds",
        "nes",
        "ps1",
        "ps2",
        "psp",
        "psvita",
        "saturn",
        "snes",
        "switch",
        "wii",
        "wiiu",
    )

    fun assetNameFor(platformId: String): String = "$platformId.png"
}
