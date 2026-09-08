package com.arcadia.shell.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendPlayingTrackerTest {

    @Test
    fun firstSnapshotDoesNotNotify() {
        val tracker = FriendPlayingTracker()
        val started = tracker.consume(
            listOf("steam:1" to "Celeste", "steam:2" to null),
        )
        assertTrue(started.isEmpty())
        assertTrue(tracker.isSeeded)
    }

    @Test
    fun startingAGameNotifiesOnce() {
        val tracker = FriendPlayingTracker()
        tracker.consume(listOf("1" to null))
        assertEquals(
            listOf("1" to "Hades"),
            tracker.consume(listOf("1" to "Hades")),
        )
        assertTrue(tracker.consume(listOf("1" to "Hades")).isEmpty())
    }

    @Test
    fun switchingGamesNotifiesTheNewTitle() {
        val tracker = FriendPlayingTracker()
        tracker.consume(listOf("1" to "Celeste"))
        assertEquals(
            listOf("1" to "Hades"),
            tracker.consume(listOf("1" to "Hades")),
        )
    }

    @Test
    fun stoppingAGameDoesNotNotify() {
        val tracker = FriendPlayingTracker()
        tracker.consume(listOf("1" to "Celeste"))
        assertTrue(tracker.consume(listOf("1" to null)).isEmpty())
    }

    @Test
    fun comingBackToTheSameGameAfterStoppingNotifiesAgain() {
        val tracker = FriendPlayingTracker()
        tracker.consume(listOf("1" to "Celeste"))
        tracker.consume(listOf("1" to null))
        assertEquals(
            listOf("1" to "Celeste"),
            tracker.consume(listOf("1" to "Celeste")),
        )
    }

    @Test
    fun playingGameTitleFromStatusStripsThePlayingPrefix() {
        assertEquals("Pokémon FireRed", playingGameTitleFromStatus("playing Pokémon FireRed"))
        assertEquals("Super Mario 64", playingGameTitleFromStatus("Playing Super Mario 64"))
        assertEquals(null, playingGameTitleFromStatus("Online"))
        assertEquals(null, playingGameTitleFromStatus("Away"))
        assertEquals(null, playingGameTitleFromStatus(""))
    }

    @Test
    fun discordPlayingGameTitleFallsBackWhenTheSdkOmitsTheName() {
        assertEquals("Celeste", discordPlayingGameTitle("Celeste", "online_game"))
        assertEquals("a game", discordPlayingGameTitle(null, "online_game"))
        assertEquals(null, discordPlayingGameTitle(null, "online_elsewhere"))
    }

    @Test
    fun friendPlayingHeadlineNamesTheUserAndGame() {
        assertEquals(
            "pal is now playing Celeste",
            friendPlayingHeadline("pal", "Celeste"),
        )
        assertEquals(
            "A friend is now playing a game",
            friendPlayingHeadline("  ", ""),
        )
    }
}
