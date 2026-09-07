package com.arcadia.shell.launcher

import android.content.Intent
import com.arcadia.shell.libretro.XoraLibretroPlayers
import com.arcadia.shell.model.Player

/**
 * Which bundled launch recipes belong in the player table after a live install scan.
 *
 * XOrA Libretro recipes stay (they are this app). RetroArch cores stay only when RetroArch
 * itself is installed. Standalone apps stay only when PackageManager can see them.
 */
object DetectedExternalPlayers {

    fun recipesToStore(
        builtIns: List<Player>,
        isInstalled: (Player) -> Boolean,
        retroArchInstalled: Boolean,
    ): List<Player> = builtIns.filter { player ->
        when {
            XoraLibretroPlayers.isXoraPlayer(player) -> true
            RetroArchPackages.isRetroArchPlayer(player) -> retroArchInstalled
            else -> isInstalled(player)
        }
    }

    /**
     * Candidates shown in Settings / per-game override for one platform: XOrA cores always,
     * everything else only when the matching app is installed.
     */
    fun visibleForPlatform(
        platformId: String,
        players: List<Player>,
        isInstalled: (Player) -> Boolean,
    ): List<Player> = players.filter { platformId in it.platformIds }.filter { player ->
        XoraLibretroPlayers.isXoraPlayer(player) || isInstalled(player)
    }
}

/** Timing for automatic emulator detection while the shell is running. */
object EmulatorDetectPolicy {
    const val PACKAGE_DEBOUNCE_MS = 750L
    const val FOREGROUND_MIN_INTERVAL_MS = 5_000L
}

/** Which package-change broadcasts should trigger a rescan. */
object EmulatorInstallEvents {

    fun shouldRefresh(action: String?, replacing: Boolean): Boolean = when (action) {
        Intent.ACTION_PACKAGE_REMOVED -> !replacing
        Intent.ACTION_PACKAGE_ADDED,
        Intent.ACTION_PACKAGE_REPLACED,
        -> true
        else -> false
    }
}
