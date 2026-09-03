package com.arcadia.shell.feature.home.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.feature.home.HomeHubSection
import com.arcadia.shell.feature.home.HomeHubUiState
import com.arcadia.shell.feature.home.HomePage
import com.arcadia.shell.feature.home.XoraXmbDepth

/**
 * Controller legend along the bottom edge. A controller-first interface has no visible affordances
 * for its buttons, so the mapping has to be stated somewhere.
 */
@Composable
fun ButtonHintBar(
    hints: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
    val exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast))
    AnimatedContent(
        targetState = hints,
        transitionSpec = { enter togetherWith exit },
        label = "buttonHints",
        modifier = modifier,
    ) { currentHints ->
        val glass = rememberGlassTokens(GlassTone.Surface)
        Row(
            modifier = Modifier
                .liquidGlass(
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    tone = GlassTone.Surface,
                    intensity = GlassIntensity.Subtle,
                )
                .padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            currentHints.forEach { (button, label) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = button,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.contentMuted,
                    )
                }
            }
        }
    }
}

fun hintsForPage(
    page: HomePage,
    displayMode: DisplayMode = DisplayMode.Dual,
    homeHub: HomeHubUiState? = null,
    xmbDepth: XoraXmbDepth? = null,
): List<Pair<String, String>> = when (page) {
    HomePage.Home -> when {
        homeHub?.vitaShortcutTrayOpen == true && homeHub.shortcutsEditMode ->
            VitaShortcutTrayEditHints
        homeHub?.vitaShortcutTrayOpen == true -> VitaShortcutTrayHints
        xmbDepth == XoraXmbDepth.Systems -> XoraSystemBrowseHints
        xmbDepth == XoraXmbDepth.Roms -> XoraRomBrowseHints
        xmbDepth == XoraXmbDepth.DspAccounts -> XoraDspBrowseHints
        xmbDepth == XoraXmbDepth.MusicAlbums ||
            xmbDepth == XoraXmbDepth.MusicTracks -> XoraMusicBrowseHints
        xmbDepth == XoraXmbDepth.NowPlaying -> XoraNowPlayingHints
        else -> XoraXmbHints
    }
    HomePage.GameSelector -> if (displayMode == DisplayMode.Single) {
        SingleGameSelectorHints
    } else {
        GameSelectorHints
    }
    HomePage.RssFeed -> RssFeedHints
    HomePage.RaLibrary -> RaLibraryHints
}

/** Classic XOrA XMB: LB/RB cycle categories; Start focuses Settings. */
val XoraXmbHints: List<Pair<String, String>> = listOf(
    "L/R" to "Category",
    "U/D" to "Item",
    "LB/RB" to "Category",
    "A" to "Select",
    "B" to "Back",
    "X" to "Achievements",
    "Y" to "Shortcuts",
    "LT" to "Social",
    "RT" to "Profile",
    "Select" to "ROM options",
    "Start" to "Settings",
    "Start+Select" to "Guide",
)

/** All Games → pick a console. */
val XoraSystemBrowseHints: List<Pair<String, String>> = listOf(
    "U/D" to "System",
    "A" to "Open",
    "B" to "Back",
    "Select" to "Console art",
    "LT" to "Circle",
    "RT" to "Profile / Alerts",
    "Start" to "Settings",
)

/** Music → albums / playlists / songs. */
val XoraMusicBrowseHints: List<Pair<String, String>> = listOf(
    "U/D" to "Browse",
    "A" to "Open",
    "B" to "Back",
    "LT" to "Circle",
    "RT" to "Profile / Alerts",
    "Start" to "Settings",
)

/** Music → Now Playing. */
val XoraNowPlayingHints: List<Pair<String, String>> = listOf(
    "A" to "Play / Pause",
    "L/R" to "Prev / Next",
    "U/D" to "Shuffle / Repeat",
    "B" to "Back",
    "Start" to "Settings",
)

/** Music → Link DSP Accounts. */
val XoraDspBrowseHints: List<Pair<String, String>> = listOf(
    "U/D" to "Service",
    "A" to "Link",
    "B" to "Back",
    "LT" to "Circle",
    "RT" to "Profile / Alerts",
    "Start" to "Settings",
)

/** A console's ROM list. */
val XoraRomBrowseHints: List<Pair<String, String>> = listOf(
    "U/D" to "Game",
    "A" to "Play",
    "B" to "Systems",
    "X" to "Achievements",
    "Y" to "Favourite",
    "Select" to "ROM options",
    "LT" to "Circle",
    "RT" to "Profile / Alerts",
    "Start" to "Settings",
)

