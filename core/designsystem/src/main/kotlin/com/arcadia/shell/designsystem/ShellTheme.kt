package com.arcadia.shell.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Stable string ids for launcher theme packs. Prefer these over ordinals when persisting.
 */
enum class ShellThemeId(val id: String, val displayName: String) {
    Default("default", "Default"),
    UsagiShadePink("usagishade_pink", "Usagi Reload"),
    UsagiShadeDark("usagishade_dark", "UsagiShade (Dark)"),
    ;

    companion object {
        fun fromId(raw: String?): ShellThemeId {
            if (raw.isNullOrBlank()) return Default
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: Default
        }
    }
}

/** How the Home hub paints its full-bleed backdrop when no custom wallpaper file is set. */
enum class ShellWallpaperStyle {
    /** Authored HOME bands drifting over a cyan → white sky (PSP-style flowing wave). */
    XoraFlowWave,
    /** Navy + yellow tartan-inspired geometric field (authored, not ripped art). */
    /** Deep indigo night gradient. */
    /** Soft XMB-like blue wave wash. */
    /** Warm amber arcade glow. */
    /** Glossy rose / sakura field (UsagiShade Pink fallback). */
    UsagiPinkGlow,
    /** Midnight navy with magenta rim light (UsagiShade Dark fallback). */
    UsagiDarkVeil,
    /** Frutiger Aero cyan sky into lime grass (DreamOS fallback). */
}

/**
 * Optional looping soundtrack packaged as an app asset (under `assets/`).
 *
 * [assetPath] is relative to the assets root, e.g. `themes/persona3_reload/bgm.mp3`.
 * When the file is missing at runtime, the shell falls back to the default raw BGM.
 */
@Immutable
data class ShellThemeBgm(
    val assetPath: String,
    val displayHint: String,
)

@Immutable
data class ShellThemeColors(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val textMuted: Color,
    val focusStart: Color,
    val focusEnd: Color,
    val shardFill: Color,
    val shardAccentFocused: Color,
    val shardAccentIdle: Color,
)

@Immutable
data class ShellTheme(
    val id: ShellThemeId,
    val colors: ShellThemeColors,
    val wallpaperStyle: ShellWallpaperStyle,
    /**
     * Optional full-bleed still image under `assets/` (e.g. theme pack wallpaper).
     * When present and readable, Home uses it instead of [wallpaperStyle]; otherwise the
     * style pattern is the fallback.
     */
    val wallpaperAssetPath: String? = null,
    /** Playback rate for a video [wallpaperAssetPath]. Below 1f slows the loop down. */
    val wallpaperPlaybackSpeed: Float = 1f,
    val bgm: ShellThemeBgm? = null,
    val description: String,
    /**
     * Square still for theme grids, cut from [wallpaperAssetPath] at build time — most theme
     * wallpapers are video loops, which a thumbnail cannot draw. Null for the themes whose
     * backdrop is a procedural [wallpaperStyle]; those fall back to a palette swatch.
     */
    @DrawableRes val previewRes: Int? = null,
)

object ShellThemeCatalog {
    val Default: ShellTheme = ShellTheme(
        id = ShellThemeId.Default,
        colors = ShellThemeColors(
            primary = Accent,
            secondary = Signal,
            background = Ink900,
            surface = Ink800,
            accent = AccentBright,
            onAccent = Ink900,
            text = Mist100,
            textMuted = Mist300,
            focusStart = Color(0xFFB8A0F0),
            focusEnd = Color(0xFFF0A8D8),
            shardFill = Color(0xE6121822),
            shardAccentFocused = Color(0xFF7EC8E3),
            shardAccentIdle = Color(0xFF3A5F73),
        ),
        wallpaperStyle = ShellWallpaperStyle.XoraFlowWave,
        wallpaperAssetPath = DEFAULT_WALLPAPER_ASSET,
        wallpaperPlaybackSpeed = DEFAULT_WALLPAPER_SPEED,
        bgm = ShellThemeBgm(
            assetPath = DEFAULT_BGM_ASSET,
            displayHint = "Home menu theme",
        ),
        description = "XOrA flowing blue wave",
        previewRes = R.drawable.theme_preview_default,
    )

