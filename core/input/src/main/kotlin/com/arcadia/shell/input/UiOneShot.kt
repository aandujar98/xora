package com.arcadia.shell.input

/** One-shot UI SFX that a feature module can fire without depending on the app audio layer. */
enum class UiOneShot {
    /** Confirm / tap on a Vita shortcut bubble (`bubble_launch.wav`). */
    BubbleLaunch,
}

fun interface UiOneShotPlayer {
    fun play(shot: UiOneShot)
}
