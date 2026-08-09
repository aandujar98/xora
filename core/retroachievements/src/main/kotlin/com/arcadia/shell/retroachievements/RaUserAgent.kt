package com.arcadia.shell.retroachievements

import android.content.Context
import android.os.Build

/**
 * Builds the RetroAchievements Connect / Web API User-Agent.
 *
 * Format (RA hardcore-compliance docs):
 * `EmulatorName/version (OS version) rcheevos/version [core_libretro]`
 *
 * The client moniker must stay **XOrA** — spoofing RetroArch / RALibRetro is an auto-fail for
 * hardcore approval and can untrack players. Hardcore unlocks stay softcore on the server until
 * RAdmin adds `XOrA` to `emulator_user_agents`.
 */
object RaUserAgent {
    const val CLIENT_NAME = "XOrA"

    fun forApp(context: Context, coreName: String? = null): String {
        val version = appVersion(context)
        val release = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() }
            ?: Build.VERSION.SDK_INT.toString()
        val rcheevos = RetroAchievementsClient.RCHEEVOS_CLIENT_VERSION
        val base = "$CLIENT_NAME/$version (Android $release) rcheevos/$rcheevos"
        val coreClause = coreClause(coreName) ?: return base
        return "$base $coreClause"
    }

    fun appVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "0.0.0"

    /**
     * Normalizes a core file / recipe name into the `_libretro` suffix RA expects for
     * multi-core frontends (e.g. `mupen64plus_next` → `mupen64plus_next_libretro`).
     */
    fun coreClause(coreName: String?): String? {
        val raw = coreName?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val stem = raw
            .substringAfterLast('/')
            .removeSuffix(".so")
            .removeSuffix(".dll")
            .removeSuffix(".dylib")
        if (stem.isBlank()) return null
        val withSuffix = when {
            stem.endsWith("_libretro", ignoreCase = true) -> stem
            stem.endsWith("_libretro_android", ignoreCase = true) -> stem
            else -> "${stem}_libretro"
        }
        return withSuffix
    }
}
