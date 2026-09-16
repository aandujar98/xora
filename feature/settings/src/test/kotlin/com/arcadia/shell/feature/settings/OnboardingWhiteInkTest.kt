package com.arcadia.shell.feature.settings

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Onboarding copy has to read white over the wallpaper. Colouring each `Text` cannot reach
 * Material's own labels, so the scheme is overridden once — this pins that override.
 */
class OnboardingWhiteInkTest {

    @Test
    fun `every ink role material can pick for text is white`() {
        val scheme = onboardingWhiteInk(
            darkColorScheme(
                onSurface = Color.Gray,
                onSurfaceVariant = Color.Gray,
                onBackground = Color.Gray,
                onPrimary = Color.Black,
                onPrimaryContainer = Color.Black,
                onSecondary = Color.Black,
                onSecondaryContainer = Color.Black,
                onTertiary = Color.Black,
                onTertiaryContainer = Color.Black,
                inverseOnSurface = Color.Black,
                error = Color.Red,
                onError = Color.Black,
                onErrorContainer = Color.Red,
            ),
        )
        val inks = mapOf(
            "onSurface" to scheme.onSurface,
            "onSurfaceVariant" to scheme.onSurfaceVariant,
            "onBackground" to scheme.onBackground,
            "onPrimary" to scheme.onPrimary,
            "onPrimaryContainer" to scheme.onPrimaryContainer,
            "onSecondary" to scheme.onSecondary,
            "onSecondaryContainer" to scheme.onSecondaryContainer,
            "onTertiary" to scheme.onTertiary,
            "onTertiaryContainer" to scheme.onTertiaryContainer,
            "inverseOnSurface" to scheme.inverseOnSurface,
            "error" to scheme.error,
            "onError" to scheme.onError,
            "onErrorContainer" to scheme.onErrorContainer,
        )
        for ((role, colour) in inks) {
            assertEquals(role, Color.White, colour)
        }
    }

    @Test
    fun `container fills are left alone so buttons keep their shape`() {
        val base = darkColorScheme()
        val scheme = onboardingWhiteInk(base)
        assertEquals(base.primary, scheme.primary)
        assertEquals(base.surface, scheme.surface)
        assertEquals(base.background, scheme.background)
    }
}
