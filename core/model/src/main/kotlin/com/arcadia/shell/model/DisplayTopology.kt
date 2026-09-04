package com.arcadia.shell.model

data class ShellDisplay(
    val displayId: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val isPrimary: Boolean,
    /**
     * False for private and overlay displays. Launching another app's activity onto a display that
     * is not public throws [SecurityException], so this gates every targeted launch.
     */
    val isPublic: Boolean,
    /**
     * True when the panel is a Presentation display (`Display.FLAG_PRESENTATION`). AYN Thor /
     * similar clamshells expose the bottom screen this way — often `FLAG_PRIVATE`, so it is not
     * [isPublic] and cannot host a launched Activity, but it *can* host a [android.app.Presentation].
     */
    val isPresentation: Boolean = false,
) {
    val aspectRatio: Float get() = if (heightPx == 0) 0f else widthPx.toFloat() / heightPx
}

data class DisplayTopology(
    val displays: List<ShellDisplay>,
    /**
     * Mirrors `PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS`. When absent,
     * `ActivityOptions.setLaunchDisplayId` is silently ignored by the platform, so a game
     * targeted at the second screen would open on the primary one instead.
     */
    val supportsActivitiesOnSecondaryDisplays: Boolean,
) {
    val primary: ShellDisplay? get() = displays.firstOrNull { it.isPrimary }

    val secondary: ShellDisplay? get() = displays.firstOrNull { !it.isPrimary && it.isPublic }

    /**
     * Second panel that can host a Presentation or overlay. Prefers a FLAG_PRESENTATION display
     * (AYN Thor bottom screen) and falls back to a public secondary.
     */
    val presentationDisplay: ShellDisplay?
        get() = displays.firstOrNull { !it.isPrimary && it.isPresentation }
            ?: secondary

    /** True when two physical panels can show shell / emulator content. */
    val isDualScreen: Boolean get() = primary != null && presentationDisplay != null

    companion object {
        val Empty = DisplayTopology(
            displays = emptyList(),
            supportsActivitiesOnSecondaryDisplays = false,
        )
    }
}
