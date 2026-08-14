package com.arcadia.shell.feature.home

import com.arcadia.shell.retroachievements.RaCompletionGame

/**
 * RT (system) profile card state — status line, pinned favorite RA game, and picker/editor chrome.
 */
data class SystemProfileCardState(
    /** Resolved line shown in the status bubble (custom or live activity). */
    val statusLine: String = "Browsing XOrA",
    /** True when [statusLine] comes from a user-set custom status. */
    val isCustomStatus: Boolean = false,
    val statusEditorOpen: Boolean = false,
    val statusDraft: String = "",
    val favorite: SystemFavoriteGame? = null,
    val favoritePickerOpen: Boolean = false,
    val favoritePickerLoading: Boolean = false,
    val favoritePickerGames: List<RaCompletionGame> = emptyList(),
    val favoritePickerError: String? = null,
    /** True while signed in to XOrA Network. Drives the RT presence dot. */
    val xoraNetworkSignedIn: Boolean = false,
    /** Live Nakama presence — only meaningful when [xoraNetworkSignedIn]. */
    val xoraNetworkOnline: Boolean = false,
    val xoraPresenceMode: com.arcadia.shell.xoranetwork.XoraPresenceMode =
        com.arcadia.shell.xoranetwork.XoraPresenceMode.Online,
)

data class SystemFavoriteGame(
    val raGameId: Int,
    val title: String,
    val imageIconUrl: String,
    /** Matched local library playtime when a ROM title lines up; otherwise 0. */
    val playTimeMs: Long = 0L,
)
