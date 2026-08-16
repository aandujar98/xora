package com.arcadia.shell.libretro.netplay

/**
 * How a netplay session is simulated.
 *
 * Handhelds (GB, GBA, PSP, …) are two (or more) devices each running their own game — like a
 * link cable. Home consoles (NES, SNES, N64, …) are one couch: the host runs the only core
 * and every joiner's pad is another controller on that same instance.
 */
enum class NetplaySessionMode {
    SharedConsole,
    HandheldLink,
}

private val HANDHELD_PLATFORM_IDS = setOf(
    "gb",
    "gbc",
    "gba",
    "nds",
    "3ds",
    "psp",
    "psvita",
    "gamegear",
    "atarilynx",
    "wonderswan",
    "ngp",
    "ngpc",
    "virtualboy",
    "pokemonmini",
    "gp32",
)

fun netplaySessionMode(platformId: String): NetplaySessionMode {
    val id = platformId.trim().lowercase()
    return if (id in HANDHELD_PLATFORM_IDS) {
        NetplaySessionMode.HandheldLink
    } else {
        NetplaySessionMode.SharedConsole
    }
}

fun NetplaySessionMode.isSharedConsole(): Boolean = this == NetplaySessionMode.SharedConsole
