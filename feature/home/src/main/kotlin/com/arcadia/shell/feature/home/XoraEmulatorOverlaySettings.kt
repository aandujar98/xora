package com.arcadia.shell.feature.home

/** Host overlay rows for XOrA's own emulator — not libretro core-option screens. */
fun isXoraNdsPlatform(platformId: String): Boolean =
    platformId.equals("nds", ignoreCase = true)

fun isXora3dsPlatform(platformId: String): Boolean =
    platformId.equals("3ds", ignoreCase = true)

fun isXoraDualScreenPlatform(platformId: String): Boolean =
    isXoraNdsPlatform(platformId) || isXora3dsPlatform(platformId)

/**
 * In-game overlay ids for DS / 3DS host display. Empty on every other system so
 * Advanced Settings does not need a leftover Nintendo DS / 3DS card.
 */
fun xoraOverlayDualScreenIds(platformId: String): List<String> = buildList {
    if (isXoraNdsPlatform(platformId)) {
        add("nds-layout")
        add("nds-gap")
    }
    if (isXora3dsPlatform(platformId)) {
        add("3ds-layout")
        add("g-res")
    }
    if (isXoraDualScreenPlatform(platformId)) {
        add("g-dual")
    }
}

/** Netplay identity that used to live under Advanced Settings → XOrA · Netplay. */
fun xoraOverlayNetplayIdentityIds(): List<String> = listOf("np-nick", "np-listen-port")
