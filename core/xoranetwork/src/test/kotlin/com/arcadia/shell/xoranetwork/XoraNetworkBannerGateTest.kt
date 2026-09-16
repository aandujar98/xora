package com.arcadia.shell.xoranetwork

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraNetworkBannerGateTest {

    @Test
    fun restoreDoesNotResetOrAnnounceUntilInboxIsReady() {
        val restoring = XoraNetworkState(signedIn = true, restoring = true)
        assertFalse(XoraNetworkBannerGate.shouldResetSession(restoring))
        assertTrue(XoraNetworkBannerGate.shouldWaitForInbox(restoring))

        val signedInEmpty = XoraNetworkState(
            signedIn = true,
            restoring = false,
            socialInboxReady = false,
        )
        assertFalse(XoraNetworkBannerGate.shouldResetSession(signedInEmpty))
        assertTrue(XoraNetworkBannerGate.shouldWaitForInbox(signedInEmpty))
    }

    @Test
    fun readyInboxCanAnnounceDiffs() {
        val ready = XoraNetworkState(
            signedIn = true,
            restoring = false,
            socialInboxReady = true,
        )
        assertFalse(XoraNetworkBannerGate.shouldResetSession(ready))
        assertFalse(XoraNetworkBannerGate.shouldWaitForInbox(ready))
    }

    @Test
    fun realSignOutResetsTheSession() {
        val signedOut = XoraNetworkState(signedIn = false, restoring = false)
        assertTrue(XoraNetworkBannerGate.shouldResetSession(signedOut))
        assertTrue(XoraNetworkBannerGate.shouldWaitForInbox(signedOut))
    }
}
