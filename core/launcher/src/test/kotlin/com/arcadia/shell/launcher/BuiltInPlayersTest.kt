package com.arcadia.shell.launcher

import com.arcadia.shell.model.PlatformCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seeded profiles are data, and nothing else validates them, so this cross-checks them against the
 * parser that has to consume them at launch time.
 *
 * Merely touching [BuiltInPlayers.all] is itself a meaningful assertion: an ordering mistake among
 * the object's properties surfaces here as a class initializer failure rather than as a crash during
 * first-run seeding on a device.
 */
class BuiltInPlayersTest {

    @Test
    fun `the seed list builds and is not empty`() {
        assertTrue(BuiltInPlayers.all.isNotEmpty())
    }

    @Test
    fun `N64 RetroArch profile uses Mupen64Plus-Next core`() {
        val n64 = BuiltInPlayers.all.first { it.uniqueId == BuiltInPlayers.RETROARCH_N64_PLAYER_ID }
        assertEquals("retroarch.n64", n64.uniqueId)
        assertTrue(n64.name.contains("Mupen64Plus-Next", ignoreCase = true))
        assertTrue(
            n64.amStartArguments.contains(
                "${RetroArchPackages.MUPEN64PLUS_NEXT_CORE}_libretro_android.so",
            ),
        )
        assertEquals(RetroArchPackages.PACKAGE_AARCH64, n64.packageName)
        assertTrue(n64.killPackageProcesses)
    }

    @Test
    fun `3DS players cover Azahar vanilla, Play Store, and Citra packages`() {
        val packages = BuiltInPlayers.all
            .filter { "3ds" in it.platformIds }
            .mapNotNull { it.packageName }
            .toSet()

        assertTrue(packages.contains("org.azahar_emu.azahar"))
        assertTrue(packages.contains("io.github.lime3ds.android"))
        assertTrue(packages.contains("org.citra.citra_emu"))

        val play = BuiltInPlayers.all.first { it.uniqueId == "azahar.play" }
        assertTrue(play.amStartArguments.contains("{file.uri}"))
        assertTrue(play.name.contains("Play Store", ignoreCase = true))
    }

    @Test
    fun `Wii U players cover Cemu mainline, Odin, and legacy packages`() {
        val wiiu = BuiltInPlayers.all.filter { "wiiu" in it.platformIds }
        val packages = wiiu.mapNotNull { it.packageName }.toSet()

        assertTrue(packages.contains("info.cemu.cemu"))
        assertTrue(packages.contains("info.cemu.cemu.odin"))
        assertTrue(packages.contains("info.cemu.Cemu"))

        val mainline = BuiltInPlayers.all.first { it.uniqueId == "cemu.wiiu" }
        assertEquals("Cemu", mainline.name)
        assertTrue(mainline.amStartArguments.contains("{file.uri}"))
        assertTrue(mainline.amStartArguments.contains("EmulationActivity"))
    }

    @Test
    fun `Switch players cover Eden mainline, legacy, and nightly packages`() {
        val switchPlayers = BuiltInPlayers.all.filter { "switch" in it.platformIds }
        val packages = switchPlayers.mapNotNull { it.packageName }.toSet()

        assertTrue(packages.contains("dev.eden.eden_emulator"))
        assertTrue(packages.contains("dev.legacy.eden_emulator"))
        assertTrue(packages.contains("dev.eden.eden_emulator.nightly"))

        val mainline = BuiltInPlayers.all.first { it.uniqueId == "eden.switch" }
        assertEquals("Eden", mainline.name)
        assertTrue(mainline.amStartArguments.contains("{file.uri}"))
        assertTrue(mainline.amStartArguments.contains("org.yuzu.yuzu_emu.activities.EmulationActivity"))
        assertTrue(mainline.killPackageProcesses)
    }

    @Test
    fun `PS2 and Dreamcast players use grantable URI templates with clear-task`() {
        val nether = BuiltInPlayers.all.first { it.uniqueId == "nethersx2.ps2" }
        assertEquals("xyz.aethersx2.android", nether.packageName)
        assertTrue(nether.amStartArguments.contains("bootPath {file.uri}"))
        assertTrue(nether.amStartArguments.contains("--activity-clear-task"))

        val flycast = BuiltInPlayers.all.first { it.uniqueId == "flycast.dreamcast" }
        assertEquals("com.flycast.emulator", flycast.packageName)
        assertTrue(flycast.amStartArguments.contains("com.flycast.emulator.MainActivity"))
        assertTrue(flycast.amStartArguments.contains("{file.uri}"))
        assertTrue(flycast.killPackageProcesses)
    }

    @Test
    fun `player ids are unique`() {
        val duplicates = BuiltInPlayers.all
            .groupBy { it.uniqueId }
            .filterValues { it.size > 1 }
            .keys

        assertEquals(emptySet<String>(), duplicates)
    }

    @Test
    fun `every template resolves to a launchable component`() {
        BuiltInPlayers.all.forEach { player ->
            val args = AmArgumentParser.parse(player.amStartArguments)

            assertTrue(
                "${player.uniqueId} has no -n component",
                args.hasComponent,
            )
            assertEquals(
                "${player.uniqueId} package does not match its parsed component",
                args.packageName,
                player.packageName,
            )
        }
    }

    @Test
    fun `no template contains an unparsed token`() {
        BuiltInPlayers.all.forEach { player ->
            val args = AmArgumentParser.parse(player.amStartArguments)

            assertEquals(
                "${player.uniqueId} has tokens the parser did not understand",
                emptyList<String>(),
                args.unknownTokens,
            )
        }
    }

    /** A profile that names no file placeholder would launch the emulator with no game. */
    @Test
    fun `every template passes the game to the emulator`() {
        val placeholders = listOf("{file.path}", "{file.uri}", "{file.documenturi}")

        BuiltInPlayers.all.forEach { player ->
            assertTrue(
                "${player.uniqueId} never references the game file",
                placeholders.any { it in player.amStartArguments },
            )
        }
    }

    @Test
    fun `every player claims a platform that exists in the catalog`() {
        BuiltInPlayers.all.forEach { player ->
            assertFalse("${player.uniqueId} claims no platform", player.platformIds.isEmpty())

            player.platformIds.forEach { platformId ->
                assertTrue(
                    "${player.uniqueId} claims unknown platform $platformId",
                    PlatformCatalog.byId(platformId) != null,
                )
            }
        }
    }

    @Test
    fun `accepted filename patterns match the platforms they claim`() {
        BuiltInPlayers.all.forEach { player ->
            val regex = Regex(player.acceptedFilenameRegex)
            val extension = player.platformIds
                .mapNotNull { PlatformCatalog.byId(it) }
                .flatMap { it.extensions }
                .first()

            assertTrue(
                "${player.uniqueId} rejects its own platform's .$extension files",
                regex.matches("Some Game (USA).$extension"),
            )
            assertFalse(
                "${player.uniqueId} accepts a non-ROM file",
                regex.matches("box art.png"),
            )
        }
    }
}
