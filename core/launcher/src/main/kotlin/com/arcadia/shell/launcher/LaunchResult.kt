package com.arcadia.shell.launcher

import com.arcadia.shell.model.Player

sealed interface LaunchResult {

    data class Launched(
        /** Null when the launch was a native Android app rather than an emulator recipe. */
        val player: Player?,
        /** The display the game was actually sent to, which may not be the one requested. */
        val displayId: Int?,
        /** Set when a specific screen was asked for but the platform refused to honour it. */
        val displayFallbackReason: String? = null,
    ) : LaunchResult

    data class NoPlayerConfigured(val platformName: String) : LaunchResult

    data class PlayerNotInstalled(val player: Player, val packageName: String) : LaunchResult

    /** The template needs a capability this game does not have, typically a real file path. */
    data class UnsupportedSource(val player: Player, val reason: String) : LaunchResult

    data class InvalidTemplate(val player: Player, val reason: String) : LaunchResult

    data class Failed(val player: Player?, val reason: String) : LaunchResult
}
