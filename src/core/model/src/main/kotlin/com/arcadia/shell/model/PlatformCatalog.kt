package com.arcadia.shell.model

/**
 * The built-in system list. Kept as plain data so a scan never needs network access and so the
 * catalog can later be extended by user-imported platform packs.
 */
object PlatformCatalog {

    val platforms: List<GamePlatform> = listOf(
        GamePlatform(
            id = "nes",
            displayName = "Nintendo Entertainment System",
            shortName = "NES",
            extensions = setOf("nes", "fds", "unf", "unif", "nez", "zip", "7z"),
            folderAliases = setOf(
                "nes",
                "famicom",
                "familycomputer",
                "fc",
                "fds",
                "nintendo",
                "nintendoentertainmentsystem",
            ),
            screenScraperSystemId = 3,
        ),
        GamePlatform(
            id = "snes",
            displayName = "Super Nintendo",
            shortName = "SNES",
            extensions = setOf("sfc", "smc", "swc", "fig", "bs", "zip", "7z"),
            folderAliases = setOf(
                "snes",
                "sfc",
                "sns",
                "superfamicom",
                "supernintendo",
                "supernintendoentertainmentsystem",
            ),
            screenScraperSystemId = 4,
        ),
        GamePlatform(
            id = "n64",
            displayName = "Nintendo 64",
            shortName = "N64",
            extensions = setOf("n64", "z64", "v64", "ndd", "zip", "7z"),
            folderAliases = setOf(
                "n64",
                "nintendo64",
                "nintendou64",
                "ultra64",
                "u64",
                "64",
                "mupen64",
                "mupen64plus",
            ),
            screenScraperSystemId = 14,
        ),
        GamePlatform(
            id = "gamecube",
            displayName = "Nintendo GameCube",
            shortName = "GC",
            extensions = setOf("gcm", "gcz", "rvz", "iso", "ciso", "wia", "tgc"),
            folderAliases = setOf("gamecube", "gc", "ngc", "dolphin"),
            screenScraperSystemId = 13,
        ),
        GamePlatform(
            id = "wii",
            displayName = "Nintendo Wii",
            shortName = "Wii",
            extensions = setOf("wbfs", "rvz", "iso", "wia", "gcz", "ciso"),
            folderAliases = setOf("wii", "nintendowii"),
            screenScraperSystemId = 16,
        ),
        GamePlatform(
            id = "wiiu",
            displayName = "Nintendo Wii U",
            shortName = "Wii U",
            extensions = setOf("wud", "wux", "rpx", "wua"),
            folderAliases = setOf("wiiu", "wii_u", "cemu"),
            screenScraperSystemId = 18,
            usesSecondScreenForGameplay = true,
        ),
        GamePlatform(
            id = "switch",
            displayName = "Nintendo Switch",
            shortName = "NSW",
            extensions = setOf("nsp", "xci", "nca", "nro"),
            folderAliases = setOf("switch", "nsw", "nintendoswitch", "yuzu", "ryujinx", "eden"),
            screenScraperSystemId = 225,
        ),
        GamePlatform(
            id = "gb",
            displayName = "Game Boy",
            shortName = "GB",
            extensions = setOf("gb", "dmg", "zip", "7z"),
            folderAliases = setOf("gb", "gameboy", "gameboyclassic", "dmg"),
            screenScraperSystemId = 9,
        ),
        GamePlatform(
            id = "gbc",
            displayName = "Game Boy Color",
            shortName = "GBC",
            extensions = setOf("gbc", "zip", "7z"),
            folderAliases = setOf("gbc", "gameboycolor", "cgb"),
            screenScraperSystemId = 10,
        ),
        GamePlatform(
            id = "gba",
            displayName = "Game Boy Advance",
            shortName = "GBA",
            extensions = setOf("gba", "srl", "agb", "zip", "7z"),
            folderAliases = setOf(
                "gba",
                "gameboyadvance",
                "gameboyadvancesp",
                "gbadvance",
                "advance",
                "agb",
            ),
            screenScraperSystemId = 12,
        ),
        GamePlatform(
            id = "nds",
            displayName = "Nintendo DS",
            shortName = "NDS",
            extensions = setOf("nds", "dsi", "ids", "zip", "7z"),
            folderAliases = setOf(
                "nds",
                "ds",
                "nintendods",
                "nintendodslite",
                "dslite",
                "melonds",
                "drastic",
            ),
            screenScraperSystemId = 15,
            usesSecondScreenForGameplay = true,
        ),
        GamePlatform(
            id = "3ds",
            displayName = "Nintendo 3DS",
            shortName = "3DS",
            extensions = setOf("3ds", "cci", "cxi", "cia", "3dsx"),
            folderAliases = setOf(
                "3ds",
                "nintendo3ds",
                "citra",
                "azahar",
                "lime3ds",
                "citrus",
                "lime",
            ),
            screenScraperSystemId = 17,
            usesSecondScreenForGameplay = true,
        ),
        GamePlatform(
            id = "ps1",
            displayName = "PlayStation",
            shortName = "PS1",
            extensions = setOf("cue", "bin", "img", "chd", "pbp", "ecm", "m3u", "iso", "mdf"),
            folderAliases = setOf("ps1", "psx", "playstation", "psone", "duckstation"),
            screenScraperSystemId = 57,
        ),
        GamePlatform(
            id = "ps2",
            displayName = "PlayStation 2",
            shortName = "PS2",
            extensions = setOf("iso", "chd", "cso", "gz", "bin", "mdf", "nrg"),
            folderAliases = setOf("ps2", "playstation2", "aethersx2", "nethersx2"),
            screenScraperSystemId = 58,
        ),
        GamePlatform(
            id = "ps3",
            displayName = "PlayStation 3",
            shortName = "PS3",
            extensions = setOf("iso", "pkg", "self", "elf"),
            folderAliases = setOf("ps3", "playstation3", "rpcs3", "aps3e"),
            screenScraperSystemId = 59,
        ),
        GamePlatform(
            id = "psp",
            displayName = "PlayStation Portable",
            shortName = "PSP",
            extensions = setOf("iso", "cso", "chd", "pbp", "prx", "elf"),
            folderAliases = setOf("psp", "playstationportable", "ppsspp"),
            screenScraperSystemId = 61,
        ),
        GamePlatform(
            id = "psvita",
            displayName = "PlayStation Vita",
            shortName = "Vita",
            extensions = setOf("vpk", "psvita", "nps"),
            folderAliases = setOf("vita", "psvita", "playstationvita", "vita3k"),
            screenScraperSystemId = 62,
        ),
        GamePlatform(
            id = "genesis",
            displayName = "Sega Genesis",
            shortName = "MD",
            extensions = setOf("md", "gen", "smd", "68k", "bin"),
            folderAliases = setOf("genesis", "megadrive", "md", "segagenesis"),
            screenScraperSystemId = 1,
        ),
        GamePlatform(
            id = "sega32x",
            displayName = "Sega 32X",
            shortName = "32X",
            extensions = setOf("32x"),
            folderAliases = setOf("32x", "sega32x"),
            screenScraperSystemId = 19,
        ),
        GamePlatform(
            id = "segacd",
            displayName = "Sega CD",
            shortName = "SCD",
            extensions = setOf("cue", "chd", "iso", "ccd"),
            folderAliases = setOf("segacd", "megacd", "scd"),
            screenScraperSystemId = 20,
        ),
        GamePlatform(
            id = "mastersystem",
            displayName = "Sega Master System",
            shortName = "SMS",
            extensions = setOf("sms"),
            folderAliases = setOf("mastersystem", "sms", "segamastersystem"),
            screenScraperSystemId = 2,
        ),
        GamePlatform(
            id = "gamegear",
            displayName = "Sega Game Gear",
            shortName = "GG",
            extensions = setOf("gg"),
            folderAliases = setOf("gamegear", "gg"),
            screenScraperSystemId = 21,
        ),
        GamePlatform(
            id = "saturn",
            displayName = "Sega Saturn",
            shortName = "SAT",
            extensions = setOf("cue", "chd", "iso", "mds", "ccd"),
            folderAliases = setOf("saturn", "segasaturn", "yaba"),
            screenScraperSystemId = 22,
        ),
        GamePlatform(
            id = "dreamcast",
            displayName = "Sega Dreamcast",
            shortName = "DC",
            extensions = setOf("gdi", "cdi", "chd", "cue"),
            folderAliases = setOf("dreamcast", "dc", "flycast", "redream"),
            screenScraperSystemId = 23,
        ),
        GamePlatform(
            id = "arcade",
            displayName = "Arcade",
            shortName = "ARC",
            extensions = setOf("zip", "7z", "chd"),
            folderAliases = setOf("arcade", "mame", "fbneo", "fba", "cps1", "cps2", "cps3"),
            screenScraperSystemId = 75,
        ),
        GamePlatform(
            id = "neogeo",
            displayName = "Neo Geo",
            shortName = "NG",
            extensions = setOf("zip", "7z"),
            folderAliases = setOf("neogeo", "neo-geo", "ng"),
            screenScraperSystemId = 142,
        ),
        GamePlatform(
            id = "pcengine",
            displayName = "PC Engine",
            shortName = "PCE",
            extensions = setOf("pce", "sgx", "cue", "chd"),
            folderAliases = setOf("pcengine", "pce", "tg16", "turbografx", "turbografx16"),
            screenScraperSystemId = 31,
        ),
        GamePlatform(
            id = "3do",
            displayName = "3DO",
            shortName = "3DO",
            extensions = setOf("iso", "chd", "cue"),
            folderAliases = setOf("3do", "panasonic3do"),
            screenScraperSystemId = 29,
        ),
        GamePlatform(
            id = "atari2600",
            displayName = "Atari 2600",
            shortName = "2600",
            extensions = setOf("a26"),
            folderAliases = setOf("atari2600", "2600", "vcs"),
            screenScraperSystemId = 26,
        ),
        GamePlatform(
            id = "atarilynx",
            displayName = "Atari Lynx",
            shortName = "LNX",
            extensions = setOf("lnx"),
            folderAliases = setOf("lynx", "atarilynx"),
            screenScraperSystemId = 28,
        ),
        GamePlatform(
            id = "wonderswan",
            displayName = "WonderSwan",
            shortName = "WS",
            extensions = setOf("ws", "wsc"),
            folderAliases = setOf("wonderswan", "ws", "wsc"),
            screenScraperSystemId = 45,
        ),
        GamePlatform(
            id = "ngp",
            displayName = "Neo Geo Pocket",
            shortName = "NGP",
            extensions = setOf("ngp", "ngc"),
            folderAliases = setOf("ngp", "neogeopocket", "ngpc"),
            screenScraperSystemId = 25,
        ),
        GamePlatform(
            id = "msx",
            displayName = "MSX",
            shortName = "MSX",
            extensions = setOf("mx1", "mx2", "rom", "dsk"),
            folderAliases = setOf("msx", "msx2"),
            screenScraperSystemId = 113,
        ),
        GamePlatform(
            id = "c64",
            displayName = "Commodore 64",
            shortName = "C64",
            extensions = setOf("d64", "t64", "crt", "prg", "tap"),
            folderAliases = setOf("c64", "commodore64", "vice"),
            screenScraperSystemId = 66,
        ),
        GamePlatform(
            id = "amiga",
            displayName = "Commodore Amiga",
            shortName = "AMI",
            extensions = setOf("adf", "adz", "dms", "ipf", "lha", "hdf"),
            folderAliases = setOf("amiga", "commodoreamiga"),
            screenScraperSystemId = 64,
        ),
        GamePlatform(
            id = "dos",
            displayName = "MS-DOS",
            shortName = "DOS",
            extensions = setOf("conf", "dosz", "exe", "bat"),
            folderAliases = setOf("dos", "msdos", "dosbox"),
            screenScraperSystemId = 135,
        ),
    )