    val UsagiShadePink: ShellTheme = ShellTheme(
        id = ShellThemeId.UsagiShadePink,
        colors = ShellThemeColors(
            primary = Color(0xFFFF7EB6),
            secondary = Color(0xFFFFC1DE),
            background = Color(0xFF1A0C14),
            surface = Color(0xFF2A1420),
            accent = Color(0xFFFF9AC8),
            onAccent = Color(0xFF1A0A12),
            text = Color(0xFFFFF4F8),
            textMuted = Color(0xFFE0B0C8),
            focusStart = Color(0xFFFF6AA8),
            focusEnd = Color(0xFFE8B0FF),
            shardFill = Color(0xE81C0E16),
            shardAccentFocused = Color(0xFFFF8AB8),
            shardAccentIdle = Color(0xFF7A4060),
        ),
        wallpaperStyle = ShellWallpaperStyle.UsagiPinkGlow,
        wallpaperAssetPath = USAGISHADE_PINK_WALLPAPER_ASSET,
        bgm = ShellThemeBgm(
            assetPath = USAGISHADE_BGM_ASSET,
            displayHint = "System menu theme",
        ),
        description = "Usagi Reload motion field, glossy rose chrome",
        previewRes = R.drawable.theme_preview_usagishade_pink,
    )

    val UsagiShadeDark: ShellTheme = ShellTheme(
        id = ShellThemeId.UsagiShadeDark,
        colors = ShellThemeColors(
            primary = Color(0xFFE85A9A),
            secondary = Color(0xFFB080C8),
            background = Color(0xFF07070C),
            surface = Color(0xFF121018),
            accent = Color(0xFFFF7EB6),
            onAccent = Color(0xFF10080C),
            text = Color(0xFFF4EEF4),
            textMuted = Color(0xFFA898A8),
            focusStart = Color(0xFF6A3060),
            focusEnd = Color(0xFFE85A9A),
            shardFill = Color(0xE80A0A12),
            shardAccentFocused = Color(0xFFFF8AB8),
            shardAccentIdle = Color(0xFF4A3048),
        ),
        wallpaperStyle = ShellWallpaperStyle.UsagiDarkVeil,
        wallpaperAssetPath = USAGISHADE_DARK_WALLPAPER_ASSET,
        bgm = ShellThemeBgm(
            assetPath = USAGISHADE_BGM_ASSET,
            displayHint = "System menu theme",
        ),
        description = "Dark motion field, magenta rim light",
        previewRes = R.drawable.theme_preview_usagishade_dark,
    )

    val all: List<ShellTheme> = listOf(
        Default,
        UsagiShadePink,
        UsagiShadeDark,
    )

    fun require(id: ShellThemeId): ShellTheme = when (id) {
        ShellThemeId.Default -> Default
        ShellThemeId.UsagiShadePink -> UsagiShadePink
        ShellThemeId.UsagiShadeDark -> UsagiShadeDark
    }

    fun resolve(rawId: String?): ShellTheme = require(ShellThemeId.fromId(rawId))
}

/**
 * Default theme wallpaper (looping video). Shared with the Vita shortcut tray so both surfaces
 * show the same loop. Missing asset falls back to [ShellWallpaperStyle.XoraFlowWave].
 */
const val DEFAULT_WALLPAPER_ASSET = "themes/default/wallpaper.mp4"

/** Playback is 1x: slow-mo is already encoded into the 60 fps loop. */
const val DEFAULT_WALLPAPER_SPEED = 1f

/** Asset path for the default theme looping BGM. */
const val DEFAULT_BGM_ASSET = "themes/default/bgm.mp3"



const val USAGISHADE_BGM_ASSET = "themes/usagishade/bgm.mp3"
const val USAGISHADE_PINK_WALLPAPER_ASSET = "themes/usagishade_pink/wallpaper.mp4"
const val USAGISHADE_DARK_WALLPAPER_ASSET = "themes/usagishade_dark/wallpaper.mp4"

/** Crossfade duration when switching launcher theme backdrops / BGM. */
const val THEME_CROSSFADE_MS = 600

val LocalShellTheme = staticCompositionLocalOf { ShellThemeCatalog.Default }

@Composable
fun currentShellTheme(): ShellTheme = LocalShellTheme.current
