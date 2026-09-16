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
     * (AYN Thor bottom screen), then a public secondary (HDMI), then any other panel large
     * enough to be a real screen. Tiny private overlays stay ignored.
     */
    val presentationDisplay: ShellDisplay?
        get() = displays.firstOrNull { !it.isPrimary && it.isPresentation }
            ?: secondary
            ?: displays.firstOrNull { it.isExpandCandidate }

    /** True when two physical panels can show shell / emulator content. */
    val isDualScreen: Boolean get() = primary != null && presentationDisplay != null

    companion object {
        /** Smaller than this on either edge is treated as a system overlay, not a game panel. */
        const val MIN_EXPAND_EDGE_PX = 240

        val Empty = DisplayTopology(
            displays = emptyList(),
            supportsActivitiesOnSecondaryDisplays = false,
        )
    }
}

/** True for a non-primary panel that can show the DS / 3DS bottom LCD. */
val ShellDisplay.isExpandCandidate: Boolean
    get() {
        if (isPrimary) return false
        if (widthPx < DisplayTopology.MIN_EXPAND_EDGE_PX ||
            heightPx < DisplayTopology.MIN_EXPAND_EDGE_PX
        ) {
            return false
        }
        return true
    }
