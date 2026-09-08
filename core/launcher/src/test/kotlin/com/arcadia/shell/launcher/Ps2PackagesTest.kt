package com.arcadia.shell.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ps2PackagesTest {

    private val seed = BuiltInPlayers.all.first { it.uniqueId == "nethersx2.ps2" }

    @Test
    fun seededRecipeIsAPs2Player() {
        assertTrue(Ps2Packages.isPs2Player(seed))
        assertFalse(
            Ps2Packages.isPs2Player(
                BuiltInPlayers.all.first { it.uniqueId == "ppsspp.psp" },
            ),
        )
    }

    @Test
    fun withPackageRetargetsTurnipClassic() {
        val bound = Ps2Packages.withPackage(seed, Ps2Packages.PACKAGE_CTURNIP)
        assertEquals(Ps2Packages.PACKAGE_CTURNIP, bound.packageName)
        assertTrue(
            bound.amStartArguments.contains(
                "${Ps2Packages.PACKAGE_CTURNIP}/${Ps2Packages.ACTIVITY_LEGACY}",
            ),
        )
        assertTrue(bound.amStartArguments.contains("bootPath {file.bootpath}"))
    }

    @Test
    fun candidateListCoversMainlineAndTurnipForks() {
        assertTrue(Ps2Packages.CANDIDATE_PACKAGES.contains(Ps2Packages.PACKAGE_DEFAULT))
        assertTrue(Ps2Packages.CANDIDATE_PACKAGES.contains(Ps2Packages.PACKAGE_CTURNIP))
        assertTrue(Ps2Packages.CANDIDATE_PACKAGES.contains(Ps2Packages.PACKAGE_TTURNIP))
        assertFalse(Ps2Packages.CANDIDATE_PACKAGES.contains(Ps2Packages.PACKAGE_PLAY))
    }

    @Test
    fun playStoreRecipeIsSeparateFromSideloadFamily() {
        val play = BuiltInPlayers.all.first { it.uniqueId == Ps2Packages.PLAYER_PLAY_ID }
        assertTrue(Ps2Packages.isPlayPlayer(play))
        assertFalse(Ps2Packages.isPs2Player(play))
        assertTrue(Ps2Packages.PLAY_PACKAGES.contains(Ps2Packages.PACKAGE_PLAY))
        assertTrue(Ps2Packages.PLAY_PACKAGES.contains(Ps2Packages.PACKAGE_PLAY_NSX2))
    }

    @Test
    fun withPackageRetargetsPlayStoreNsx2Listing() {
        val play = BuiltInPlayers.all.first { it.uniqueId == Ps2Packages.PLAYER_PLAY_ID }
        val bound = Ps2Packages.withPackage(play, Ps2Packages.PACKAGE_PLAY_NSX2)
        assertEquals(Ps2Packages.PACKAGE_PLAY_NSX2, bound.packageName)
        assertTrue(
            bound.amStartArguments.contains(
                "${Ps2Packages.PACKAGE_PLAY_NSX2}/${Ps2Packages.ACTIVITY_LEGACY}",
            ),
        )
        assertTrue(bound.amStartArguments.contains("bootPath {file.bootpath}"))
    }
}
