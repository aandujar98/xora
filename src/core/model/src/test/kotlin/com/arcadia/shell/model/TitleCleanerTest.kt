package com.arcadia.shell.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCleanerTest {

    @Test
    fun `strips region tags and dump flags`() {
        assertEquals("Super Mario 64", TitleCleaner.clean("Super Mario 64 (USA) [!].z64"))
        assertEquals(
            "Chrono Trigger",
            TitleCleaner.clean("Chrono Trigger (USA) (Rev 1) [T+Eng].sfc"),
        )
    }

    @Test
    fun `treats underscores and dots as word separators`() {
        assertEquals("Sonic The Hedgehog", TitleCleaner.clean("Sonic_The_Hedgehog.md"))
    }

    /** Multi-disc games are distinct entries, so the disc number has to survive the tag stripping. */
    @Test
    fun `keeps a disc number that only appeared inside a tag`() {
        assertEquals(
            "Final Fantasy VII (Disc 2)",
            TitleCleaner.clean("Final Fantasy VII (USA) (Disc 2).cue"),
        )
    }

    @Test
    fun `does not duplicate a disc number already in the title`() {
        assertEquals("Metal Gear Solid Disc 1", TitleCleaner.clean("Metal Gear Solid Disc 1.chd"))
    }

    @Test
    fun `falls back to the bare filename when everything was a tag`() {
        assertEquals("(USA)", TitleCleaner.clean("(USA).zip"))
    }

    @Test
    fun `sort key drops a leading article`() {
        assertEquals("legend of zelda", TitleCleaner.sortKey("The Legend of Zelda"))
        assertEquals("link to the past", TitleCleaner.sortKey("A Link to the Past"))
    }

    @Test
    fun `sort key keeps an article that is part of the first word`() {
        assertEquals("theme park", TitleCleaner.sortKey("Theme Park"))
    }

    @Test
    fun `extension is lowercased and empty when absent`() {
        assertEquals("z64", TitleCleaner.extensionOf("Mario.Z64"))
        assertEquals("", TitleCleaner.extensionOf("README"))
    }
}
