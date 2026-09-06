package com.arcadia.shell.launcher

import com.arcadia.shell.datastore.AndroidAppInclusionMode
import com.arcadia.shell.datastore.ShellSettings

/**
 * Which launchable packages should be mirrored into the Android platform.
 *
 * Sync-off always yields an empty set so [InstalledAppSync] can prune leftover rows.
 */
fun ShellSettings.includedAndroidApps(apps: List<InstalledApp>): List<InstalledApp> {
    if (!androidAppSyncEnabled) return emptyList()
    return when (androidAppInclusionMode) {
        AndroidAppInclusionMode.All -> apps
        AndroidAppInclusionMode.Allowlist ->
            apps.filter { it.packageName in androidAppAllowlist }
    }
}

/**
 * Persistable inclusion after the user ticks a subset.
 *
 * Selecting every known package stores [AndroidAppInclusionMode.All] so later installs still
 * appear. Selecting none stores an empty allowlist (the Android platform stays hidden).
 */
fun resolveAndroidAppInclusion(
    allPackages: Set<String>,
    selected: Set<String>,
): Pair<AndroidAppInclusionMode, Set<String>> {
    val cleaned = selected.filter { it in allPackages }.toSet()
    return if (allPackages.isNotEmpty() && cleaned.size == allPackages.size) {
        AndroidAppInclusionMode.All to emptySet()
    } else {
        AndroidAppInclusionMode.Allowlist to cleaned
    }
}

/** Toggle one package against the current mode / allowlist, then [resolveAndroidAppInclusion]. */
fun toggleAndroidAppInclusion(
    mode: AndroidAppInclusionMode,
    allowlist: Set<String>,
    allPackages: Set<String>,
    packageName: String,
    selected: Boolean,
): Pair<AndroidAppInclusionMode, Set<String>> {
    val current = when (mode) {
        AndroidAppInclusionMode.All -> allPackages
        AndroidAppInclusionMode.Allowlist -> allowlist
    }
    val next = if (selected) current + packageName else current - packageName
    return resolveAndroidAppInclusion(allPackages, next)
}

fun selectedAndroidPackages(
    mode: AndroidAppInclusionMode,
    allowlist: Set<String>,
    allPackages: Set<String>,
): Set<String> = when (mode) {
    AndroidAppInclusionMode.All -> allPackages
    AndroidAppInclusionMode.Allowlist -> allowlist
}
