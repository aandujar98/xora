package com.arcadia.shell.launcher

import com.arcadia.shell.database.repository.PlayerRepository
import com.arcadia.shell.libretro.CoreStore
import com.arcadia.shell.libretro.XoraCoreCatalog
import com.arcadia.shell.libretro.XoraLibretroPlayers
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a user-triggered emulator scan after bundled recipes are re-synced. */
data class EmulatorScanResult(
    /** Bundled launch recipes written (or refreshed) into the player table. */
    val seededCount: Int,
    /** Standalone emulator apps that resolve as installed. */
    val installedStandalone: Int,
    /** RetroArch cores found on shared storage, when RetroArch itself is installed. */
    val installedCores: Int,
    /** XOrA Libretro cores present under filesDir/cores. */
    val installedXoraCores: Int,
    val retroArchInstalled: Boolean,
) {
    val installedTotal: Int get() = installedStandalone + installedCores + installedXoraCores
}

@Singleton
class PlayerSeeder @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val probe: InstalledPlayerProbe,
    private val coreScanner: RetroArchCoreScanner,
    private val xoraCatalog: XoraCoreCatalog,
    private val coreStore: CoreStore,
) {
    private fun allBuiltIns() =
        BuiltInPlayers.all + XoraLibretroPlayers.allPlayers(xoraCatalog)

    /** Upserts bundled players so newly added package ids (Azahar, Cemu, etc.) are detected after upgrade. */
    suspend fun seedIfNeeded() = playerRepository.syncBuiltIns(allBuiltIns())

    /**
     * Re-syncs every bundled launch recipe, then counts what is actually installed.
     *
     * Used by Settings / Start → Scan for emulators so a newly sideloaded app (Cemu, Eden, …)
     * shows up in Choose Emulator without restarting SORA.
     */
    suspend fun scanInstalled(): EmulatorScanResult {
        val builtIns = allBuiltIns()
        playerRepository.syncBuiltIns(builtIns)
        coreStore.refreshInstalled()

        val standalone = builtIns.filterNot {
            RetroArchPackages.isRetroArchPlayer(it) || XoraLibretroPlayers.isXoraPlayer(it)
        }
        val installedStandalone = probe.installedPlayers(standalone)

        val retroArchPkg = RetroArchPackages.findInstalledPackage(probe)
        val cores = if (retroArchPkg != null) {
            coreScanner.installedCoreNames(retroArchPkg)
        } else {
            emptySet()
        }

        return EmulatorScanResult(
            seededCount = builtIns.size,
            installedStandalone = installedStandalone.size,
            installedCores = cores.size,
            installedXoraCores = coreStore.installedCoreNames.value.size,
            retroArchInstalled = retroArchPkg != null,
        )
    }
}
