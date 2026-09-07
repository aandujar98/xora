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
        assertTrue(bound.amStartArguments.contains("bootPath {file.uri}"))
    }

    @Test
    fun candidateListCoversMainlineAndTurnipForks() {
        assertTrue(Ps2Packages.CANDIDATE_PACKAGES.contains(Ps2Packages.PACKAGE_DEFAULT))
        assertTrue(Ps2Packages.CANDIDATE_PACKAGES.contains(Ps2Packages.PACKAGE_CTURNIP))
        assertTrue(Ps2Packages.CANDIDATE_PACKAGES.contains(Ps2Packages.PACKAGE_TTURNIP))
    }
}
