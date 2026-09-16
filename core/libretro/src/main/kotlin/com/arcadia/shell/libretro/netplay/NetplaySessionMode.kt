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
 * Host sits as Player 1 immediately. A joiner is Player 2 for Game Link even
 * before ASSIGN, matching how the host boots two GBAs the moment they press Host.
 */
fun gbaLockstepLocalSlot(playerSlot: Int, hosting: Boolean, joining: Boolean): Int = when {
    playerSlot >= 1 -> playerSlot
    hosting -> 1
    joining -> 2
    else -> 0
}

/** Hidden GBA on this phone: host shows Core 0, joiner shows Core 1. */
fun gbaLockstepHiddenPort(localSlot: Int): Int {
    val self = (localSlot - 1).coerceIn(0, 1)
    return 1 - self
}

/**
 * Clone this phone's pad onto the hidden GBA only while waiting alone.
 * Once Player 2 is linked, Core 0 is P1's pad and Core 1 is P2's pad on both
 * phones — cloning would make two private 2-player games that never meet.
 */
fun shouldMirrorGbaLockstepPartnerPad(linked: Boolean, playerCount: Int): Boolean =
    !linked || playerCount < 2

/**
 * Host-only: hold Player 1's pad this many frames so Player 2's network taps
 * land on the hidden GBA at the same time. The joiner stays immediate — that
 * side already felt instant. Do not stall the emu thread or match frame ids.
 */
const val GBA_LOCKSTEP_INPUT_DELAY_FRAMES: Int = 3

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
