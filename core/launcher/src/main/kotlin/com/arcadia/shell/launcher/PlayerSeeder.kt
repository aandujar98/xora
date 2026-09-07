package com.arcadia.shell.launcher

import com.arcadia.shell.database.repository.PlayerRepository
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.libretro.CoreStore
import com.arcadia.shell.libretro.XoraCoreCatalog
import com.arcadia.shell.libretro.XoraLibretroPlayers
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a live emulator scan after bundled recipes are rewritten to match the device. */
data class EmulatorScanResult(
    /** Bundled launch recipes written into the player table after install filtering. */
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
    private val preferences: ShellPreferences,
) {
    private fun allBuiltIns() =
        BuiltInPlayers.all + XoraLibretroPlayers.allPlayers(xoraCatalog)

    /**
     * Cold-start detection: once per catalog epoch, wipe stale Choose Emulator picks, then
     * rewrite the player table from what PackageManager can actually see.
     */
    suspend fun seedIfNeeded() {
        preferences.consumeEmulatorDetectionReset()
        scanInstalled()
    }

    /**
     * Rebuilds bundled recipes from the apps (and RetroArch cores) installed on this device.
     *
     * Called on startup, when the shell returns to the foreground, when a package is
     * installed or removed, and from the optional Refresh now control.
     */
    suspend fun scanInstalled(): EmulatorScanResult {
        val builtIns = allBuiltIns()
        coreStore.refreshInstalled()

        val standalone = builtIns.filterNot {
            RetroArchPackages.isRetroArchPlayer(it) || XoraLibretroPlayers.isXoraPlayer(it)
        }
        val installedStandalone = probe.installedPlayers(standalone)
        val retroArchPkg = RetroArchPackages.findInstalledPackage(probe)
        val retroArchInstalled = retroArchPkg != null
        val stored = DetectedExternalPlayers.recipesToStore(
            builtIns = builtIns,
            isInstalled = probe::isInstalled,
            retroArchInstalled = retroArchInstalled,
        )
        playerRepository.replaceBuiltIns(stored)

        val cores = if (retroArchPkg != null) {
            coreScanner.installedCoreNames(retroArchPkg)
        } else {
            emptySet()
        }

        return EmulatorScanResult(
            seededCount = stored.size,
            installedStandalone = installedStandalone.size,
            installedCores = cores.size,
            installedXoraCores = coreStore.installedCoreNames.value.size,
            retroArchInstalled = retroArchInstalled,
        )
    }
}