/** Vita bubble tray over Home XMB. */
val VitaShortcutTrayHints: List<Pair<String, String>> = listOf(
    "L/R" to "Shortcut",
    "A" to "Open",
    "Select" to "Edit",
    "Y" to "Close",
    "B" to "Close",
    "LT" to "Circle",
    "RT" to "Profile / Alerts",
    "Start+Select" to "Guide",
)

val VitaShortcutTrayEditHints: List<Pair<String, String>> = listOf(
    "L/R" to "Shortcut",
    "A" to "Add / Remove",
    "Select" to "Done",
    "Y" to "Close",
    "B" to "Done",
    "Start+Select" to "Guide",
)

val HomeHubHints: List<Pair<String, String>> = XoraXmbHints

val HomeShortcutsHints: List<Pair<String, String>> = listOf(
    "L/R/U/D" to "Move",
    "A" to "Open",
    "B" to "Shards",
    "Select" to "Customize",
    "R3" to "Customize",
    "LB" to "Feed",
    "RB" to "Games",
    "Y" to "Swap screens",
    "LT" to "Social",
    "RT" to "Profile",
    "Start+Select" to "Guide",
)

val HomeShortcutsCustomizeHints: List<Pair<String, String>> = listOf(
    "U/D" to "Cols / Rows / tiles",
    "L/R" to "Adjust / move",
    "Select" to "Cycle tile size",
    "A" to "Remove / Add",
    "B" to "Done",
    "LB" to "Feed",
    "RB" to "Games",
    "Y" to "Swap screens",
    "Start+Select" to "Guide",
)

val GameSelectorHints: List<Pair<String, String>> = listOf(
    "L/R" to "Game",
    "U/D" to "System",
    "LB/RB" to "Home",
    "A" to "Play",
    "B" to "Home",
    "X" to "Achievements",
    "Y" to "Swap screens",
    "LT" to "Social",
    "RT" to "Profile",
    "Select" to "Library / Emulator",
    "Start" to "Settings",
    "Start+Select" to "Guide",
)

/** Vertical single-screen selector: U/D games, L/R systems. */
val SingleGameSelectorHints: List<Pair<String, String>> = listOf(
    "U/D" to "Game",
    "L/R" to "System",
    "LB/RB" to "Home",
    "A" to "Play",
    "B" to "Home",
    "X" to "Achievements",
    "Y" to "Swap screens",
    "LT" to "Social",
    "RT" to "Profile",
    "Select" to "Library / Emulator",
    "Start" to "Settings",
    "Start+Select" to "Guide",
)

val RssFeedHints: List<Pair<String, String>> = listOf(
    "L/R/U/D" to "Move",
    "LB/RB" to "Home",
    "A" to "Open",
    "B" to "Home",
    "Y" to "Swap screens",
    "LT" to "Social",
    "RT" to "Profile",
    "Start" to "Settings",
    "Start+Select" to "Guide",
)

val RaLibraryHints: List<Pair<String, String>> = listOf(
    "U/D" to "Game",
    "L/R" to "Tab",
    "A" to "Open in library",
    "B" to "Back",
    "LB/RB" to "Home",
    "X" to "This game",
    "LT" to "Social",
    "Start+Select" to "Guide",
)

val GuideHints: List<Pair<String, String>> = listOf(
    "U/D" to "Move",
    "A" to "Select",
    "B" to "Close",
    "Start+Select" to "Close Guide",
)

val SocialMenuHints: List<Pair<String, String>> = listOf(
    "LB/RB" to "Tab",
    "L/R" to "Tab",
    "U/D" to "Move",
    "A" to "Select",
    "B" to "Back",
    "LT" to "Close",
)

val SystemMenuHints: List<Pair<String, String>> = listOf(
    "U/D" to "Move",
    "A" to "Edit / Pick",
    "B" to "Back",
    "RT" to "Close",
)

val StartSettingsHints: List<Pair<String, String>> = listOf(
    "U/D" to "Move",
    "L/R" to "Category",
    "LB/RB" to "Category",
    "A" to "Activate",
    "B" to "Close",
    "Start" to "Close",
)

fun hintsForGuide(): List<Pair<String, String>> = GuideHints

fun hintsForSocialMenu(): List<Pair<String, String>> = SocialMenuHints

fun hintsForSystemMenu(): List<Pair<String, String>> = SystemMenuHints

fun hintsForStartSettings(): List<Pair<String, String>> = StartSettingsHints
