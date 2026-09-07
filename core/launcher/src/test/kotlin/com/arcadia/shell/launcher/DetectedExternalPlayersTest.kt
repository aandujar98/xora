package com.arcadia.shell.launcher

import android.content.Intent
import com.arcadia.shell.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectedExternalPlayersTest {

    private val dolphin = standalone("dolphin.gamecube", "Dolphin", "org.dolphinemu.dolphinemu", "gamecube")
    private val skyline = standalone("skyline.switch", "Skyline", "skyline.emu", "switch")
    private val retroArch = Player(
        uniqueId = "retroarch.snes",
        name = "RetroArch (SNES)",
        amStartArguments = "-n com.retroarch.aarch64/com.retroarch.browser.retroactivity." +
            "RetroActivityFuture -e ROM {file.path}",
        acceptedFilenameRegex = "",
        killPackageProcesses = true,
        platformIds = setOf("snes"),
        builtIn = true,
    )
    private val xora = Player(
        uniqueId = "xora.libretro.snes.snes9x",
        name = "XOrA Emulator (SNES)",
        amStartArguments = "-n com.sora.shell/com.arcadia.shell.libretro.XoraLibretroActivity " +
            "-e CORE_NAME snes9x -e ROM_PATH {file.path}",
        acceptedFilenameRegex = "",
        killPackageProcesses = false,
        platformIds = setOf("snes"),
        builtIn = true,
    )

    @Test
    fun `stores only installed standalone apps`() {
        val stored = DetectedExternalPlayers.recipesToStore(
            builtIns = listOf(dolphin, skyline, retroArch, xora),
            isInstalled = { it.uniqueId == "dolphin.gamecube" },
            retroArchInstalled = false,
        )

        assertEquals(listOf("dolphin.gamecube", "xora.libretro.snes.snes9x"), stored.map { it.uniqueId })
    }

    @Test
    fun `keeps RetroArch recipes only when RetroArch is installed`() {
        val withoutRa = DetectedExternalPlayers.recipesToStore(
            builtIns = listOf(retroArch, xora),
            isInstalled = { false },
            retroArchInstalled = false,
        )
        assertEquals(listOf("xora.libretro.snes.snes9x"), withoutRa.map { it.uniqueId })

        val withRa = DetectedExternalPlayers.recipesToStore(
            builtIns = listOf(retroArch, xora),
            isInstalled = { false },
            retroArchInstalled = true,
        )
        assertEquals(
            listOf("retroarch.snes", "xora.libretro.snes.snes9x"),
            withRa.map { it.uniqueId },
        )
    }

    @Test
    fun `platform picker hides uninstalled standalones and keeps XOrA`() {
        val visible = DetectedExternalPlayers.visibleForPlatform(
            platformId = "switch",
            players = listOf(skyline, dolphin, xora.copy(platformIds = setOf("switch"))),
            isInstalled = { false },
        )

        assertEquals(listOf("xora.libretro.snes.snes9x"), visible.map { it.uniqueId })
    }

    @Test
    fun `platform picker includes an installed standalone`() {
        val visible = DetectedExternalPlayers.visibleForPlatform(
            platformId = "switch",
            players = listOf(skyline, dolphin),
            isInstalled = { it.uniqueId == "skyline.switch" },
        )

        assertEquals(listOf("skyline.switch"), visible.map { it.uniqueId })
    }

    @Test
    fun `settings list groups Dolphin recipes and omits XOrA`() {
        val dolphinWii = standalone("dolphin.wii", "Dolphin (Wii)", "org.dolphinemu.dolphinemu", "wii")
        val apps = DetectedExternalPlayers.appsFromPlayers(
            listOf(dolphin, dolphinWii, skyline, retroArch, xora),
        )

        assertEquals(listOf("Dolphin", "RetroArch", "Skyline"), apps.map { it.displayName })
        assertEquals("org.dolphinemu.dolphinemu", apps.first { it.displayName == "Dolphin" }.packageName)
        assertEquals(
            listOf("GC", "Wii"),
            apps.first { it.displayName == "Dolphin" }.platformLabels,
        )
    }

    @Test
    fun `uninstalling an app drops it from the settings list`() {
        val installed = DetectedExternalPlayers.appsFromPlayers(listOf(dolphin, skyline))
        assertEquals(setOf("Dolphin", "Skyline"), installed.map { it.displayName }.toSet())

        val afterRemoval = DetectedExternalPlayers.appsFromPlayers(listOf(dolphin))
        assertEquals(listOf("Dolphin"), afterRemoval.map { it.displayName })
        assertFalse(afterRemoval.any { it.packageName == "skyline.emu" })
    }

    @Test
    fun `standalone apps add their platforms even with no library games`() {
        assertEquals(setOf("gamecube", "switch"), DetectedExternalPlayers.extraPlatformIds(listOf(dolphin, skyline, retroArch, xora)))
        assertEquals(emptySet<String>(), DetectedExternalPlayers.extraPlatformIds(listOf(retroArch, xora)))
    }
}

class EmulatorInstallEventsTest {

    @Test
    fun `package add and replace trigger a rescan`() {
        assertTrue(EmulatorInstallEvents.shouldRefresh(Intent.ACTION_PACKAGE_ADDED, replacing = false))
        assertTrue(EmulatorInstallEvents.shouldRefresh(Intent.ACTION_PACKAGE_REPLACED, replacing = true))
    }

    @Test
    fun `uninstall triggers a rescan but an update's removal does not`() {
        assertTrue(EmulatorInstallEvents.shouldRefresh(Intent.ACTION_PACKAGE_REMOVED, replacing = false))
        assertFalse(EmulatorInstallEvents.shouldRefresh(Intent.ACTION_PACKAGE_REMOVED, replacing = true))
    }

    @Test
    fun `unrelated broadcasts are ignored`() {
        assertFalse(EmulatorInstallEvents.shouldRefresh(Intent.ACTION_PACKAGE_CHANGED, replacing = false))
        assertFalse(EmulatorInstallEvents.shouldRefresh(null, replacing = false))
    }
}

private fun standalone(
    id: String,
    name: String,
    packageName: String,
    platformId: String,
) = Player(
    uniqueId = id,
    name = name,
    amStartArguments = "-n $packageName/.MainActivity -d {file.uri}",
    acceptedFilenameRegex = "",
    killPackageProcesses = false,
    platformIds = setOf(platformId),
    builtIn = true,
)
