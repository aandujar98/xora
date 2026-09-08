package com.arcadia.shell.launcher

import android.content.Context
import android.content.pm.PackageManager
import com.arcadia.shell.model.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers whether the emulator behind a launch recipe is actually present.
 *
 * Results are cached for the life of a query batch only. Emulators get installed and uninstalled
 * while the shell is running, and a stale "not installed" would hide a working player.
 */
@Singleton
class InstalledPlayerProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val packageManager: PackageManager get() = context.packageManager

    fun isInstalled(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return runCatching {
            packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    fun isInstalled(player: Player): Boolean {
        if (RetroArchPackages.isRetroArchPlayer(player)) {
            return RetroArchPackages.findInstalledPackage(this) != null
        }
        if (Ps2Packages.isPlayPlayer(player)) {
            return Ps2Packages.findInstalledPlayPackage(this) != null
        }
        if (Ps2Packages.isPs2Player(player)) {
            return Ps2Packages.findInstalledPackage(this) != null
        }
        return isInstalled(player.packageName)
    }

    fun installedPlayers(players: List<Player>): List<Player> {
        val cache = mutableMapOf<String, Boolean>()
        return players.filter { player ->
            if (RetroArchPackages.isRetroArchPlayer(player)) {
                return@filter RetroArchPackages.findInstalledPackage(this) != null
            }
            if (Ps2Packages.isPlayPlayer(player)) {
                return@filter Ps2Packages.findInstalledPlayPackage(this) != null
            }
            if (Ps2Packages.isPs2Player(player)) {
                return@filter Ps2Packages.findInstalledPackage(this) != null
            }
            val packageName = player.packageName ?: return@filter false
            cache.getOrPut(packageName) { isInstalled(packageName) }
        }
    }

    /** Best-effort scan when QUERY_ALL_PACKAGES is honoured (sideload / custom builds). */
    fun findInstalledPackagePrefixed(prefix: String): String? {
        if (prefix.isBlank()) return null
        return runCatching {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
                .asSequence()
                .map { it.packageName }
                .firstOrNull { it.startsWith(prefix) }
        }.getOrNull()
    }

    fun appLabel(packageName: String): String? = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrNull()
}
