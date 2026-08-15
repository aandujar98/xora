package com.arcadia.shell.libretro

/**
 * Maps a XOrA platform / core to Nintendo Switch Online overlay stems such as `nso-gba`.
 *
 * Pack layout (from the NSO overlay zip):
 * ```
 * cfg/nso-gba.cfg          → img/nso-gba.png
 * cfg/nso-gba-full.cfg     → img/nso-gba-full.png
 * ```
 */
object NsoBezelCatalog {
    /** Systems that ship a cfg in the bundled NSO pack. */
    val PACK_SYSTEMS = listOf("gba", "gbc", "n64", "snes", "p8")

    /** RetroArch / NSO overlay file stem, e.g. `nso-gba`. */
    fun overlayStem(platformId: String, coreName: String = "", full: Boolean = false): String {
        val system = systemKey(platformId, coreName)
        return if (full) "nso-$system-full" else "nso-$system"
    }

    /**
     * File-name stems we will accept for [platformId], most specific first.
     * [preferFull] puts `nso-gba-full` ahead of `nso-gba` (Display → Full screen).
     */
    fun candidateStems(
        platformId: String,
        coreName: String = "",
        preferFull: Boolean = false,
    ): List<String> {
        val system = systemKey(platformId, coreName)
        val aliases = listOf(system) + systemAliases(system)
        val stems = linkedSetOf<String>()
        fun add(id: String, full: Boolean) {
            val suffix = if (full) "-full" else ""
            stems += "nso-$id$suffix"
            stems += "$id$suffix"
        }
        if (preferFull) aliases.forEach { add(it, full = true) }
        aliases.forEach { add(it, full = false) }
        if (!preferFull) aliases.forEach { add(it, full = true) }
        return stems.toList()
    }

    /** Native framebuffer aspect used until the first real frame arrives. */
    fun defaultAspect(platformId: String): Float = when (systemKey(platformId)) {
        "gba" -> 240f / 160f
        "gb", "gbc" -> 160f / 144f
        "gg", "gamegear" -> 160f / 144f
        "lynx", "atarilynx" -> 160f / 102f
        "ws", "wonderswan" -> 224f / 144f
        "ngp" -> 160f / 152f
        "nds" -> 256f / 384f
        "3ds" -> 400f / 480f
        "psp" -> 480f / 272f
        "p8" -> 128f / 128f
        else -> 4f / 3f
    }

    fun systemKey(platformId: String, coreName: String = ""): String {
        val id = platformId.trim().lowercase()
        val core = coreName.trim().lowercase()
        fromCoreName(core)?.let { return it }
        return when (id) {
            "mastersystem", "sms" -> "sms"
            "gamegear", "gg" -> "gg"
            "sega32x", "32x" -> "32x"
            "segacd", "megacd" -> "segacd"
            "genesis", "megadrive", "md" -> "genesis"
            "atarilynx", "lynx" -> "lynx"
            "pcengine", "tg16", "pce" -> "pce"
            "wonderswan", "ws" -> "ws"
            "neogeo", "ng" -> "neogeo"
            "gamecube", "gc" -> "gc"
            "pico8", "pico-8", "p8" -> "p8"
            else -> id.ifBlank { "generic" }
        }
    }

    private fun fromCoreName(core: String): String? {
        if (core.isBlank()) return null
        return when {
            core.contains("mgba") || core.contains("vba") -> "gba"
            core.contains("gambatte") || core.contains("sameboy") ||
                core.contains("tgbdual") -> "gbc"
            core.contains("mupen") || core.contains("parallel_n64") -> "n64"
            core.contains("snes9x") || core.contains("bsnes") || core.contains("mesen-s") -> "snes"
            core.contains("fceumm") || core.contains("nestopia") ||
                core == "mesen" || core.startsWith("mesen_") -> "nes"
            core.contains("melond") || core.contains("desmume") -> "nds"
            core.contains("citra") || core.contains("azahar") -> "3ds"
            core.contains("genesis_plus") || core.contains("picodrive") -> "genesis"
            core.contains("pcsx") || core.contains("beetle_psx") ||
                core.contains("swanstation") -> "ps1"
            core.contains("ppsspp") -> "psp"
            core.contains("retro8") || core.contains("pico") -> "p8"
            else -> null
        }
    }

    private fun systemAliases(system: String): List<String> = when (system) {
        "gbc" -> listOf("gb")
        "gb" -> listOf("gbc")
        "genesis" -> listOf("md", "megadrive")
        "sms" -> listOf("mastersystem")
        "gg" -> listOf("gamegear")
        "pce" -> listOf("pcengine", "tg16")
        "lynx" -> listOf("atarilynx")
        "ws" -> listOf("wonderswan")
        "p8" -> listOf("pico8", "pico-8")
        else -> emptyList()
    }
}
