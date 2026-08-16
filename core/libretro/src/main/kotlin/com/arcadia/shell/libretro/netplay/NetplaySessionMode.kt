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

/**
 * Joiners never load the host snapshot. Home-console joiners only display host video.
 * Handhelds each keep their own cart — cloning the host save overwrote the joiner and
 * unserialize of mGBA SIO pointers crashed the lobby the moment a second person joined.
 */
fun NetplaySessionMode.joinerAppliesHostState(): Boolean = false

/**
 * Home-console sessions used to freeze and broadcast a savestate. That payload
 * kicked Nakama, and joiners never loaded it anyway (they display host video).
 * Both modes now assign a slot and GO.
 */
fun NetplaySessionMode.usesSavestateBarrier(): Boolean = this == NetplaySessionMode.SharedConsole
