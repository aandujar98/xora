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
    /**
     * LiveArea peel drag, by how fast the dog-ear is moving. Each band drives a looping haptic
     * whose pulse rate tracks the drag; [PeelSlow] is silent but still buzzes, since the paper
     * only starts to rasp ([PeelMid] / [PeelFast], `peel_*.wav`) once it is moving.
     */
    PeelSlow,
    PeelMid,
    PeelFast,
    /** Stop the peel loop and its haptic when the finger lifts. */
    PeelStop,
    /**
     * Cursor tick without a directional [NavAction] — used when Home swallows Up/Down on the
     * Vita tray so a page turn can play [VitaPageNavigate] instead of the generic click.
     */
    Cursor,
    /** Vita shortcut tray page turn (`vita_page_navigate.wav`, GitHub tag `vita-page-navigate`). */
    VitaPageNavigate,
}

fun interface UiOneShotPlayer {
    fun play(shot: UiOneShot)
}
