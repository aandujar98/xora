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
    fun `Switch players cover Eden, Skyline, Strato, and Sudachi packages`() {
        val switchPlayers = BuiltInPlayers.all.filter { "switch" in it.platformIds }
        val packages = switchPlayers.mapNotNull { it.packageName }.toSet()

        assertTrue(packages.contains("dev.eden.eden_emulator"))
        assertTrue(packages.contains("dev.legacy.eden_emulator"))
        assertTrue(packages.contains("dev.eden.eden_emulator.nightly"))
        assertTrue(packages.contains("skyline.emu"))
        assertTrue(packages.contains("emu.skyline"))
        assertTrue(packages.contains("org.stratoemu.strato"))
        assertTrue(packages.contains("org.sudachi.sudachi_emu.ea"))
        assertTrue(packages.contains("org.sudachi.sudachi_emu"))

        val mainline = BuiltInPlayers.all.first { it.uniqueId == "eden.switch" }
        assertEquals("Eden", mainline.name)
        assertTrue(mainline.amStartArguments.contains("{file.uri}"))
        assertTrue(mainline.amStartArguments.contains("org.yuzu.yuzu_emu.activities.EmulationActivity"))
        assertTrue(mainline.killPackageProcesses)

        val skyline = BuiltInPlayers.all.first { it.uniqueId == "skyline.switch" }
        assertTrue(skyline.amStartArguments.contains("emu.skyline.EmulationActivity"))
        assertTrue(skyline.amStartArguments.contains("{file.uri}"))

        val sudachi = BuiltInPlayers.all.first { it.uniqueId == "sudachi.switch" }
        assertTrue(sudachi.amStartArguments.contains("android.nfc.action.TECH_DISCOVERED"))
        assertTrue(sudachi.killPackageProcesses)
    }

    @Test
    fun `handheld standalone players cover Lemonade Pizza Boy My Boy Snes9x EX+ and John GBA`() {
        val lemonade = BuiltInPlayers.all.first { it.uniqueId == "lemonade.3ds" }
        assertEquals("org.gamerytb.lemonade.canary", lemonade.packageName)
        assertTrue(lemonade.amStartArguments.contains("org.citra.citra_emu.activities.EmulationActivity"))
        assertTrue(lemonade.killPackageProcesses)

        val pizza = BuiltInPlayers.all.first { it.uniqueId == "pizzaboy.gb" }
        assertEquals(setOf("gb", "gbc"), pizza.platformIds)
        assertTrue(pizza.amStartArguments.contains("rom_uri {file.path}"))

        val pizzaGba = BuiltInPlayers.all.first { it.uniqueId == "pizzaboy.gba" }
        assertEquals("it.dbtecno.pizzaboygba", pizzaGba.packageName)

        val myBoy = BuiltInPlayers.all.first { it.uniqueId == "myboy.gba" }
        assertEquals("com.fastemulator.gba", myBoy.packageName)
        assertTrue(myBoy.amStartArguments.contains("EmulatorActivity"))

        val snes9x = BuiltInPlayers.all.first { it.uniqueId == "snes9xex.snes" }
        assertEquals("com.explusalpha.Snes9xPlus", snes9x.packageName)
        assertTrue(snes9x.amStartArguments.contains("-t application/zip"))

        val john = BuiltInPlayers.all.first { it.uniqueId == "johngba.gba" }
        assertEquals("com.johnemulators.johngba", john.packageName)
        assertTrue(john.amStartArguments.contains("com.johnemulators.activity.GameActivity"))

        val packages = BuiltInPlayers.all.mapNotNull { it.packageName }.toSet()
        assertTrue(packages.contains("it.dbtecno.pizzaboypro"))
        assertTrue(packages.contains("it.dbtecno.pizzaboygbapro"))
        assertTrue(packages.contains("com.fastemulator.gbafree"))
        assertTrue(packages.contains("com.fastemulator.gbc"))
        assertTrue(packages.contains("com.johnemulators.johngbalite"))
        assertTrue(packages.contains("com.johnemulators.johngbac"))
    }

    @Test
    fun `PS2 and Dreamcast players use grantable URI templates with clear-task`() {
        val nether = BuiltInPlayers.all.first { it.uniqueId == "nethersx2.ps2" }
        assertEquals(Ps2Packages.PACKAGE_DEFAULT, nether.packageName)
        assertTrue(Ps2Packages.isPs2Player(nether))
        assertFalse(Ps2Packages.isPlayPlayer(nether))
        assertTrue(nether.amStartArguments.contains("bootPath {file.bootpath}"))
        assertFalse(nether.amStartArguments.contains("bootPath {file.uri}"))
        assertTrue(nether.amStartArguments.contains("--activity-clear-task"))

        val play = BuiltInPlayers.all.first { it.uniqueId == Ps2Packages.PLAYER_PLAY_ID }
        assertEquals("NetherSX2 (Play Store)", play.name)
        assertEquals(Ps2Packages.PACKAGE_PLAY, play.packageName)
        assertTrue(Ps2Packages.isPlayPlayer(play))
        assertFalse(Ps2Packages.isPs2Player(play))
        assertTrue(play.amStartArguments.contains("bootPath {file.bootpath}"))
        assertTrue(play.amStartArguments.contains(Ps2Packages.ACTIVITY_LEGACY))

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
        val placeholders = listOf(
            "{file.path}",
            "{file.uri}",
            "{file.documenturi}",
            "{file.bootpath}",
        )

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
