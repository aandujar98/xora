package com.arcadia.shell.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Stable string ids for launcher theme packs. Prefer these over ordinals when persisting.
 */
enum class ShellThemeId(val id: String, val displayName: String) {
    Default("default", "Default"),
    Persona3Reload("persona3_reload", "Persona 3 Reload"),
    Midnight("midnight", "Midnight"),
    ClassicXmb("classic_xmb", "Classic XMB"),
    WarmArcade("warm_arcade", "Warm Arcade"),
    UsagiShadePink("usagishade_pink", "UsagiShade (Pink)"),
    UsagiShadeDark("usagishade_dark", "UsagiShade (Dark)"),
    DreamOs("dreamos", "DreamOS"),
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
    Persona3Tartan,
    /** Deep indigo night gradient. */
    MidnightGradient,
    /** Soft XMB-like blue wave wash. */
    ClassicXmbWave,
    /** Warm amber arcade glow. */
    WarmArcadeGlow,
    /** Glossy rose / sakura field (UsagiShade Pink fallback). */
    UsagiPinkGlow,
    /** Midnight navy with magenta rim light (UsagiShade Dark fallback). */
    UsagiDarkVeil,
    /** Frutiger Aero cyan sky into lime grass (DreamOS fallback). */
    DreamOsSky,
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
        bgm = null,
        description = "XOrA flowing blue wave",
    )

    val Persona3Reload: ShellTheme = ShellTheme(
        id = ShellThemeId.Persona3Reload,
        colors = ShellThemeColors(
            primary = Color(0xFF1B3A6B),
            secondary = Color(0xFFE8C547),
            background = Color(0xFF07101F),
            surface = Color(0xFF0E1A30),
            accent = Color(0xFFF0D060),
            onAccent = Color(0xFF0A1424),
            text = Color(0xFFF4F0E6),
            textMuted = Color(0xFFA8B4C8),
            focusStart = Color(0xFF1E4A8C),
            focusEnd = Color(0xFFE8C547),
            shardFill = Color(0xE80A1428),
            shardAccentFocused = Color(0xFFF0D060),
            shardAccentIdle = Color(0xFF3A5080),
        ),
        wallpaperStyle = ShellWallpaperStyle.Persona3Tartan,
        wallpaperAssetPath = PERSONA3_WALLPAPER_ASSET,
        bgm = ShellThemeBgm(
            assetPath = PERSONA3_BGM_ASSET,
            displayHint = "Title screen theme",
        ),
        description = "Makoto underwater art, navy & gold shell",
    )

    val Midnight: ShellTheme = ShellTheme(
        id = ShellThemeId.Midnight,
        colors = ShellThemeColors(
            primary = Color(0xFF5B7CFF),
            secondary = Color(0xFF9B8CFF),
            background = Color(0xFF05060C),
            surface = Color(0xFF0C0E18),
            accent = Color(0xFF7A9BFF),
            onAccent = Color(0xFF060810),
            text = Color(0xFFE8ECF8),
            textMuted = Color(0xFF8A94B0),
            focusStart = Color(0xFF3A4A8C),
            focusEnd = Color(0xFF6A5ACD),
            shardFill = Color(0xE6080A14),
            shardAccentFocused = Color(0xFF8AA4FF),
            shardAccentIdle = Color(0xFF3A4468),
        ),
        wallpaperStyle = ShellWallpaperStyle.MidnightGradient,
        bgm = null,
        description = "Deep night indigo",
    )

    val ClassicXmb: ShellTheme = ShellTheme(
        id = ShellThemeId.ClassicXmb,
        colors = ShellThemeColors(
            primary = Color(0xFF2E8BC9),
            secondary = Color(0xFF5EC8E8),
            background = Color(0xFF061820),
            surface = Color(0xFF0A2430),
            accent = Color(0xFF6ED4F0),
            onAccent = Color(0xFF041018),
            text = Color(0xFFF2F8FC),
            textMuted = Color(0xFF9BB8C8),
            focusStart = Color(0xFF1A6A9A),
            focusEnd = Color(0xFF5EC8E8),
            shardFill = Color(0xE80A2030),
            shardAccentFocused = Color(0xFF7AD8F0),
            shardAccentIdle = Color(0xFF2A5870),
        ),
        wallpaperStyle = ShellWallpaperStyle.ClassicXmbWave,
        bgm = null,
        description = "Cross-media bar blue wave",
    )

    val WarmArcade: ShellTheme = ShellTheme(
        id = ShellThemeId.WarmArcade,
        colors = ShellThemeColors(
            primary = Color(0xFFE07A3A),
            secondary = Color(0xFFF0B060),
            background = Color(0xFF140C08),
            surface = Color(0xFF1E140E),
            accent = Color(0xFFFFB040),
            onAccent = Color(0xFF1A0C06),
            text = Color(0xFFFFF4E8),
            textMuted = Color(0xFFC8A888),
            focusStart = Color(0xFFC45A28),
            focusEnd = Color(0xFFF0A040),
            shardFill = Color(0xE81A100A),
            shardAccentFocused = Color(0xFFFFB050),
            shardAccentIdle = Color(0xFF6A4830),
        ),
        wallpaperStyle = ShellWallpaperStyle.WarmArcadeGlow,
        bgm = null,
        description = "Cabinet amber glow",
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
        description = "Pink motion field, glossy rose chrome",
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
    )

    val DreamOs: ShellTheme = ShellTheme(
        id = ShellThemeId.DreamOs,
        colors = ShellThemeColors(
            primary = Color(0xFF3DB8E8),
            secondary = Color(0xFF7AE08A),
            background = Color(0xFF0A3048),
            surface = Color(0xFF124058),
            accent = Color(0xFF7AE0C8),
            onAccent = Color(0xFF062030),
            text = Color(0xFFF4FCFF),
            textMuted = Color(0xFFB0D0E0),
            focusStart = Color(0xFF2A90C8),
            focusEnd = Color(0xFF80E0A0),
            shardFill = Color(0xE8103044),
            shardAccentFocused = Color(0xFF6ED8F0),
            shardAccentIdle = Color(0xFF2A6078),
        ),
        wallpaperStyle = ShellWallpaperStyle.DreamOsSky,
        wallpaperAssetPath = DREAMOS_WALLPAPER_ASSET,
        bgm = ShellThemeBgm(
            assetPath = DREAMOS_BGM_ASSET,
            displayHint = "Distant ocean",
        ),
        description = "Frutiger Aero sky, grass, and glass dew",
    )

    val all: List<ShellTheme> = listOf(
        Default,
        Persona3Reload,
        Midnight,
        ClassicXmb,
        WarmArcade,
        UsagiShadePink,
        UsagiShadeDark,
        DreamOs,
    )

    fun require(id: ShellThemeId): ShellTheme = when (id) {
        ShellThemeId.Default -> Default
        ShellThemeId.Persona3Reload -> Persona3Reload
        ShellThemeId.Midnight -> Midnight
        ShellThemeId.ClassicXmb -> ClassicXmb
        ShellThemeId.WarmArcade -> WarmArcade
        ShellThemeId.UsagiShadePink -> UsagiShadePink
        ShellThemeId.UsagiShadeDark -> UsagiShadeDark
        ShellThemeId.DreamOs -> DreamOs
    }

    fun resolve(rawId: String?): ShellTheme = require(ShellThemeId.fromId(rawId))
}

