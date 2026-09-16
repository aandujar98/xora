package com.arcadia.shell.xoranetwork

/**
 * XOrA Network banners must not replay the inbox on every process start / app update.
 *
 * Restore briefly publishes [XoraNetworkState.signedIn] with an empty inbox, then fills
 * notifications. Seeding on that first snapshot makes the later fetch look like brand-new
 * toasts. Wait until [XoraNetworkState.socialInboxReady] instead.
 */
object XoraNetworkBannerGate {
    fun shouldResetSession(state: XoraNetworkState): Boolean =
        !state.signedIn && !state.restoring

    fun shouldWaitForInbox(state: XoraNetworkState): Boolean =
        !state.signedIn || state.restoring || !state.socialInboxReady
}
