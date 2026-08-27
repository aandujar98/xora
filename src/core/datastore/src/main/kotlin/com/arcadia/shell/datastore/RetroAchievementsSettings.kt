package com.arcadia.shell.datastore

/**
 * RetroAchievements preferences shared by the XOrA launcher (XMB / Setup) and the
 * in-process XOrA Emulator session.
 */
data class RetroAchievementsSettings(
    /** Master switch — when false, the emulator skips RA and the launcher hides live unlocks. */
    val enabled: Boolean = true,
    /**
     * Hardcore mode (no save states / cheats). Softcore is the default so states stay available.
     */
    val hardcore: Boolean = false,
    /** Show shell banners when an achievement unlocks in-emulator or via sync. */
    val unlockNotifications: Boolean = true,
    /**
     * Surface RetroAchievements in the launcher (XMB shard, game progress cues, Start menu).
     * Sign-in still works when this is off; library browsing is de-emphasized.
     */
    val showInLauncher: Boolean = true,
    /** Prefer RetroAchievements rich-presence text in shell status when a game is linked. */
    val richPresence: Boolean = true,
)
