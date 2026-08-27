package com.arcadia.shell.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchPackagesTest {

    @Test
    fun `withPackage rewrites component core and config paths`() {
        val seeded = BuiltInPlayers.all.first {
            it.uniqueId == BuiltInPlayers.RETROARCH_N64_PLAYER_ID
        }
        val bound = RetroArchPackages.withPackage(seeded, RetroArchPackages.PACKAGE_DEFAULT)

        assertEquals(RetroArchPackages.PACKAGE_DEFAULT, bound.packageName)
        assertTrue(
            bound.amStartArguments.contains(
                "/data/data/${RetroArchPackages.PACKAGE_DEFAULT}/cores/" +
                    "${RetroArchPackages.MUPEN64PLUS_NEXT_CORE}_libretro_android.so",
            ),
        )
        assertTrue(
            bound.amStartArguments.contains(
                "/storage/emulated/0/Android/data/${RetroArchPackages.PACKAGE_DEFAULT}/files/retroarch.cfg",
            ),
        )
        assertFalse(bound.amStartArguments.contains(RetroArchPackages.PACKAGE_AARCH64))
    }

    @Test
    fun `launchTemplate matches seeded RetroArch extras`() {
        val template = RetroArchPackages.launchTemplate(
            RetroArchPackages.PACKAGE_AARCH64,
            RetroArchPackages.MUPEN64PLUS_NEXT_CORE,
        )
        val args = AmArgumentParser.parse(template)
        assertEquals(RetroArchPackages.PACKAGE_AARCH64, args.packageName)
        assertEquals(RetroArchPackages.ACTIVITY, args.className)
        assertTrue(args.extras.any { it is AmExtra.StringValue && it.key == "ROM" })
        assertTrue(
            args.extras.any {
                it is AmExtra.StringValue &&
                    it.key == "LIBRETRO" &&
                    it.value.endsWith("mupen64plus_next_libretro_android.so")
            },
        )
    }
}
