package com.arcadia.shell.launcher.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DismissedNotificationTrackerTest {

    @Test
    fun clearedKeysStayDismissed() {
        val persisted = mutableListOf<Set<String>>()
        val tracker = DismissedNotificationTracker { persisted += it }
        val invite = ShellNotification.XoraNetplayInvite(
            id = "xora-netplay:pal|ABC123|9",
            displayName = "pal",
            gameTitle = "Kirby",
            sessionCode = "ABC123",
            fromUsername = "pal",
        )
        tracker.dismiss(invite.dismissalKeys())
        assertTrue(tracker.isDismissed(invite.dismissalKeys()))
        assertTrue(tracker.isDismissed("xora-netplay-session:pal|ABC123"))
        assertTrue(persisted.isNotEmpty())
        assertTrue(persisted.last().contains("xora-netplay:pal|ABC123|9"))
    }

    @Test
    fun seedRestoresClearedKeysAcrossProcessDeath() {
        val tracker = DismissedNotificationTracker()
        tracker.seed(listOf("xora-message:42", "  xora-request:sam  "))
        assertTrue(tracker.isDismissed("xora-message:42"))
        assertTrue(tracker.isDismissed("xora-request:sam"))
        assertFalse(tracker.isDismissed("xora-message:99"))
    }

    @Test
    fun blankKeysAreIgnored() {
        val tracker = DismissedNotificationTracker()
        tracker.dismiss(listOf("", "  "))
        assertTrue(tracker.snapshot().isEmpty())
    }
}
