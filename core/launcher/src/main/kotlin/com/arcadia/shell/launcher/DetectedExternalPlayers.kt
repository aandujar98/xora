package com.arcadia.shell.launcher

import android.content.Intent
import com.arcadia.shell.libretro.XoraLibretroPlayers
import com.arcadia.shell.model.PlatformCatalog
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

    /**
     * One row per installed external app for Setup → Emulators. XOrA Libretro is omitted
     * (it has its own card). RetroArch cores collapse to a single RetroArch row.
     */
    fun appsFromPlayers(
        players: List<Player>,
        appLabel: (packageName: String) -> String? = { null },
    ): List<DetectedEmulatorApp> {
        val grouped = linkedMapOf<String, MutableList<Player>>()
        players.forEach { player ->
            val key = groupingKey(player) ?: return@forEach
            grouped.getOrPut(key) { mutableListOf() }.add(player)
        }
        return grouped.map { (packageName, group) ->
            val platformIds = group.flatMap { it.platformIds }.distinct()
            DetectedEmulatorApp(
                packageName = packageName,
                displayName = displayName(packageName, group, appLabel),
                platformLabels = platformIds.map { id ->
                    PlatformCatalog.byId(id)?.shortName ?: id.uppercase()
                }.distinct().sorted(),
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Platforms served by installed standalone apps, so Setup still lists Skyline on Switch
     * even when the library has no Switch games yet. RetroArch is excluded here to avoid
     * creating a card for every core.
     */
    fun extraPlatformIds(players: List<Player>): Set<String> =
        players.filterNot {
            XoraLibretroPlayers.isXoraPlayer(it) || RetroArchPackages.isRetroArchPlayer(it)
        }.flatMap { it.platformIds }.toSet()

    private fun groupingKey(player: Player): String? {
        if (XoraLibretroPlayers.isXoraPlayer(player)) return null
        if (RetroArchPackages.isRetroArchPlayer(player)) {
            return player.packageName ?: RetroArchPackages.PACKAGE_AARCH64
        }
        return player.packageName?.takeIf { it.isNotBlank() }
    }

    private fun displayName(
        packageName: String,
        group: List<Player>,
        appLabel: (String) -> String?,
    ): String {
        appLabel(packageName)?.takeIf { it.isNotBlank() }?.let { return it }
        if (group.any { RetroArchPackages.isRetroArchPlayer(it) }) return "RetroArch"
        val stripped = group.map { it.name.substringBefore(" (").trim() }.distinct()
        return stripped.singleOrNull()
            ?: group.minByOrNull { it.name.length }?.name
            ?: packageName
    }
}

/** An external emulator app currently installed on the device. */
data class DetectedEmulatorApp(
    val packageName: String,
    val displayName: String,
    val platformLabels: List<String>,
)

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
