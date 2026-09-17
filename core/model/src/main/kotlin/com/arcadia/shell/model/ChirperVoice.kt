package com.arcadia.shell.model

/**
 * A chirper: the little voice a profile speaks with. Picked in the profile editor next to the
 * display name, and played whenever this player's status changes — a custom message, going
 * in-game, a message arriving — both here and on the friends who see that change.
 *
 * Each one has several takes so a run of updates does not sound like one sample on repeat.
 *
 * The icon is an animated GIF (`feature/home` `res/raw/chirper_<id>.gif`) that plays once per
 * press rather than looping, and the takes are `app` `res/raw/chirp_<id>_<n>.wav`. [accent] is the
 * icon's dominant colour, sampled from its own first frame, and drives the selected pill's
 * gradient and glow.
 */
enum class ChirperVoice(
    val id: String,
    val displayName: String,
    /** Opaque ARGB. Kotlin/JVM module, so no `androidx.compose.ui.graphics.Color` here. */
    val accent: Long,
    /** How many takes `chirp_<id>_<n>.wav` exist, numbered from 1. */
    val takes: Int,
) {
    // Order follows the picker in docs/design/reference/EditProfile-SelectaChirp.jpg for the five
    // rows that design shows; the rest keep the order of the feature request's own table.
    UsagiShade("usagishade", "UsagiShade", 0xFFEF6EA9, takes = 5),
    UsagiIshii("usagiishii", "UsagiIshii", 0xFFFFB1BE, takes = 1),
    IPrinceAngel("iprinceangel", "iPrinceAngel", 0xFF0D8EAE, takes = 4),
    Furogii("furogii", "Furogii", 0xFF34C971, takes = 3),
    Sora("sora", "Sora", 0xFFC53939, takes = 1),
    Tonic("tonic", "Tonic", 0xFF026FFF, takes = 4),
    Somarix("somarix", "Somarix", 0xFFF6B912, takes = 3),
    Makoto("makoto", "Makoto", 0xFF1BB5FF, takes = 3),
    Lyn("lyn", "L Y N", 0xFF6267AF, takes = 3),
    Marlix("marlix", "Marlix", 0xFF26538D, takes = 4);

    companion object {
        val Default = UsagiShade

        fun fromId(id: String?): ChirperVoice =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: Default
    }
}

/**
 * Plays a chirp. Implemented in the app audio layer; declared here so a feature module can make a
 * profile speak without depending on it, the same way `UiOneShotPlayer` works for menu SFX.
 */
fun interface ChirpPlayer {
    /**
     * Plays one take of [voice], chosen at random so repeated updates vary. Respects the UI SFX
     * volume and stays silent when that is muted.
     */
    fun chirp(voice: ChirperVoice)
}
