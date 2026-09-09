package com.arcadia.shell.feature.settings

import com.arcadia.shell.datastore.VisualPerformanceChoices
import com.arcadia.shell.datastore.VisualPerformanceMode
import com.arcadia.shell.datastore.visualPerformanceModeLabel
import com.arcadia.shell.launcher.discord.DiscordPresenceCapability
import com.arcadia.shell.launcher.discord.DiscordPresenceUiState
import com.arcadia.shell.launcher.discord.XoraPlusCheckState
import com.arcadia.shell.launcher.discord.XoraPlusStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepTest {

    @Test
    fun `android apps sits between library and emulators`() {
        val steps = OnboardingStep.entries
        assertEquals(OnboardingStep.Welcome, steps[0])
        assertEquals(OnboardingStep.Profile, steps[1])
        assertEquals(OnboardingStep.DisplayMode, steps[2])
        assertEquals(OnboardingStep.Performance, steps[3])
        assertEquals(OnboardingStep.Library, steps[4])
        assertEquals(OnboardingStep.AndroidApps, steps[5])
        assertEquals(OnboardingStep.Emulators, steps[6])
        assertEquals(OnboardingStep.Scrapers, steps[7])
    }

    @Test
    fun `discord and steam are separate steps in that order`() {
        val steps = OnboardingStep.entries
        assertEquals(OnboardingStep.Discord, steps[8])
        assertEquals(OnboardingStep.Steam, steps[9])
        assertEquals(OnboardingStep.RetroAchievements, steps[10])
    }

    @Test
    fun performanceStepOffersAutoPerformanceAndQuality() {
        assertEquals(
            listOf(
                VisualPerformanceMode.Auto,
                VisualPerformanceMode.Smooth,
                VisualPerformanceMode.Quality,
            ),
            VisualPerformanceChoices,
        )
        assertEquals("Auto", visualPerformanceModeLabel(VisualPerformanceMode.Auto))
        assertEquals("Performance", visualPerformanceModeLabel(VisualPerformanceMode.Smooth))
        assertEquals("Quality", visualPerformanceModeLabel(VisualPerformanceMode.Quality))
        assertEquals("perf_auto", onboardingPerformancePadId(VisualPerformanceMode.Auto))
        assertEquals("perf_smooth", onboardingPerformancePadId(VisualPerformanceMode.Smooth))
        assertEquals("perf_quality", onboardingPerformancePadId(VisualPerformanceMode.Quality))
    }

    @Test
    fun `next is blocked only while the emulator scan is running`() {
        val scanning = OnboardingUiState(
            step = OnboardingStep.Emulators,
            scanRunning = true,
        )
        assertFalse(scanning.canAdvance)

        val ready = OnboardingUiState(
            step = OnboardingStep.Emulators,
            scanRunning = false,
            scanCompleted = true,
        )
        assertTrue(ready.canAdvance)

        val library = OnboardingUiState(step = OnboardingStep.Library, scanRunning = true)
        assertTrue(library.canAdvance)
    }

    @Test
    fun discordNextRequiresPlusOrBypass() {
        val blocked = OnboardingUiState(step = OnboardingStep.Discord)
        assertFalse(blocked.canAdvance)

        val linkedNoPlus = OnboardingUiState(
            step = OnboardingStep.Discord,
            discordPresence = DiscordPresenceUiState(
                capability = DiscordPresenceCapability.Connected,
            ),
            xoraPlus = XoraPlusCheckState(status = XoraPlusStatus.InGuildNoPlus),
        )
        assertFalse(linkedNoPlus.canAdvance)

        val plus = OnboardingUiState(
            step = OnboardingStep.Discord,
            discordPresence = DiscordPresenceUiState(
                capability = DiscordPresenceCapability.Connected,
            ),
            xoraPlus = XoraPlusCheckState(status = XoraPlusStatus.HasPlus),
        )
        assertTrue(plus.canAdvance)

        // Discord never names guild roles for a user token; membership alone still opens the gate.
        val unverified = OnboardingUiState(
            step = OnboardingStep.Discord,
            discordPresence = DiscordPresenceUiState(
                capability = DiscordPresenceCapability.Connected,
            ),
            xoraPlus = XoraPlusCheckState(status = XoraPlusStatus.InGuildUnverified),
        )
        assertTrue(unverified.canAdvance)

        val bypass = OnboardingUiState(
            step = OnboardingStep.Discord,
            xoraPlusBypass = true,
        )
        assertTrue(bypass.canAdvance)
    }

    @Test
    fun steamStepNeverBlocksTheFlow() {
        assertTrue(OnboardingUiState(step = OnboardingStep.Steam).canAdvance)
    }

    @Test
    fun artworkStepListsSteamGridIgdbAndScreenScraper() {
        assertEquals(
            listOf(
                OnboardingScraperService.SteamGridDb,
                OnboardingScraperService.Igdb,
                OnboardingScraperService.ScreenScraper,
            ),
            OnboardingScraperService.entries.toList(),
        )
    }
}
