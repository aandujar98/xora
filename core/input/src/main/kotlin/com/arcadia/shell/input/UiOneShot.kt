package com.arcadia.shell.input

/** One-shot UI SFX that a feature module can fire without depending on the app audio layer. */
enum class UiOneShot {
    /** LT friends / social window opening. */
    FriendsTab,
    /** RT profile window opening. */
    ProfileTab,
    /** LT or RT window closing (not a nested back inside the window). */
    NavClose,
}

fun interface UiOneShotPlayer {
    fun play(shot: UiOneShot)
}
