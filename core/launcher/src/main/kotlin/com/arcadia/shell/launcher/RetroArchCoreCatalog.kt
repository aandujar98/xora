package com.arcadia.shell.launcher

/**
 * Known libretro cores per platform, used for seeding launch recipes and the
 * Choose Emulator picker. [playerId] is stable across upgrades.
 */
data class RetroArchCoreOption(
    val platformId: String,
    val core: String,
    val label: String,
    /**
     * Stable player uniqueId. Primary cores use `retroarch.$platformId`;
     * alternates append a suffix (`retroarch.n64.parallel_n64`).
     */
    val playerId: String,
)

object RetroArchCoreCatalog {

    val all: List<RetroArchCoreOption> = listOf(
        core("nes", "mesen", "Mesen"),
        core("nes", "nestopia", "Nestopia UE", suffix = "nestopia"),
        core("nes", "fceumm", "FCEUmm", suffix = "fceumm"),
        core("snes", "snes9x", "Snes9x"),
        core("snes", "bsnes", "bsnes", suffix = "bsnes"),
        core("n64", RetroArchPackages.MUPEN64PLUS_NEXT_CORE, "Mupen64Plus-Next"),
        // RetroArch ships GLES-specific Mupen builds on Android; they are separate `.so` files.
        core("n64", "mupen64plus_next_gles3", "Mupen64Plus-Next GLES3", suffix = "gles3"),
        core("n64", "mupen64plus_next_gles2", "Mupen64Plus-Next GLES2", suffix = "gles2"),
        core("n64", "parallel_n64", "ParaLLEl N64", suffix = "parallel_n64"),
        core("gb", "gambatte", "Gambatte"),
        core("gbc", "gambatte", "Gambatte"),
        core("gba", "mgba", "mGBA"),
        core("nds", "melonds", "melonDS"),
        core("genesis", "genesis_plus_gx", "Genesis Plus GX"),
        core("sega32x", "picodrive", "PicoDrive"),
        core("segacd", "genesis_plus_gx", "Genesis Plus GX"),
        core("mastersystem", "genesis_plus_gx", "Genesis Plus GX"),
        core("gamegear", "genesis_plus_gx", "Genesis Plus GX"),
        core("saturn", "yabause", "Yabause"),
        core("dreamcast", "flycast", "Flycast"),
        core("ps1", "swanstation", "SwanStation"),
        core("ps1", "pcsx_rearmed", "PCSX-ReARMed", suffix = "pcsx_rearmed"),
        core("ps1", "duckstation", "DuckStation", suffix = "duckstation"),
        core("ps1", "beetle_psx_hw", "Beetle PSX HW", suffix = "beetle_psx_hw"),
        core("psp", "ppsspp", "PPSSPP"),
        core("3ds", "azahar", "Azahar"),
        core("3ds", "citra", "Citra", suffix = "citra"),
        core("pcengine", "mednafen_pce_fast", "Beetle PCE Fast"),
        core("arcade", "fbneo", "FinalBurn Neo"),
        core("neogeo", "fbneo", "FinalBurn Neo"),
        core("atari2600", "stella", "Stella"),
        core("atarilynx", "handy", "Handy"),
        core("wonderswan", "mednafen_wswan", "Beetle WonderSwan"),
        core("ngp", "mednafen_ngp", "Beetle NeoPop"),
        core("msx", "bluemsx", "blueMSX"),
        core("c64", "vice_x64", "VICE x64"),
        core("amiga", "puae", "PUAE"),
        core("3do", "opera", "Opera"),
        core("dos", "dosbox_pure", "DOSBox Pure"),
    )

    fun forPlatform(platformId: String): List<RetroArchCoreOption> =
        all.filter { it.platformId == platformId }

    fun byPlayerId(playerId: String): RetroArchCoreOption? =
        all.firstOrNull { it.playerId == playerId }

    fun byCore(platformId: String, core: String): RetroArchCoreOption? =
        all.firstOrNull { it.platformId == platformId && it.core == core }

    fun byCoreName(core: String): RetroArchCoreOption? =
        all.firstOrNull { it.core.equals(core, ignoreCase = true) }

