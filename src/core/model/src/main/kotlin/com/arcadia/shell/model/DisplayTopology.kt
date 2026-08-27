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

    /** True only when the shell can actually drive two panes on two separate physical screens. */
    val isDualScreen: Boolean get() = primary != null && secondary != null

    companion object {
        val Empty = DisplayTopology(
            displays = emptyList(),
            supportsActivitiesOnSecondaryDisplays = false,
        )
    }
}
