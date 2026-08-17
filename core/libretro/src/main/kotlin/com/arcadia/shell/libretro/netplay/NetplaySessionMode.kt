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

/** GBA Game Link now uses the gpSP libretro core's built-in netpacket cable. */
@Suppress("UNUSED_PARAMETER")
fun usesGbaLockstep(platformId: String): Boolean = false

/** GBA netplay runs gpSP so each device can talk over the core's link-cable / RFU. */
fun usesGbaGpspLink(platformId: String): Boolean =
    platformId.trim().equals("gba", ignoreCase = true)

const val GBA_NETPLAY_CORE: String = "gpsp"

fun netplayCoreName(platformId: String, currentCore: String): String =
    if (usesGbaGpspLink(platformId)) GBA_NETPLAY_CORE else currentCore

fun gbaNetplayClientId(playerSlot: Int): Int = (playerSlot - 1).coerceAtLeast(0)

/**
 * Plug gpSP's cable as soon as this GBA is hosting or has a seat. Waiting for a
 * second player left the host with send_fn=NULL, so Kirby never saw a cable.
 */
fun shouldStartGbaNetpacket(
    platformId: String,
    handheldLink: Boolean,
    localSlot: Int,
    playerCount: Int,
    alreadyStarted: Boolean,
): Boolean =
    usesGbaGpspLink(platformId) &&
        handheldLink &&
        localSlot >= 1 &&
        playerCount >= 1 &&
        !alreadyStarted

/** Keep writing SIOCNT/SIOMULTI while a GBA netplay session is up, even before GO. */
fun shouldArmGbaLinkCable(
    platformId: String,
    handheldLink: Boolean,
    localSlot: Int,
    hosting: Boolean,
): Boolean =
    usesGbaGpspLink(platformId) &&
        handheldLink &&
        (hosting || localSlot >= 1)

/**
 * Start in-process lockstep once per handshake. Unused: GBA netplay now uses gpSP netpacket.
 */
@Suppress("UNUSED_PARAMETER")
fun shouldStartGbaLockstep(
    platformId: String,
    handheldLink: Boolean,
    localSlot: Int,
    alreadyActive: Boolean,
    alreadyAttempted: Boolean,
): Boolean = false

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
