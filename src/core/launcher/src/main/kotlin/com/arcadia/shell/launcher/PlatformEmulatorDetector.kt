package com.arcadia.shell.launcher

import com.arcadia.shell.database.repository.PlayerRepository
import com.arcadia.shell.datastore.PlatformEmulatorChoice
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.libretro.CoreStore
import com.arcadia.shell.libretro.XoraCoreCatalog
import com.arcadia.shell.libretro.XoraLibretroPlayers
import com.arcadia.shell.model.PlatformCatalog
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class DetectedEmulatorKind {
    Standalone,
    RetroArchCore,
    XoraCore,
}

/**
 * One installed (or catalog-known) launch option for a platform's Choose Emulator list.
 */
data class DetectedEmulator(
    val playerId: String,
    val displayName: String,
    val subtitle: String,
    val packageName: String?,
    val coreName: String?,
    val kind: DetectedEmulatorKind,
    /** True when the package (and for cores, preferably the `.so`) is present. */
    val available: Boolean,
)

@Singleton
class PlatformEmulatorDetector @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val probe: InstalledPlayerProbe,
    private val coreScanner: RetroArchCoreScanner,
    private val preferences: ShellPreferences,
    private val coreStore: CoreStore,
    private val xoraCatalog: XoraCoreCatalog,
) {
    /**
     * Emulators / RetroArch cores the user can pick for [platformId].
     * Only entries that are actually available are returned (installed package,
     * and for RetroArch either a core file on shared storage or RetroArch itself
     * when the private cores dir is unreadable).
     */
    suspend fun detectForPlatform(platformId: String): List<DetectedEmulator> {
        val players = playerRepository.getPlayers()
            .filter { platformId in it.platformIds }
            .ifEmpty {
                BuiltInPlayers.all.filter { platformId in it.platformIds }
            }

        val retroArchPkg = RetroArchPackages.findInstalledPackage(probe)
        val coresOnDisk = if (retroArchPkg != null) {
            coreScanner.installedCoreNames(retroArchPkg)
        } else {
            emptySet()
        }

        val results = mutableListOf<DetectedEmulator>()
        val seen = mutableSetOf<String>()

        // XOrA in-process Libretro cores first when installed (or listed for download).
        xoraCatalog.forPlatform(platformId).forEach { entry ->
            val playerId = XoraLibretroPlayers.playerId(platformId, entry.core)
            if (!seen.add(playerId)) return@forEach
            val onDisk = coreStore.isInstalled(entry.core)
            results += DetectedEmulator(
                playerId = playerId,
                displayName = "XOrA Emulator · ${entry.label}",
                subtitle = buildString {
                    append(entry.core)
                    append(" · ")
                    append(entry.license.ifBlank { "Libretro" })
                    if (onDisk) append(" · installed")
                    else append(" · downloads on launch")
                },
                packageName = XoraLibretroPlayers.PACKAGE,
                coreName = entry.core,
                kind = DetectedEmulatorKind.XoraCore,
                available = true,
            )
        }

        // Standalone next (Mupen FZ, PPSSPP, …).
        players
            .filterNot {
                RetroArchPackages.isRetroArchPlayer(it) || XoraLibretroPlayers.isXoraPlayer(it)
            }
            .forEach { player ->
                if (!probe.isInstalled(player)) return@forEach
                if (!seen.add(player.uniqueId)) return@forEach
                results += DetectedEmulator(
                    playerId = player.uniqueId,
                    displayName = player.name,
                    subtitle = player.packageName ?: "Installed app",
                    packageName = player.packageName,
                    coreName = null,
                    kind = DetectedEmulatorKind.Standalone,
                    available = true,
                )
            }

        if (retroArchPkg != null) {
            // Cores actually present on disk come first, including variants and cores with no
            // catalog entry (`mupen64plus_next_gles3`, `beetle_psx_hw`, …). Classifying by name
            // means a newly downloaded core shows up without a SORA release.
            val installedForPlatform = coresOnDisk
                .filter { RetroArchCoreCatalog.platformForCore(it) == platformId }
                .sorted()

            installedForPlatform.forEach { core ->
                val playerId = RetroArchCoreCatalog.playerIdForDiscovered(platformId, core)
                if (!seen.add(playerId)) return@forEach
                results += retroArchEmulator(
                    playerId = playerId,
                    core = core,
                    retroArchPkg = retroArchPkg,
                    onDisk = true,
                )
            }

            RetroArchCoreCatalog.forPlatform(platformId).forEach { option ->
                if (!seen.add(option.playerId)) return@forEach
                val onDisk = option.core in coresOnDisk ||
                    coreScanner.hasCore(option.core, retroArchPkg)
                // A readable cores dir that simply lacks this core means it is genuinely not
                // installed, so it is only listed when nothing was readable at all. Private
                // `/data/data/<pkg>/cores` is unreadable on most devices, and there the whole
                // catalog is offered so the user can still pick a core by hand.
                if (!onDisk && coresOnDisk.isNotEmpty()) {
                    seen.remove(option.playerId)
                    return@forEach
                }
                results += retroArchEmulator(
                    playerId = option.playerId,
                    core = option.core,
                    retroArchPkg = retroArchPkg,
                    onDisk = onDisk,
                )
            }
        }

        return results
    }

    private fun retroArchEmulator(
        playerId: String,
        core: String,
        retroArchPkg: String,
        onDisk: Boolean,
    ): DetectedEmulator = DetectedEmulator(
        playerId = playerId,
        displayName = "RetroArch · ${RetroArchCoreCatalog.labelForCore(core)}",
        subtitle = buildString {
            append(retroArchPkg)
            append(" · ")
            append(core)
            if (onDisk) append(" · found on storage")
            else append(" · install via Online Updater if launch fails")
        },
        packageName = retroArchPkg,
        coreName = core,
        kind = DetectedEmulatorKind.RetroArchCore,
        available = true,
    )

    suspend fun selectedChoice(platformId: String): PlatformEmulatorChoice? =
        preferences.platformEmulatorChoice(platformId)

    suspend fun selectedPlayerId(platformId: String): String? {
        preferences.platformEmulatorChoice(platformId)?.playerId?.let { return it }
        // Legacy N64 Mupen toggle → treat as RetroArch Mupen64Plus-Next.
        if (platformId == "n64" && preferences.settings.first().n64UseMupen64PlusNext) {
            return BuiltInPlayers.RETROARCH_N64_PLAYER_ID
        }
        return playerRepository.settingsFor(platformId)?.selectedPlayerId
    }

    fun emptyMessage(platformId: String): String {
        val name = PlatformCatalog.byId(platformId)?.shortName
            ?: PlatformCatalog.byId(platformId)?.displayName
            ?: platformId.uppercase()
        return "No $name emulators detected"
    }
}
