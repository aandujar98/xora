package com.arcadia.shell.datastore

/**
 * Built-in XOrA Emulator preferences (display layouts, bezels, netplay).
 * Applied when launching [com.arcadia.shell.libretro.XoraLibretroActivity].
 */
data class XoraEmulatorSettings(
    /** Nintendo DS (melonDS / melonDS DS) dual-screen arrangement. */
    val ndsScreenLayout: DualScreenLayout = DualScreenLayout.TopBottom,
    /** Pixel gap between DS screens when the core supports it (0–100). */
    val ndsScreenGap: Int = 0,
    /** Nintendo 3DS (Citra / Azahar) dual-screen arrangement. */
    val threeDsScreenLayout: ThreeDsScreenLayout = ThreeDsScreenLayout.TopBottom,
    /**
     * On dual-screen devices, put the top DS/3DS screen on the primary panel and the bottom
     * screen on the secondary panel (forces a stacked core layout, then splits the frame).
     */
    val expandDualDisplay: Boolean = false,
    /** How the framebuffer is fitted inside the panel. */
    val aspectMode: XoraAspectMode = XoraAspectMode.Core,
    /** Cap for [XoraAspectMode.Integer] (1–8). 0 = auto (largest that fits). */
    val integerScale: Int = 0,
    /** Internal render scale for cores that expose a resolution factor (Citra, …). */
    val internalResolution: XoraInternalResolution = XoraInternalResolution.Native,
    /** Draw system bezels / matte around the fitted framebuffer. */
    val bezelsEnabled: Boolean = true,
    /**
     * Keep the pause menu fully opaque and lay the game beside it instead of under it.
     * The previous translucent Compose sheet is what left a milky white wash after submenus.
     */
    val blockOverlayWash: Boolean = true,
    /** Bezel matte opacity (0 = invisible, 1 = solid). */
    val bezelOpacity: Float = 0.88f,
    /** Emulator AudioTrack gain (0 = mute, 1 = full). */
    val audioVolume: Float = 1f,
    /** When true, show Host / Join controls in the emulator pause menu. */
    val netplayEnabled: Boolean = false,
    val netplayNickname: String = "Player",
    /** UDP/TCP listen port for hosting (libretro convention ~55435). */
    val netplayPort: Int = DEFAULT_NETPLAY_PORT,
    /** Prefer spectator mode when joining. */
    val netplaySpectator: Boolean = false,
    /** True = Online (XOrA Network session code). False = Local Wireless (same Wi‑Fi IP:port). */
    val netplayUseRelay: Boolean = false,
    /** Last host address typed in Join (IP or hostname). */
    val netplayHostAddress: String = "",
    /**
     * Preferred physical controller name from [android.view.InputDevice.getName].
     * Blank = accept input from any connected gamepad.
     */
    val preferredControllerName: String = "",
    /**
     * Optional keyCode → Libretro joypad button index remaps.
     * Empty = built-in [com.arcadia.shell.libretro.LibretroPad] defaults.
     */
    val buttonMappings: Map<Int, Int> = emptyMap(),
)

/** Encode [XoraEmulatorSettings.buttonMappings] for DataStore (`keycode:button,…`). */
fun encodeButtonMappings(mappings: Map<Int, Int>): String =
    mappings.entries
        .sortedBy { it.value }
        .joinToString(",") { "${it.key}:${it.value}" }

/** Decode a [encodeButtonMappings] string; invalid tokens are skipped. */
fun decodeButtonMappings(raw: String): Map<Int, Int> {
    if (raw.isBlank()) return emptyMap()
    val out = LinkedHashMap<Int, Int>()
    raw.split(',').forEach { token ->
        val parts = token.split(':')
        if (parts.size != 2) return@forEach
        val key = parts[0].toIntOrNull() ?: return@forEach
        val button = parts[1].toIntOrNull() ?: return@forEach
        if (button in 0..15) out[key] = button
    }
    return out
}

/** How XOrA scales the core framebuffer on screen. */
enum class XoraAspectMode {
    /** Preserve aspect, letterbox (ContentScale.Fit). */
    Core,
    /** Nearest integer multiple of the native resolution that fits. */
    Integer,
    /** Fill the panel (may stretch). */
    Stretch,
}

/** Core-side resolution multiplier when the Liberto core supports it. */
enum class XoraInternalResolution {
    Native,
    Scale2x,
    Scale3x,
    Scale4x,
    Scale5x,
}

