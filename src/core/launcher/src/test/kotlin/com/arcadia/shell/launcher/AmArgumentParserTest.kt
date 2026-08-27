package com.arcadia.shell.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These templates are the actual seeded profiles and the shapes community configs come in, since a
 * parsing mistake here means an emulator that silently refuses to boot a game.
 */
class AmArgumentParserTest {

    @Test
    fun `expands a relative class name against the package`() {
        val args = AmArgumentParser.parse(
            "-n com.github.stenzek.duckstation/.EmulationActivity",
        )

        assertEquals("com.github.stenzek.duckstation", args.packageName)
        assertEquals("com.github.stenzek.duckstation.EmulationActivity", args.className)
        assertTrue(args.hasComponent)
    }

    @Test
    fun `keeps an absolute class name as written`() {
        val args = AmArgumentParser.parse(
            "-n com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture",
        )

        assertEquals("com.retroarch.aarch64", args.packageName)
        assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", args.className)
    }

    @Test
    fun `a package with no class is not a usable component`() {
        val args = AmArgumentParser.parse("-n org.dolphinemu.dolphinemu")

        assertEquals("org.dolphinemu.dolphinemu", args.packageName)
        assertNull(args.className)
        assertTrue(!args.hasComponent)
    }

    @Test
    fun `parses typed extras`() {
        val args = AmArgumentParser.parse(
            "-e bootPath {file.path} --ez resumeState 0 --ei slot 3 --el seed 90000000000 " +
                "--ef speed 1.5",
        )

        assertEquals(
            listOf(
                AmExtra.StringValue("bootPath", "{file.path}"),
                AmExtra.BooleanValue("resumeState", false),
                AmExtra.IntValue("slot", 3),
                AmExtra.LongValue("seed", 90_000_000_000L),
                AmExtra.FloatValue("speed", 1.5f),
            ),
            args.extras,
        )
    }

    @Test
    fun `accepts both spellings of a true boolean extra`() {
        val args = AmArgumentParser.parse("--ez first true --ez second 1 --ez third TRUE")

        assertEquals(
            listOf(true, true, true),
            args.extras.filterIsInstance<AmExtra.BooleanValue>().map { it.value },
        )
    }

    @Test
    fun `keeps quoted values with spaces in one piece`() {
        val args = AmArgumentParser.parse("""-e ROM "/roms/Chrono Trigger (USA).sfc"""")

        assertEquals(
            listOf(AmExtra.StringValue("ROM", "/roms/Chrono Trigger (USA).sfc")),
            args.extras,
        )
    }

    @Test
    fun `reads action data mime type and categories`() {
        val args = AmArgumentParser.parse(
            "-a android.intent.action.VIEW -d {file.documenturi} -t application/octet-stream " +
                "-c android.intent.category.DEFAULT",
        )

        assertEquals("android.intent.action.VIEW", args.action)
        assertEquals("{file.documenturi}", args.data)
        assertEquals("application/octet-stream", args.mimeType)
        assertEquals(listOf("android.intent.category.DEFAULT"), args.categories)
    }

    @Test
    fun `reads flags in decimal and hex`() {
        assertEquals(0x10000000, AmArgumentParser.parse("-f 0x10000000").flags)
        assertEquals(268435456, AmArgumentParser.parse("-f 268435456").flags)
    }

    @Test
    fun `collects task flags`() {
        val args = AmArgumentParser.parse("--activity-clear-task --activity-clear-top")

        assertTrue(args.clearTask)
        assertTrue(args.clearTop)
    }

    /** A truncated template must not cause the next flag to be swallowed as a value. */
    @Test
    fun `a flag missing its value does not consume the following flag`() {
        val args = AmArgumentParser.parse("-e onlyKey -a android.intent.action.MAIN")

        assertEquals("android.intent.action.MAIN", args.action)
        assertTrue(args.extras.isEmpty())
    }

    @Test
    fun `a negative number is still read as a value`() {
        val args = AmArgumentParser.parse("--ei slot -1 --ef offset -0.5")

        assertEquals(
            listOf<AmExtra>(AmExtra.IntValue("slot", -1), AmExtra.FloatValue("offset", -0.5f)),
            args.extras,
        )
    }

    @Test
    fun `unrecognised tokens are reported rather than dropped`() {
        val args = AmArgumentParser.parse("-a android.intent.action.MAIN --nonsense QUITFOCUS")

        assertEquals(listOf("--nonsense", "QUITFOCUS"), args.unknownTokens)
    }
}
