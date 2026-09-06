package com.arcadia.shell.feature.home

import com.arcadia.shell.model.Game
import com.arcadia.shell.model.GamePlatform
import com.arcadia.shell.model.PlatformCatalog
import com.arcadia.shell.model.PlatformSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraXmbAndroidPlatformTest {

    @Test
    fun androidSummaryIsAppendedWhenAppsAreMirrored() {
        val nes = PlatformSummary(
            platform = PlatformCatalog.requireById("nes"),
            gameCount = 2,
        )
        val games = listOf(
            sampleGame(id = "nes:zelda", platformId = "nes", title = "Zelda"),
            sampleGame(
                id = "android:com.android.chrome",
                platformId = GamePlatform.Android.id,
                title = "Chrome",
                fileName = "com.android.chrome",
            ),
        )
        val summaries = withAndroidPlatformSummary(listOf(nes), games)
        assertEquals(2, summaries.size)
        assertEquals(GamePlatform.Android, summaries.last().platform)
        assertEquals(1, summaries.last().gameCount)
    }

    @Test
    fun androidSummaryIsOmittedWhenNoAppsAreMirrored() {
        val games = listOf(sampleGame(id = "nes:zelda", platformId = "nes", title = "Zelda"))
        assertTrue(withAndroidPlatformSummary(emptyList(), games).isEmpty())
    }

    @Test
    fun platformsMenuListsAndroidAndMarksItReady() {
        val summaries = listOf(
            PlatformSummary(platform = GamePlatform.Android, gameCount = 3),
        )
        val items = buildXoraSystemItems(summaries, readyPlatformIds = emptySet())
        val android = items.single { it.id == "sys_android" }
        assertEquals("Android", android.title)
        assertEquals("3 apps", android.subtitle)
        assertTrue(android.ready)
        assertEquals(XmbIcon.Device, android.icon)
        assertEquals(XoraXmbAction.DrillSystem(GamePlatform.Android.id), android.action)
    }

    @Test
    fun emptyAndroidCountDoesNotCreateARow() {
        val items = buildXoraSystemItems(
            listOf(PlatformSummary(platform = GamePlatform.Android, gameCount = 0)),
        )
        assertTrue(items.none { it.id == "sys_android" })
    }

    @Test
    fun romDrillListsAndroidApps() {
        val chrome = sampleGame(
            id = "android:com.android.chrome",
            platformId = GamePlatform.Android.id,
            title = "Chrome",
            fileName = "com.android.chrome",
        )
        val items = buildXoraRomItems(listOf(chrome))
        assertEquals("Chrome", items.single().title)
        assertEquals("App", items.single().subtitle)
        assertEquals(XoraXmbAction.LaunchGame(chrome.id), items.single().action)
    }

    private fun sampleGame(
        id: String,
        platformId: String,
        title: String,
        fileName: String = "$title.nes",
    ) = Game(
        id = id,
        title = title,
        sortKey = title,
        platformId = platformId,
        fileName = fileName,
        filePath = fileName,
        documentUri = null,
        sizeBytes = 1L,
    )
}
