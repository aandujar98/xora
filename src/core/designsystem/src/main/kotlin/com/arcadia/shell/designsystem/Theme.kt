package com.arcadia.shell.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.chrisbanes.haze.rememberHazeState

private val ArcadiaDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Mist100,
    primaryContainer = AccentDim,
    onPrimaryContainer = AccentBright,
    secondary = Signal,
    onSecondary = Ink900,
    background = Ink900,
    onBackground = Mist100,
    surface = Ink800,
    onSurface = Mist100,
    surfaceVariant = Ink600,
    onSurfaceVariant = Mist300,
    surfaceContainer = Ink700,
    surfaceContainerHigh = Ink600,
    surfaceContainerHighest = Ink500,
    outline = Mist500,
    outlineVariant = Ink500,
    error = Danger,
    onError = Ink900,
    tertiary = Warning,
    onTertiary = Ink900,
)

/**
 * Light palette: pale SORA sky papers + medium-blue primary, readable dusk ink.
 * Intentionally not purple-on-white or cream+terracotta.
 */
private val ArcadiaLightColorScheme = lightColorScheme(
    primary = SoraBlue,
    onPrimary = Paper800,
    primaryContainer = SoraBlueContainer,
    onPrimaryContainer = OnSoraBlueContainer,
    secondary = SoraBlueBright,
    onSecondary = Slate900,
    background = Paper950,
    onBackground = Slate900,
    surface = Paper900,
    onSurface = Slate900,
    surfaceVariant = Paper700,
    onSurfaceVariant = Slate700,
    surfaceContainer = Paper800,
    surfaceContainerHigh = Paper800,
    surfaceContainerHighest = Paper700,
    outline = Slate500,
    outlineVariant = Paper600,
    error = Danger,
    onError = Paper800,
    tertiary = Warning,
    onTertiary = Slate900,
)

@Composable
fun ArcadiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    shellThemeId: String = ShellThemeId.Default.id,
    uiTextScale: Float = 0.85f,
    /**
     * Multiplies [LocalDensity] so the whole shell (dp layout + chrome) fits the panel.
     * 1f leaves system density untouched. Typical Auto range is ~0.7–1.35.
     */
    uiLayoutScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val shellTheme = remember(shellThemeId) { ShellThemeCatalog.resolve(shellThemeId) }
    val glass = if (darkTheme) darkGlassTokens() else lightGlassTokens()
    val hazeState = rememberHazeState()
    val colorScheme = remember(darkTheme, shellTheme.id) {
        if (darkTheme) {
            shellTheme.toDarkColorScheme()
        } else {
            ArcadiaLightColorScheme
        }
    }
    val typography = remember(uiTextScale) { scaledArcadiaTypography(uiTextScale) }
    val systemDensity = LocalDensity.current
    val layoutScale = uiLayoutScale.coerceIn(0.65f, 1.4f)
    val fittedDensity = remember(systemDensity, layoutScale) {
        Density(
            density = systemDensity.density * layoutScale,
            fontScale = systemDensity.fontScale,
        )
    }
    CompositionLocalProvider(
        LocalArcadiaGlass provides glass,
        LocalArcadiaHaze provides hazeState,
        LocalShellTheme provides shellTheme,
        LocalXoraTextScale provides uiTextScale.coerceIn(0.75f, 1.3f),
        LocalDensity provides fittedDensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

private fun ShellTheme.toDarkColorScheme() = darkColorScheme(
    primary = colors.primary,
    onPrimary = onColorFor(colors.primary),
    primaryContainer = colors.surface,
    onPrimaryContainer = colors.accent,
    secondary = colors.secondary,
    onSecondary = colors.onAccent,
    background = colors.background,
    onBackground = colors.text,
    surface = colors.surface,
    onSurface = colors.text,
    surfaceVariant = colors.surface.lighten(0.08f),
    onSurfaceVariant = colors.textMuted,
    surfaceContainer = colors.surface.lighten(0.04f),
    surfaceContainerHigh = colors.surface.lighten(0.08f),
    surfaceContainerHighest = colors.surface.lighten(0.12f),
    outline = colors.textMuted.copy(alpha = 0.65f),
    outlineVariant = colors.surface.lighten(0.16f),
    error = Danger,
    onError = Ink900,
    tertiary = colors.accent,
    onTertiary = colors.onAccent,
)

private fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.45f) Ink900 else Mist100

private fun Color.lighten(amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = (red + (1f - red) * t).coerceIn(0f, 1f),
        green = (green + (1f - green) * t).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * t).coerceIn(0f, 1f),
        alpha = alpha,
    )
}
