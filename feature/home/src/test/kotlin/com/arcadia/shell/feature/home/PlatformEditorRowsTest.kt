package com.arcadia.shell.feature.home

import com.arcadia.shell.model.GamePlatform
import com.arcadia.shell.scraper.ScraperPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformEditorRowsTest {

    @Test
    fun railListsEmulatorsBetweenArtworkAndLibrary() {
        val labels = PlatformEditorSection.entries.map { it.label }
        assertEquals(listOf("Details", "Artwork", "Emulators", "Library"), labels)
    }

    @Test
    fun emulatorsSectionListsCurrentChoiceAndChangeAction() {
        val rows = emulatorRows(samplePlatform(), currentEmulatorLabel = "RetroArch · melonDS", actions())
        assertEquals(listOf("emulator", "emulator_choose", "emulator_auto"), rows.map { it.key })
        val current = rows.single { it.key == "emulator" }
        assertEquals("Default emulator", current.label)
        assertEquals("RetroArch · melonDS", current.value)
        assertNotNull(current.onActivate)
        assertNotNull(current.onClear)
        assertNotNull(rows.single { it.key == "emulator_choose" }.onActivate)
        assertNotNull(rows.single { it.key == "emulator_auto" }.onActivate)
    }

    @Test
    fun automaticEmulatorOmitsResetRow() {
        val rows = emulatorRows(samplePlatform(), currentEmulatorLabel = null, actions())
        assertEquals(listOf("emulator", "emulator_choose"), rows.map { it.key })
        val current = rows.single { it.key == "emulator" }
        assertEquals("Automatic", current.value)
        assertNull(current.onClear)
    }

    @Test
    fun libraryNoLongerHostsTheEmulatorRow() {
        val rows = platformEditorRows(
            section = PlatformEditorSection.Library,
            platform = samplePlatform(),
            gameCount = 3,
            bannerPath = null,
            hasCustomBanner = false,
            platformPreference = ScraperPreference.Auto,
            currentEmulatorLabel = "RetroArch · melonDS",
            actions = actions(),
        )
        assertTrue(rows.none { it.key.startsWith("emulator") })
        assertEquals(listOf("scraperplatform", "rescrapeplatform"), rows.map { it.key })
    }

    private fun samplePlatform() = GamePlatform(
        id = "nds",
        displayName = "Nintendo DS",
        shortName = "NDS",
        extensions = setOf("nds"),
        folderAliases = setOf("nds"),
    )

    private fun actions() = PlatformEditorActions(
        onDismiss = {},
        onUploadBanner = {},
        onClearBanner = {},
        onRefreshArt = {},
        onSetPlatformPreference = {},
        onChooseEmulator = {},
        onClearEmulator = {},
        onRescrapePlatform = {},
    )
}
