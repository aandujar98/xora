package com.arcadia.shell.input

/** One-shot UI SFX that a feature module can fire without depending on the app audio layer. */
enum class UiOneShot {
    /** LT friends / social window opening (`nav_friend.wav`). */
    FriendsTab,
    /** RT profile window opening. */
    ProfileTab,
    /** LT or RT window closing (not a nested back inside the window). */
    NavClose,
    /** Confirm / tap on a Vita shortcut bubble (`bubble_launch.wav`). */
    BubbleLaunch,
    /** Vita shortcut peel finished — boot the pinned title (`boot_vita.wav`). */
    BootVita,
    /** Confirm a ROM from the XMB (`boot_3.wav`). */
    BootXmb,
    /** LiveArea peel drag — slow / mid / fast loops (`peel_*.wav`). */
    PeelSlow,
    PeelMid,
    PeelFast,
    /** Stop the looping peel sample when the finger lifts. */
    PeelStop,
}

fun interface UiOneShotPlayer {
    fun play(shot: UiOneShot)
}
