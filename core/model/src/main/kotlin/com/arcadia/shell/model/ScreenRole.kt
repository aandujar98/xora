package com.arcadia.shell.model

/**
 * Which pane of the shell a physical display is currently showing. On a dual-screen handheld both
 * roles are live at once; in single-screen mode both panes share one display.
 */
enum class ScreenRole {
    /** The scrolling library, dock, and status bar. This is the pane that owns input focus. */
    Grid,

    /** Large artwork and metadata for whatever the grid currently has selected. */
    Hero,
}

fun ScreenRole.swapped(): ScreenRole = when (this) {
    ScreenRole.Grid -> ScreenRole.Hero
    ScreenRole.Hero -> ScreenRole.Grid
}
