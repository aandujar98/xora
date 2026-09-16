package com.arcadia.shell.libretro

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class XoraCoreEntry(
    val platformId: String,
    val core: String,
    val label: String,
    val license: String = "",
)

@Serializable
private data class XoraCoreCatalogFile(
    val repoBaseUrl: String = "https://buildbot.libretro.com/nightly/android/latest",
    val cores: List<XoraCoreEntry> = emptyList(),
)

/**
 * Phase-1 / extended core list shipped in assets. Primary core per platform is the first entry.
 */
@Singleton
class XoraCoreCatalog @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val catalog: XoraCoreCatalogFile = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
            json.decodeFromString(XoraCoreCatalogFile.serializer(), reader.readText())
        }
    }.getOrElse {
        XoraCoreCatalogFile(cores = FALLBACK_CORES)
    }

    val repoBaseUrl: String get() = catalog.repoBaseUrl.ifBlank { DEFAULT_REPO }

    val all: List<XoraCoreEntry> get() = catalog.cores

    fun forPlatform(platformId: String): List<XoraCoreEntry> =
        catalog.cores.filter { it.platformId == platformId }

    fun primaryForPlatform(platformId: String): XoraCoreEntry? =
        forPlatform(platformId).firstOrNull()

    fun byCore(platformId: String, core: String): XoraCoreEntry? =
        catalog.cores.firstOrNull {
            it.platformId == platformId && it.core.equals(core, ignoreCase = true)
        }

    fun playerId(platformId: String, core: String = primaryForPlatform(platformId)?.core ?: "core"): String =
        "xora.libretro.$platformId.${core.lowercase()}"

    /** Platforms that ship with a default XOrA Libretro recipe (Phase 1 + BIOS-heavy Phase 2). */
    val supportedPlatformIds: Set<String>
        get() = catalog.cores.map { it.platformId }.toSet()

    companion object {
        const val ASSET_NAME = "libretro_cores.json"
        const val DEFAULT_REPO = "https://buildbot.libretro.com/nightly/android/latest"

        /** Used if assets fail to load so the module still seeds players. */
        val FALLBACK_CORES: List<XoraCoreEntry> = listOf(
            // FCEUmm first — Mesen has been crashing on some Android/NES boots.
            XoraCoreEntry("nes", "fceumm", "FCEUmm", "GPLv2"),
            XoraCoreEntry("nes", "nestopia", "Nestopia UE", "GPLv2"),
            XoraCoreEntry("nes", "mesen", "Mesen", "GPLv2"),
            XoraCoreEntry("snes", "snes9x", "Snes9x", "Non-commercial"),
            // Android buildbot ships GLES variants only (no plain mupen64plus_next).
            XoraCoreEntry("n64", "mupen64plus_next_gles3", "Mupen64Plus-Next GLES3", "GPLv2"),
            XoraCoreEntry("n64", "mupen64plus_next_gles2", "Mupen64Plus-Next GLES2", "GPLv2"),
            XoraCoreEntry("n64", "parallel_n64", "ParaLLEl N64", "GPLv2"),
            XoraCoreEntry("gb", "gambatte", "Gambatte", "GPLv2"),
            XoraCoreEntry("gbc", "gambatte", "Gambatte", "GPLv2"),
            XoraCoreEntry("gba", "mgba", "mGBA", "MPL-2.0"),
            XoraCoreEntry("gba", "gpsp", "gpSP", "GPLv2"),
            XoraCoreEntry("nds", "melonds", "melonDS", "GPLv3"),
            XoraCoreEntry("3ds", "azahar", "Azahar", "GPLv2"),
            XoraCoreEntry("3ds", "citra", "Citra", "GPLv2"),
            XoraCoreEntry("genesis", "genesis_plus_gx", "Genesis Plus GX", "Non-commercial"),
            XoraCoreEntry("mastersystem", "genesis_plus_gx", "Genesis Plus GX", "Non-commercial"),
            XoraCoreEntry("gamegear", "genesis_plus_gx", "Genesis Plus GX", "Non-commercial"),
            XoraCoreEntry("sega32x", "picodrive", "PicoDrive", "MAME / GPLv2"),
            XoraCoreEntry("segacd", "genesis_plus_gx", "Genesis Plus GX", "Non-commercial"),
            XoraCoreEntry("saturn", "mednafen_saturn", "Beetle Saturn", "GPLv2"),
            XoraCoreEntry("dreamcast", "flycast", "Flycast", "GPLv2"),
            XoraCoreEntry("ps1", "pcsx_rearmed", "PCSX-ReARMed", "GPLv2"),
            XoraCoreEntry("psp", "ppsspp", "PPSSPP", "GPLv2+"),
            XoraCoreEntry("arcade", "fbneo", "FinalBurn Neo", "Non-commercial"),
            XoraCoreEntry("neogeo", "fbneo", "FinalBurn Neo", "Non-commercial"),
            XoraCoreEntry("atari2600", "stella", "Stella", "GPLv2"),
            XoraCoreEntry("atarilynx", "handy", "Handy", "zlib"),
            XoraCoreEntry("pcengine", "mednafen_pce_fast", "Beetle PCE Fast", "GPLv2"),
            XoraCoreEntry("wonderswan", "mednafen_wswan", "Beetle WonderSwan", "GPLv2"),
            XoraCoreEntry("ngp", "mednafen_ngp", "Beetle NeoPop", "GPLv2"),
            XoraCoreEntry("msx", "bluemsx", "blueMSX", "GPLv2"),
            XoraCoreEntry("c64", "vice_x64", "VICE x64", "GPLv2"),
            XoraCoreEntry("amiga", "puae", "PUAE", "GPLv2"),
            XoraCoreEntry("3do", "opera", "Opera", "GPLv3"),
            XoraCoreEntry("dos", "dosbox_pure", "DOSBox Pure", "GPLv2"),
        )
    }
}
