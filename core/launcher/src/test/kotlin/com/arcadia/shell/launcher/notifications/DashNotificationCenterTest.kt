package com.arcadia.shell.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The queue's ordering rule on its own. [DashNotificationCenter] itself pumps on the main
 * dispatcher, which a plain JVM test has no way to provide, so the rule lives in a function
 * rather than inside the pump.
 */
class DashNotificationCenterTest {

    private fun line(kind: DashNotificationKind, text: String) =
        DashNotification(id = "$kind:$text", text = text, kind = kind)

    private fun ArrayDeque<DashNotification>.kinds() = map { it.kind }

    @Test
    fun `playtime jumps whatever is already waiting`() {
        val queue = ArrayDeque<DashNotification>()
        enqueueDashLine(queue, line(DashNotificationKind.Scraping, "Fetching artwork"))
        enqueueDashLine(queue, line(DashNotificationKind.Scanning, "Scanning for new files"))
        enqueueDashLine(queue, line(DashNotificationKind.Playtime, "2 hours in Persona 3"))

        // Coming back from a game is exactly when a scrape or a scan the session started is
        // likely to be queued ahead of the playtime line.
        assertEquals(
            listOf(
                DashNotificationKind.Playtime,
                DashNotificationKind.Scraping,
                DashNotificationKind.Scanning,
            ),
            queue.kinds(),
        )
    }

    @Test
    fun `everything else keeps the order it arrived in`() {
        val queue = ArrayDeque<DashNotification>()
        enqueueDashLine(queue, line(DashNotificationKind.Scraping, "Fetching artwork"))
        enqueueDashLine(queue, line(DashNotificationKind.Scanning, "Scanning for new files"))
        enqueueDashLine(queue, line(DashNotificationKind.Update, "XOrA 0.5.2 is available"))

        assertEquals(
            listOf(
                DashNotificationKind.Scraping,
                DashNotificationKind.Scanning,
                DashNotificationKind.Update,
            ),
            queue.kinds(),
        )
    }

    @Test
    fun `two playtime lines stay in the order they happened`() {
        val queue = ArrayDeque<DashNotification>()
        enqueueDashLine(queue, line(DashNotificationKind.Scraping, "Fetching artwork"))
        enqueueDashLine(queue, line(DashNotificationKind.Playtime, "40 minutes in Sly 2"))
        enqueueDashLine(queue, line(DashNotificationKind.Playtime, "2 hours in Persona 3"))

        // A plain addFirst on both would have reversed them.
        assertEquals(
            listOf("40 minutes in Sly 2", "2 hours in Persona 3", "Fetching artwork"),
            queue.map { it.text },
        )
    }

    @Test
    fun `playtime into an empty queue is simply first`() {
        val queue = ArrayDeque<DashNotification>()
        enqueueDashLine(queue, line(DashNotificationKind.Playtime, "2 hours in Persona 3"))
        assertEquals(listOf(DashNotificationKind.Playtime), queue.kinds())
    }
}
