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
     * Defaults on — only takes effect when a second display is actually present.
     */
    val expandDualDisplay: Boolean = true,
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
     * Nintendo DS Nintendo WFC replacement. melonDS talks to Kaeru / Wiimmfi / AltWFC
     * when this is not [NdsWfcServer.Off]; the in-game Nintendo Wi-Fi Connection menu
     * is the matchmaking UI (Mario Kart DS, …).
     */
    val ndsWfcServer: NdsWfcServer = NdsWfcServer.Kaeru,
    /** Used when [ndsWfcServer] is [NdsWfcServer.Custom]. */
    val ndsWfcCustomDns: String = "",
    /**
     * Azahar / Citra-style public room list (`GET {url}/lobby`). Blank = try the
     * built-in community endpoints. Libretro Azahar cannot join those rooms.
     */
    val azaharLobbyApiUrl: String = "",
    /**
     * Kept for older installs. Does not put Pretendo on XOrA Emulator — Azahar libretro
     * has no Nimbus / LLE-online / plugin-loader options. Prefer standalone Azahar.
     */
    val threeDsPretendoPrep: Boolean = false,
    /**
     * PPSSPP WLAN / Pro AdHoc. Games use their own infrastructure menu; XOrA Host/Join
     * points joiners at the host IP and runs the built-in AdHoc server on the host.
     */
    val pspAdhocEnabled: Boolean = true,
    /** When not in an XOrA lobby, run PPSSPP's built-in Pro AdHoc server on this device. */
    val pspAdhocIsServer: Boolean = false,
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

/** How XOrA scales the core framebuffer on screen. [Core] is stored as Auto. */
enum class XoraAspectMode {
    /** Auto: preserve the core framebuffer aspect (letterbox). */
    Core,
    /** Classic 4:3 television. */
    Ratio4x3,
    /** SNES / many handhelds (~1.14:1). */
    Ratio8x7,
    /** 3:2 (GBA, many 8-bit computers). */
    Ratio3x2,
    /** Square. */
    Ratio1x1,
    /** 16:10 laptop / some handhelds. */
    Ratio16x10,
    /** Widescreen 16:9. */
    Ratio16x9,
    /** Ultrawide 21:9. */
    Ratio21x9,
    /** Nearest integer multiple of the native resolution that fits. */
    Integer,
    /** Fill the panel (may stretch). */
    Stretch,
}

fun XoraAspectMode.next(): XoraAspectMode {
    val all = XoraAspectMode.entries
    return all[(ordinal + 1) % all.size]
}

/** Forced width/height, or null to use the framebuffer (Auto) / integer / stretch. */
fun XoraAspectMode.forcedRatio(): Float? = when (this) {
    XoraAspectMode.Core, XoraAspectMode.Integer, XoraAspectMode.Stretch -> null
    XoraAspectMode.Ratio4x3 -> 4f / 3f
    XoraAspectMode.Ratio8x7 -> 8f / 7f
    XoraAspectMode.Ratio3x2 -> 3f / 2f
    XoraAspectMode.Ratio1x1 -> 1f
    XoraAspectMode.Ratio16x10 -> 16f / 10f
    XoraAspectMode.Ratio16x9 -> 16f / 9f
    XoraAspectMode.Ratio21x9 -> 21f / 9f
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

/** Fan-run Nintendo WFC DNS targets understood by melonDS / melonDS DS. */
enum class NdsWfcServer {
    Off,
    Kaeru,
    Wiimmfi,
    AltWfc,
    Custom,
}

const val FIRMWARE_DEFAULT_WFC_DNS = "0.0.0.0"
const val KAERU_WFC_DNS = "178.62.43.212"
const val WIIMMFI_WFC_DNS = "95.217.77.181"
const val ALTWFC_DNS = "172.104.88.237"

fun NdsWfcServer.label(): String = when (this) {
    NdsWfcServer.Off -> "Off"
    NdsWfcServer.Kaeru -> "Kaeru WFC"
    NdsWfcServer.Wiimmfi -> "Wiimmfi"
    NdsWfcServer.AltWfc -> "AltWFC"
    NdsWfcServer.Custom -> "Custom DNS"
}

/** Overlay cycle skips Custom (that DNS is typed in Settings). */
fun NdsWfcServer.nextPublic(): NdsWfcServer {
    val cycle = listOf(
        NdsWfcServer.Kaeru,
        NdsWfcServer.Wiimmfi,
        NdsWfcServer.AltWfc,
        NdsWfcServer.Off,
    )
    val index = cycle.indexOf(this)
    return if (index < 0) cycle.first() else cycle[(index + 1) % cycle.size]
}

fun NdsWfcServer.dns(custom: String = ""): String = when (this) {
    NdsWfcServer.Off -> FIRMWARE_DEFAULT_WFC_DNS
    NdsWfcServer.Kaeru -> KAERU_WFC_DNS
    NdsWfcServer.Wiimmfi -> WIIMMFI_WFC_DNS
    NdsWfcServer.AltWfc -> ALTWFC_DNS
    NdsWfcServer.Custom -> custom.trim().ifBlank { KAERU_WFC_DNS }
}

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
    XoraAspectMode.Core -> "Auto"
    XoraAspectMode.Ratio4x3 -> "4:3"
    XoraAspectMode.Ratio8x7 -> "8:7"
    XoraAspectMode.Ratio3x2 -> "3:2"
    XoraAspectMode.Ratio1x1 -> "1:1"
    XoraAspectMode.Ratio16x10 -> "16:10"
    XoraAspectMode.Ratio16x9 -> "16:9"
    XoraAspectMode.Ratio21x9 -> "21:9"
    XoraAspectMode.Integer -> "Integer scale"
    XoraAspectMode.Stretch -> "Full screen"
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

/** A friend's online netplay invite waiting for this device to join. */
data class PendingNetplayJoin(
    val code: String = "",
    val platformId: String = "",
    val gameTitle: String = "",
    val fromUsername: String = "",
    val coreName: String = "",
    val createdAtMs: Long = 0,
) {
    val isPresent: Boolean get() = code.isNotBlank()

    fun isActive(nowMs: Long, ttlMs: Long = PENDING_NETPLAY_JOIN_TTL_MS): Boolean {
        if (!isPresent) return false
        if (createdAtMs <= 0L) return true
        return nowMs - createdAtMs in 0 until ttlMs
    }
}

const val PENDING_NETPLAY_JOIN_TTL_MS = 15L * 60L * 1_000L

