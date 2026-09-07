package com.arcadia.shell.launcher

import com.arcadia.shell.model.Player

/**
 * Known AetherSX2 / NetherSX2 applicationIds and helpers to bind the seeded
 * `nethersx2.ps2` / `aethersx2.ps2` recipes to whichever package is installed.
 *
 * Mainline NetherSX2 patches keep [PACKAGE_DEFAULT]. Turnip Classic and some
 * community builds ship as [PACKAGE_CTURNIP] / [PACKAGE_TTURNIP] with the
 * original `xyz.aethersx2.android.EmulationActivity` class name.
 */
object Ps2Packages {

    const val PACKAGE_DEFAULT = "xyz.aethersx2.android"
    const val PACKAGE_CTURNIP = "xyz.aethersx2.cturnip"
    const val PACKAGE_TTURNIP = "xyz.aethersx2.tturnip"
    const val ACTIVITY_RELATIVE = ".EmulationActivity"
    const val ACTIVITY_LEGACY = "xyz.aethersx2.android.EmulationActivity"

    data class Candidate(
        val packageName: String,
        val activity: String,
    )

    val CANDIDATES: List<Candidate> = listOf(
        Candidate(PACKAGE_DEFAULT, ACTIVITY_RELATIVE),
        Candidate(PACKAGE_CTURNIP, ACTIVITY_LEGACY),
        Candidate(PACKAGE_TTURNIP, ACTIVITY_LEGACY),
    )

    val CANDIDATE_PACKAGES: List<String> = CANDIDATES.map { it.packageName }

    private val PLAYER_IDS = setOf("nethersx2.ps2", "aethersx2.ps2")

    fun isPs2Player(player: Player): Boolean {
        if (player.uniqueId in PLAYER_IDS) return true
        val pkg = player.packageName ?: return false
        return "ps2" in player.platformIds && pkg in CANDIDATE_PACKAGES
    }

    fun findInstalledPackage(probe: InstalledPlayerProbe): String? {
        CANDIDATE_PACKAGES.firstOrNull { probe.isInstalled(it) }?.let { return it }
        return probe.findInstalledPackagePrefixed("xyz.aethersx2")
    }

    fun withPackage(player: Player, packageName: String): Player {
        val candidate = CANDIDATES.firstOrNull { it.packageName == packageName }
            ?: Candidate(packageName, ACTIVITY_LEGACY)
        val component = "${candidate.packageName}/${candidate.activity}"
        val rewritten = player.amStartArguments.replace(
            Regex("""-n\s+\S+"""),
            "-n $component",
        )
        return if (rewritten == player.amStartArguments) player
        else player.copy(amStartArguments = rewritten)
    }
}
