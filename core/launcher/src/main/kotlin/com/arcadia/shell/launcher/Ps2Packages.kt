package com.arcadia.shell.launcher

import com.arcadia.shell.model.Player

/**
 * Known AetherSX2 / NetherSX2 applicationIds and helpers to bind the seeded
 * `nethersx2.ps2` / `aethersx2.ps2` recipes to whichever sideload package is installed.
 *
 * Mainline NetherSX2 patches keep [PACKAGE_DEFAULT]. Turnip Classic and some
 * community builds ship as [PACKAGE_CTURNIP] / [PACKAGE_TTURNIP] with the
 * original `xyz.aethersx2.android.EmulationActivity` class name.
 *
 * Google Play listings use a different applicationId ([PACKAGE_PLAY], also
 * [PACKAGE_PLAY_NSX2]) and are bound by the `nethersx2.play` recipe so a
 * sideload install and a Play Store install can both appear in Choose Emulator.
 */
object Ps2Packages {

    const val PACKAGE_DEFAULT = "xyz.aethersx2.android"
    const val PACKAGE_CTURNIP = "xyz.aethersx2.cturnip"
    const val PACKAGE_TTURNIP = "xyz.aethersx2.tturnip"
    /** Play Store listing titled "NetherSX2 Emulator". */
    const val PACKAGE_PLAY = "com.theemulatorapp.nethersx2"
    /** Play Store listing titled "NSX2 Emulator - Alpha". */
    const val PACKAGE_PLAY_NSX2 = "com.aethersx2.ps22"
    const val ACTIVITY_RELATIVE = ".EmulationActivity"
    const val ACTIVITY_LEGACY = "xyz.aethersx2.android.EmulationActivity"

    const val PLAYER_PLAY_ID = "nethersx2.play"

    data class Candidate(
        val packageName: String,
        val activity: String,
    )

    val CANDIDATES: List<Candidate> = listOf(
        Candidate(PACKAGE_DEFAULT, ACTIVITY_RELATIVE),
        Candidate(PACKAGE_CTURNIP, ACTIVITY_LEGACY),
        Candidate(PACKAGE_TTURNIP, ACTIVITY_LEGACY),
    )

    val PLAY_CANDIDATES: List<Candidate> = listOf(
        Candidate(PACKAGE_PLAY, ACTIVITY_LEGACY),
        Candidate(PACKAGE_PLAY_NSX2, ACTIVITY_LEGACY),
    )

    val CANDIDATE_PACKAGES: List<String> = CANDIDATES.map { it.packageName }
    val PLAY_PACKAGES: List<String> = PLAY_CANDIDATES.map { it.packageName }

    private val PLAYER_IDS = setOf("nethersx2.ps2", "aethersx2.ps2")
    private val PLAY_PLAYER_IDS = setOf(PLAYER_PLAY_ID)

    fun isPs2Player(player: Player): Boolean {
        if (isPlayPlayer(player)) return false
        if (player.uniqueId in PLAYER_IDS) return true
        val pkg = player.packageName ?: return false
        return "ps2" in player.platformIds && pkg in CANDIDATE_PACKAGES
    }

    fun isPlayPlayer(player: Player): Boolean {
        if (player.uniqueId in PLAY_PLAYER_IDS) return true
        val pkg = player.packageName ?: return false
        return "ps2" in player.platformIds && pkg in PLAY_PACKAGES
    }

    fun findInstalledPackage(probe: InstalledPlayerProbe): String? {
        CANDIDATE_PACKAGES.firstOrNull { probe.isInstalled(it) }?.let { return it }
        return probe.findInstalledPackagePrefixed("xyz.aethersx2")
    }

    fun findInstalledPlayPackage(probe: InstalledPlayerProbe): String? =
        PLAY_PACKAGES.firstOrNull { probe.isInstalled(it) }

    fun withPackage(player: Player, packageName: String): Player {
        val candidate = (CANDIDATES + PLAY_CANDIDATES).firstOrNull { it.packageName == packageName }
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