/**
 * Default theme wallpaper (looping video). Shared with the Vita shortcut tray so both surfaces
 * show the same loop. Missing asset falls back to [ShellWallpaperStyle.XoraFlowWave].
 */
const val DEFAULT_WALLPAPER_ASSET = "themes/default/wallpaper.mp4"

/** The authored loop reads better a touch under real time. */
const val DEFAULT_WALLPAPER_SPEED = 0.5f

/** Asset path for Persona 3 Reload theme BGM. */
const val PERSONA3_BGM_ASSET = "themes/persona3_reload/bgm.mp3"

/** Asset path for Persona 3 Reload full-bleed wallpaper (looping video). */
const val PERSONA3_WALLPAPER_ASSET = "themes/persona3_reload/wallpaper.mp4"

const val USAGISHADE_BGM_ASSET = "themes/usagishade/bgm.mp3"
const val USAGISHADE_PINK_WALLPAPER_ASSET = "themes/usagishade_pink/wallpaper.mp4"
const val USAGISHADE_DARK_WALLPAPER_ASSET = "themes/usagishade_dark/wallpaper.mp4"
const val DREAMOS_BGM_ASSET = "themes/dreamos/bgm.mp3"
const val DREAMOS_WALLPAPER_ASSET = "themes/dreamos/wallpaper.jpg"

/** Crossfade duration when switching launcher theme backdrops / BGM. */
const val THEME_CROSSFADE_MS = 600

val LocalShellTheme = staticCompositionLocalOf { ShellThemeCatalog.Default }

@Composable
fun currentShellTheme(): ShellTheme = LocalShellTheme.current
