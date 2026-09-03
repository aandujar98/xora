package com.arcadia.shell.launcher

import com.arcadia.shell.model.Player

/**
 * Known RetroArch applicationIds on Android and helpers to bind a seeded
 * `retroarch.*` launch recipe to whichever package is actually installed.
 *
 * Play Store / 64-bit builds use [PACKAGE_AARCH64]; older or 32-bit APKs often
 * ship as [PACKAGE_DEFAULT]. Core and config paths are under the chosen package.
 */
object RetroArchPackages {

    const val PACKAGE_AARCH64 = "com.retroarch.aarch64"
    const val PACKAGE_DEFAULT = "com.retroarch"
    const val ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"

    /** Preference order when probing for an installed RetroArch. */
    val CANDIDATE_PACKAGES: List<String> = listOf(
        PACKAGE_AARCH64,
        PACKAGE_DEFAULT,
    )

    const val N64_PLAYER_ID = "retroarch.n64"
    const val MUPEN64PLUS_NEXT_CORE = "mupen64plus_next"

    fun isRetroArchPlayer(player: Player): Boolean {
        if (player.uniqueId.startsWith("retroarch.")) return true
        val pkg = player.packageName ?: return false
        return pkg in CANDIDATE_PACKAGES || pkg.startsWith("com.retroarch")
    }

    fun findInstalledPackage(probe: InstalledPlayerProbe): String? =
        CANDIDATE_PACKAGES.firstOrNull { probe.isInstalled(it) }

    /**
     * Rewrites `-n`, `LIBRETRO`, and `CONFIGFILE` paths from the seeded package
     * to [packageName]. No-op when already bound.
     */
    fun withPackage(player: Player, packageName: String): Player {
        val current = player.packageName ?: return player
        if (current == packageName) return player
        return player.copy(amStartArguments = player.amStartArguments.replace(current, packageName))
    }

    /** Absolute path RetroArch expects for a core `.so` inside its private data. */
    fun coreLibPath(packageName: String, core: String): String =
        "/data/data/$packageName/cores/${core}_libretro_android.so"

    fun configFilePath(packageName: String): String =
        "/storage/emulated/0/Android/data/$packageName/files/retroarch.cfg"

    fun launchTemplate(packageName: String, core: String): String =
        "-n $packageName/$ACTIVITY " +
            "-e ROM {file.path} " +
            "-e LIBRETRO ${coreLibPath(packageName, core)} " +
            "-e CONFIGFILE ${configFilePath(packageName)}"

    fun missingInstallMessage(coreHint: String? = null): String {
        val core = coreHint?.takeIf { it.isNotBlank() } ?: "the required"
        return "RetroArch is not installed. Install RetroArch " +
            "(${CANDIDATE_PACKAGES.joinToString(" or ")}) and download $core " +
            "core (Online Updater → Core Downloader)."
    }

    /** Core base name from a seeded player's LIBRETRO path, if present. */
    fun coreNameFromPlayer(player: Player): String? {
        val match = Regex("""/([^/]+)_libretro_android\.so""")
            .find(player.amStartArguments)
        return match?.groupValues?.getOrNull(1)
    }
}