    private val byId: Map<String, GamePlatform> = platforms.associateBy { it.id }

    // Keys are normalized the same way lookups are, so aliases may be written naturally
    // ("neo-geo", "wii_u") without the two sides drifting apart.
    private val byFolderAlias: Map<String, GamePlatform> = buildMap {
        platforms.forEach { platform ->
            platform.folderAliases.forEach { alias -> put(normalizeFolderName(alias), platform) }
        }
    }

    /** Extensions claimed by exactly one platform, and therefore safe to identify a file by. */
    val exclusiveExtensions: Set<String> = platforms
        .flatMap { platform -> platform.extensions.map { it to platform.id } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.distinct().size == 1 }
        .keys

    val allExtensions: Set<String> = platforms.flatMapTo(mutableSetOf()) { it.extensions }

    fun byId(id: String?): GamePlatform? = when (id) {
        null -> null
        GamePlatform.Android.id -> GamePlatform.Android
        else -> byId[id]
    }

    fun requireById(id: String): GamePlatform = when (id) {
        GamePlatform.Android.id -> GamePlatform.Android
        else -> byId[id] ?: GamePlatform.Unknown
    }

    /** Matches a directory name against known aliases, ignoring case, spaces, and separators. */
    fun byFolderName(folderName: String): GamePlatform? =
        byFolderAlias[normalizeFolderName(folderName)]

    fun byExclusiveExtension(extension: String): GamePlatform? {
        val ext = extension.lowercase()
        if (ext !in exclusiveExtensions) return null
        return platforms.firstOrNull { ext in it.extensions }
    }

    private fun normalizeFolderName(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }
}