    /**
     * Platform for an arbitrary core base name found on disk.
     *
     * Cores are versioned and hardware-specialised faster than any hardcoded list can track
     * (`mupen64plus_next_gles3`, `bsnes_hd_beta`, …), so an exact catalog hit is tried first and
     * then the longest catalog core name that prefixes [core]. That keeps new suffixed variants
     * mapped to the right system without another release.
     */
    fun platformForCore(core: String): String? {
        val name = core.lowercase().removeSuffix("_libretro")
        byCoreName(name)?.let { return it.platformId }
        EXTRA_CORE_PLATFORMS[name]?.let { return it }

        val prefixMatch = all
            .filter { name.startsWith(it.core.lowercase()) }
            .maxByOrNull { it.core.length }
        if (prefixMatch != null) return prefixMatch.platformId

        return EXTRA_CORE_PLATFORMS.entries
            .filter { name.startsWith(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /** Stable player id for a core discovered on disk that has no catalog entry. */
    fun playerIdForDiscovered(platformId: String, core: String): String =
        byCore(platformId, core)?.playerId ?: "retroarch.$platformId.${core.lowercase()}"

    /**
     * Human label for a core base name, e.g. `mupen64plus_next_gles3` →
     * `Mupen64Plus-Next GLES3`. Catalog labels win; anything else is title-cased.
     */
    fun labelForCore(core: String): String {
        byCoreName(core)?.let { return it.label }
        return core.split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                val lower = part.lowercase()
                KNOWN_WORDS[lower]
                    ?: lower.takeIf { GRAPHICS_SUFFIX.matches(it) }?.uppercase()
                    ?: part.replaceFirstChar { it.uppercaseChar() }
            }
    }

    /** `gles3`, `gles31`, `gl3`, … all read better fully uppercased. */
    private val GRAPHICS_SUFFIX = Regex("""^gl(es)?\d+$""")

    /** Cores with no seeded launch recipe, kept only so disk scans can classify them. */
    private val EXTRA_CORE_PLATFORMS: Map<String, String> = mapOf(
        "mupen64plus" to "n64",
        "parallel_n64_gles3" to "n64",
        "bsnes_hd_beta" to "snes",
        "bsnes_mercury" to "snes",
        "snes9x2010" to "snes",
        "mesen_s" to "snes",
        "mgba_gles" to "gba",
        "vba_next" to "gba",
        "vbam" to "gba",
        "gpsp" to "gba",
        "sameboy" to "gb",
        "tgbdual" to "gb",
        "desmume" to "nds",
        "desmume2015" to "nds",
        "melonds_ds" to "nds",
        "citra" to "3ds",
        "citra2018" to "3ds",
        "azahar" to "3ds",
        "panda3ds" to "3ds",
        "dolphin" to "gamecube",
        "pcsx2" to "ps2",
        "play" to "ps2",
        "ppsspp_gles" to "psp",
        "flycast_gles2" to "dreamcast",
        "kronos" to "saturn",
        "beetle_saturn" to "saturn",
        "yabasanshiro" to "saturn",
        "mednafen_psx" to "ps1",
        "mednafen_psx_hw" to "ps1",
        "mednafen_supergrafx" to "pcengine",
        "mednafen_pce" to "pcengine",
        "picodrive" to "genesis",
        "mame" to "arcade",
        "mame2003_plus" to "arcade",
        "fbalpha2012" to "arcade",
        "prosystem" to "atari7800",
        "atari800" to "atari800",
        "virtualjaguar" to "atarijaguar",
        "o2em" to "odyssey2",
        "vecx" to "vectrex",
    )

    private val KNOWN_WORDS: Map<String, String> = mapOf(
        "gles2" to "GLES2",
        "gles3" to "GLES3",
        "gl" to "GL",
        "hw" to "HW",
        "hd" to "HD",
        "ds" to "DS",
        "pce" to "PCE",
        "psx" to "PSX",
        "ngp" to "NGP",
        "msx" to "MSX",
        "next" to "Next",
        "plus" to "Plus",
        "gx" to "GX",
        "ue" to "UE",
        "beta" to "beta",
        "mupen64plus" to "Mupen64Plus",
        "ppsspp" to "PPSSPP",
        "pcsx2" to "PCSX2",
        "snes9x" to "Snes9x",
        "bsnes" to "bsnes",
        "mgba" to "mGBA",
        "melonds" to "melonDS",
        "azahar" to "Azahar",
        "fbneo" to "FinalBurn Neo",
        "mame" to "MAME",
    )

    private fun core(
        platformId: String,
        core: String,
        label: String,
        suffix: String? = null,
    ) = RetroArchCoreOption(
        platformId = platformId,
        core = core,
        label = label,
        playerId = if (suffix == null) {
            "retroarch.$platformId"
        } else {
            "retroarch.$platformId.$suffix"
        },
    )
}
