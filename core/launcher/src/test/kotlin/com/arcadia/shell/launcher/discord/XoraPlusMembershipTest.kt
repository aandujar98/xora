package com.arcadia.shell.launcher.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraPlusMembershipTest {

    @Test
    fun memberJsonReadsRoleIds() {
        val ids = parseMemberRoleIds(
            """{"roles":["111","222"],"user":{"id":"9"}}""",
        )
        assertEquals(setOf("111", "222"), ids)
    }

    @Test
    fun guildRolesJsonMapsIdToName() {
        val names = parseGuildRoleNames(
            """[{"id":"1","name":"Member"},{"id":"2","name":"XOrA Plus"}]""",
        )
        assertEquals("XOrA Plus", names["2"])
        assertEquals("Member", names["1"])
    }

    @Test
    fun plusRoleNameMatchesXoraPlusVariants() {
        assertTrue(isXoraPlusRoleName("XOrA Plus"))
        assertTrue(isXoraPlusRoleName("xora+"))
        assertTrue(isXoraPlusRoleName("  XORA PLUS  "))
        assertFalse(isXoraPlusRoleName("Moderator"))
        assertFalse(isXoraPlusRoleName("Plus"))
    }

    @Test
    fun plusIsGrantedWhenMemberHoldsNamedRole() {
        val member = setOf("2", "9")
        val names = mapOf("1" to "everyone", "2" to "XOrA Plus")
        assertTrue(hasXoraPlusRole(member, names, knownIds = emptySet()))
        assertFalse(hasXoraPlusRole(setOf("9"), names, knownIds = emptySet()))
    }

    @Test
    fun plusIsGrantedFromKnownRoleId() {
        assertTrue(
            hasXoraPlusRole(
                memberRoleIds = setOf("plus-id"),
                namedRoles = emptyMap(),
                knownIds = setOf("plus-id"),
            ),
        )
    }

    @Test
    fun roleIdListDropsAnythingThatIsNotASnowflake() {
        assertEquals(
            setOf("1539658971126694071", "1539658971126694072"),
            splitRoleIds(" 1539658971126694071, 1539658971126694072 , XOrA Plus, 12 "),
        )
        assertEquals(emptySet<String>(), splitRoleIds(""))
    }

    @Test
    fun unverifiedMembershipStillOpensTheGateButIsNotConfirmed() {
        val unverified = XoraPlusCheckState(status = XoraPlusStatus.InGuildUnverified)
        assertTrue(unverified.hasPlus)
        assertFalse(unverified.plusConfirmed)

        val confirmed = XoraPlusCheckState(status = XoraPlusStatus.HasPlus)
        assertTrue(confirmed.hasPlus)
        assertTrue(confirmed.plusConfirmed)

        for (blocked in listOf(
            XoraPlusStatus.NotLinked,
            XoraPlusStatus.Checking,
            XoraPlusStatus.InGuildNoPlus,
            XoraPlusStatus.NotInGuild,
            XoraPlusStatus.CheckFailed,
        )) {
            assertFalse(blocked.name, XoraPlusCheckState(status = blocked).hasPlus)
        }
    }

    @Test
    fun linkedDiscordIncludesConnectedAndUserId() {
        assertFalse(discordAccountLinked(DiscordPresenceUiState()))
        assertTrue(
            discordAccountLinked(
                DiscordPresenceUiState(capability = DiscordPresenceCapability.Connected),
            ),
        )
        assertTrue(
            discordAccountLinked(
                DiscordPresenceUiState(currentUserId = "123"),
            ),
        )
    }

    @Test
    fun signOutIsOfferedWhileLinkedOrConnecting() {
        assertFalse(discordCanSignOut(DiscordPresenceUiState()))
        assertTrue(
            discordCanSignOut(
                DiscordPresenceUiState(capability = DiscordPresenceCapability.Connected),
            ),
        )
        assertTrue(
            discordCanSignOut(
                DiscordPresenceUiState(
                    capability = DiscordPresenceCapability.NeedsAccountLink,
                    connecting = true,
                ),
            ),
        )
        assertFalse(
            discordCanSignOut(
                DiscordPresenceUiState(capability = DiscordPresenceCapability.NeedsAccountLink),
            ),
        )
    }

    @Test
    fun onboardingLineReportsWhetherPlusWasDetected() {
        assertEquals(
            "Link Discord so XOrA can detect whether you have the XOrA Plus role.",
            xoraPlusOnboardingLine(bypass = false, plus = XoraPlusCheckState()),
        )
        assertEquals(
            "XOrA detected the XOrA Plus role on this Discord account.",
            xoraPlusOnboardingLine(
                bypass = false,
                plus = XoraPlusCheckState(status = XoraPlusStatus.HasPlus),
            ),
        )
        assertEquals(
            "XOrA did not detect the XOrA Plus role on this Discord account.",
            xoraPlusOnboardingLine(
                bypass = false,
                plus = XoraPlusCheckState(status = XoraPlusStatus.InGuildNoPlus),
            ),
        )
        assertEquals(
            "You're in the XOrA Discord. XOrA could not confirm the XOrA Plus role.",
            xoraPlusOnboardingLine(
                bypass = false,
                plus = XoraPlusCheckState(status = XoraPlusStatus.InGuildUnverified),
            ),
        )
        assertEquals(
            "Access override accepted.",
            xoraPlusOnboardingLine(bypass = true, plus = XoraPlusCheckState()),
        )
        val failed = xoraPlusOnboardingLine(
            bypass = false,
            plus = XoraPlusCheckState(
                status = XoraPlusStatus.CheckFailed,
                detail = "Could not reach Discord (500). Link Discord again, then check XOrA Plus.",
            ),
        )
        assertFalse(failed.contains("five times"))
        assertFalse(failed.contains("role id"))
        assertFalse(failed.contains("0825"))
    }

    @Test
    fun settingsSignInLabelFollowsSession() {
        assertEquals(
            "Sign in with Discord",
            discordSettingsSignInLabel(DiscordPresenceUiState()),
        )
        assertEquals(
            "Connecting Discord…",
            discordSettingsSignInLabel(DiscordPresenceUiState(connecting = true)),
        )
        assertEquals(
            "Re-link Discord",
            discordSettingsSignInLabel(
                DiscordPresenceUiState(capability = DiscordPresenceCapability.Connected),
            ),
        )
        assertEquals(
            "Retry Discord sign-in",
            discordSettingsSignInLabel(
                DiscordPresenceUiState(capability = DiscordPresenceCapability.Failed),
            ),
        )
    }
}