/** melonDS-style dual-screen layouts (values map to core option strings). */
enum class DualScreenLayout {
    TopBottom,
    BottomTop,
    LeftRight,
    RightLeft,
    TopOnly,
    BottomOnly,
    HybridTop,
    HybridBottom,
}

/** Citra / Azahar layout option subset. */
enum class ThreeDsScreenLayout {
    TopBottom,
    SideBySide,
    SingleScreen,
    LargeSmall,
}

const val DEFAULT_NETPLAY_PORT = 55435
const val MIN_NETPLAY_PORT = 1024
const val MAX_NETPLAY_PORT = 65535

fun DualScreenLayout.toMelonDsValue(): String = when (this) {
    DualScreenLayout.TopBottom -> "Top/Bottom"
    DualScreenLayout.BottomTop -> "Bottom/Top"
    DualScreenLayout.LeftRight -> "Left/Right"
    DualScreenLayout.RightLeft -> "Right/Left"
    DualScreenLayout.TopOnly -> "Top Only"
    DualScreenLayout.BottomOnly -> "Bottom Only"
    DualScreenLayout.HybridTop -> "Hybrid Top"
    DualScreenLayout.HybridBottom -> "Hybrid Bottom"
}

/** melonDS DS (JesseTG) uses kebab-case layout tokens. */
fun DualScreenLayout.toMelonDsDsValue(): String = when (this) {
    DualScreenLayout.TopBottom -> "top-bottom"
    DualScreenLayout.BottomTop -> "bottom-top"
    DualScreenLayout.LeftRight -> "left-right"
    DualScreenLayout.RightLeft -> "right-left"
    DualScreenLayout.TopOnly -> "top"
    DualScreenLayout.BottomOnly -> "bottom"
    DualScreenLayout.HybridTop -> "hybrid-top"
    DualScreenLayout.HybridBottom -> "hybrid-bottom"
}

fun DualScreenLayout.label(): String = when (this) {
    DualScreenLayout.TopBottom -> "Top / Bottom"
    DualScreenLayout.BottomTop -> "Bottom / Top"
    DualScreenLayout.LeftRight -> "Left / Right"
    DualScreenLayout.RightLeft -> "Right / Left"
    DualScreenLayout.TopOnly -> "Top only"
    DualScreenLayout.BottomOnly -> "Bottom only"
    DualScreenLayout.HybridTop -> "Hybrid top"
    DualScreenLayout.HybridBottom -> "Hybrid bottom"
}

fun ThreeDsScreenLayout.toCitraValue(): String = when (this) {
    ThreeDsScreenLayout.TopBottom -> "Default Top-Bottom Screen"
    ThreeDsScreenLayout.SideBySide -> "Side by Side"
    ThreeDsScreenLayout.SingleScreen -> "Single Screen Only"
    ThreeDsScreenLayout.LargeSmall -> "Large Screen, Small Screen"
}

fun ThreeDsScreenLayout.label(): String = when (this) {
    ThreeDsScreenLayout.TopBottom -> "Top / Bottom"
    ThreeDsScreenLayout.SideBySide -> "Side by side"
    ThreeDsScreenLayout.SingleScreen -> "Single screen"
    ThreeDsScreenLayout.LargeSmall -> "Large + small"
}

fun XoraAspectMode.label(): String = when (this) {
    XoraAspectMode.Core -> "Core (fit)"
    XoraAspectMode.Integer -> "Integer scale"
    XoraAspectMode.Stretch -> "Stretch"
}

fun XoraInternalResolution.label(): String = when (this) {
    XoraInternalResolution.Native -> "Native (1×)"
    XoraInternalResolution.Scale2x -> "2×"
    XoraInternalResolution.Scale3x -> "3×"
    XoraInternalResolution.Scale4x -> "4×"
    XoraInternalResolution.Scale5x -> "5×"
}

fun XoraInternalResolution.toCitraFactor(): String = when (this) {
    XoraInternalResolution.Native -> "1x (Native)"
    XoraInternalResolution.Scale2x -> "2x"
    XoraInternalResolution.Scale3x -> "3x"
    XoraInternalResolution.Scale4x -> "4x"
    XoraInternalResolution.Scale5x -> "5x"
}

fun XoraInternalResolution.factor(): Int = when (this) {
    XoraInternalResolution.Native -> 1
    XoraInternalResolution.Scale2x -> 2
    XoraInternalResolution.Scale3x -> 3
    XoraInternalResolution.Scale4x -> 4
    XoraInternalResolution.Scale5x -> 5
}
