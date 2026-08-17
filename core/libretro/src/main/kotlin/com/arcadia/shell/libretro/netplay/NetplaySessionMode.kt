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

/** GBA Game Link is two libmgba cores on one in-process SIO lockstep bus. */
fun usesGbaLockstep(platformId: String): Boolean =
    platformId.trim().equals("gba", ignoreCase = true)

/** gpSP netpacket only fakes Pokemon/RFU. Kirby needs a real MULTI cable. */
fun usesGbaGpspLink(platformId: String): Boolean = false

const val GBA_NETPLAY_CORE: String = "mgba"

fun netplayCoreName(platformId: String, currentCore: String): String = currentCore

fun gbaNetplayClientId(playerSlot: Int): Int = (playerSlot - 1).coerceAtLeast(0)

fun shouldStartGbaNetpacket(
    platformId: String,
    handheldLink: Boolean,
    localSlot: Int,
    playerCount: Int,
    alreadyStarted: Boolean,
): Boolean = false

fun shouldArmGbaLinkCable(
    platformId: String,
    handheldLink: Boolean,
    localSlot: Int,
    hosting: Boolean,
): Boolean = false

/**
 * Start in-process lockstep once this GBA has a seat. Retrying every frame after a
 * failed start toasted the lobby.
 */
fun shouldStartGbaLockstep(
    platformId: String,
    handheldLink: Boolean,
    localSlot: Int,
    alreadyActive: Boolean,
    alreadyAttempted: Boolean,
): Boolean =
    usesGbaLockstep(platformId) &&
        handheldLink &&
        localSlot >= 1 &&
        !alreadyActive &&
        !alreadyAttempted

/**
 * Restart lockstep when the lobby changes (host waiting vs two players linked)
 * so both phones reset together, the same way desktop mGBA starts both GBAs at once.
 */
fun gbaLockstepGenerationKey(localSlot: Int, linked: Boolean, playerCount: Int): String {
    val slot = localSlot.coerceAtLeast(1)
    return if (linked && playerCount >= 2) {
        "linked:$slot:$playerCount"
    } else {
        "solo:$slot"
    }
}

/**
 * Until a second person is linked, copy P1's pad onto the hidden GBA so both
 * local cores enter MULTI together. After P2 joins, each seat has its own pad.
 */
fun shouldMirrorGbaLockstepPartnerPad(linked: Boolean, playerCount: Int): Boolean =
    !linked || playerCount < 2

/** Game Link is always two GBAs on this phone; 3–4 player MULTI can come later. */
fun gbaLockstepPlayerCount(playerCount: Int): Int = playerCount.coerceIn(2, 4)

private val GBA_CART_EXTENSIONS = setOf("gba", "agb", "mb", "bin", "elf")

/**
 * mGBA lockstep cannot fopen a folder (RetroArch's "ROM Directory") or a zip
 * without libzip. Prefer a real cart file next to / inside that path.
 */
fun resolveGbaLockstepRomPath(romPath: String): String {
    val file = java.io.File(romPath)
    if (file.isFile) return file.absolutePath
    if (!file.isDirectory) return romPath
    val children = file.listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase() }.orEmpty()
    children.firstOrNull { it.extension.lowercase() in GBA_CART_EXTENSIONS }
        ?.let { return it.absolutePath }
    children.firstOrNull { it.extension.equals("zip", ignoreCase = true) }
        ?.let { return it.absolutePath }
    return romPath
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
