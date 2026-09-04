package com.arcadia.shell.launcher.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordPresencePublishTest {

    @Test
    fun `idle clears remote presence`() {
        assertNull(discordPresencePublish(DiscordPresenceActivity.Idle))
    }

    @Test
    fun `menus publish Browsing XOrA with a Discord-valid state`() {
        val payload = discordPresencePublish(DiscordPresenceActivity.InSora)!!
        assertEquals("Browsing XOrA", payload.details)
        assertEquals("In the menus", payload.state)
        assertEquals("XOrA", payload.name)
        assertTrue(payload.details.length in 2..128)
        assertTrue(payload.state!!.length in 2..128)
    }

    @Test
    fun `focused game in menus does not publish the title`() {
        val payload = discordPresencePublish(
            DiscordPresenceActivity.Browsing(
                gameTitle = "Super Metroid",
                platformName = "SNES",
            ),
        )!!
        assertEquals("Browsing XOrA", payload.details)
        assertEquals("In the menus", payload.state)
    }

    @Test
    fun `launch publishes Playing the game`() {
        val payload = discordPresencePublish(
            DiscordPresenceActivity.Playing(
                gameTitle = "Persona 4 Golden",
                platformName = "PlayStation Vita",
            ),
        )!!
        assertEquals("Playing Persona 4 Golden", payload.details)
        assertEquals("PlayStation Vita", payload.state)
    }

    @Test
    fun `short platform name is omitted so Discord does not reject state`() {
        val payload = discordPresencePublish(
            DiscordPresenceActivity.Playing(
                gameTitle = "Tetris",
                platformName = " ",
            ),
        )!!
        assertEquals("Playing Tetris", payload.details)
        assertNull(payload.state)
    }
}
