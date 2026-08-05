package com.arcadia.shell.retroachievements

/**
 * Maps Arcadia [com.arcadia.shell.model.GamePlatform] ids to RetroAchievements console ids
 * (`rc_consoles.h`). Used for Web API hash-library fallback when Connect `gameid` is blocked.
 */
object RaConsoleIds {
    fun forPlatform(platformId: String): Int? = when (platformId) {
        "genesis" -> 1
        "n64" -> 2
        "snes" -> 3
        "gb" -> 4
        "gba" -> 5
        "gbc" -> 6
        "nes" -> 7
        "pcengine", "tg16" -> 8
        "segacd" -> 9
        "sega32x" -> 10
        "mastersystem" -> 11
        "ps1" -> 12
        "lynx" -> 13
        "ngp", "ngpc" -> 14
        "gamegear" -> 15
        "gamecube" -> 16
        "jaguar" -> 17
        "nds" -> 18
        "wii" -> 19
        "wiiu" -> 20
        "ps2" -> 21
        "atari2600" -> 25
        "arcade" -> 27
        "virtualboy" -> 28
        "saturn" -> 39
        "dreamcast" -> 40
        "psp" -> 41
        "3do" -> 43
        "colecovision" -> 44
        "intellivision" -> 45
        "vectrex" -> 46
        "wonderswan" -> 53
        "pcenginecd" -> 76
        "neogeo" -> 27 // arcade / FBNeo shared id on RA for many sets
        "3ds" -> 62
        else -> null
    }

    /** Best-effort reverse map for focusing a local library game from an RA console id. */
    fun platformIdFor(consoleId: Int): String? = when (consoleId) {
        1 -> "genesis"
        2 -> "n64"
        3 -> "snes"
        4 -> "gb"
        5 -> "gba"
        6 -> "gbc"
        7 -> "nes"
        8 -> "pcengine"
        9 -> "segacd"
        10 -> "sega32x"
        11 -> "mastersystem"
        12 -> "ps1"
        13 -> "lynx"
        14 -> "ngp"
        15 -> "gamegear"
        16 -> "gamecube"
        18 -> "nds"
        19 -> "wii"
        20 -> "wiiu"
        21 -> "ps2"
        25 -> "atari2600"
        27 -> "arcade"
        28 -> "virtualboy"
        39 -> "saturn"
        40 -> "dreamcast"
        41 -> "psp"
        43 -> "3do"
        53 -> "wonderswan"
        62 -> "3ds"
        else -> null
    }
}
