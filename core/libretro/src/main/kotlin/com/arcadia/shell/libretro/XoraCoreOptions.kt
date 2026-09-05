package com.arcadia.shell.libretro

import com.arcadia.shell.datastore.DualScreenLayout
import com.arcadia.shell.datastore.NdsWfcServer
import com.arcadia.shell.datastore.dns
import com.arcadia.shell.datastore.ThreeDsScreenLayout
import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.toCitraFactor
import com.arcadia.shell.datastore.toCitraValue
import com.arcadia.shell.datastore.toMelonDsDsValue
import com.arcadia.shell.datastore.toMelonDsValue
import com.arcadia.shell.libretro.netplay.AzaharPretendo
import java.security.MessageDigest

/**
 * Maps XOrA Emulator settings to Libretro core option key/value pairs
 * applied before [LibretroNative.nativeLoadGame].
 */
object XoraCoreOptions {

    /** Live XOrA lobby, used to point PPSSPP AdHoc at the host. */
    data class NetplayContext(
        val hosting: Boolean = false,
        val joining: Boolean = false,
        val hostAddress: String = "",
    )

    fun variablesFor(
        platformId: String,
        coreName: String,
        settings: XoraEmulatorSettings,
        expandActive: Boolean = false,
        netplay: NetplayContext = NetplayContext(),
        romPath: String = "",
    ): Map<String, String> {
        val out = linkedMapOf<String, String>()
        when (platformId) {
            "nds" -> applyNds(coreName, settings, expandActive, romPath, out)
            "3ds" -> apply3ds(coreName, settings, expandActive, out)
        }
        applyResolution(platformId, coreName, settings, out)
        applyNes(platformId, coreName, out)
        applySnes(platformId, coreName, out)
        applyN64(platformId, coreName, out)
        applyPs1(platformId, coreName, out)
        applyGameCube(platformId, coreName, out)
        applyGenesis(platformId, coreName, out)
        applySaturn(platformId, coreName, out)
        applyDreamcast(platformId, coreName, out)
        applyPcEngine(platformId, coreName, out)
        applyAtari2600(platformId, coreName, out)
        applyThreeDo(platformId, coreName, out)
        applyAmiga(platformId, coreName, out)
        applyGba(platformId, coreName, out)
        applyPsp(platformId, coreName, settings, netplay, out)
        // Last so per-core defaults (N64 4:3, etc.) cannot undo the player's ratio.
        applyAspect(platformId, coreName, settings, out)
        return out
    }

    private fun applyGba(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "gba" && !coreName.contains("gpsp", ignoreCase = true)) return
        // auto leaves Kirby / Mario Kart with serial disabled (gba_over flags = 0).
        // mul_poke is gpSP's generic 2–4 player Game Link. Interpreter so SIO writes
        // hit write_siocnt instead of a stale DRC block.
        out["gpsp_serial"] = "mul_poke"
        out["gpsp_drc"] = "disabled"
    }

    private fun applyNes(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "nes" &&
            !coreName.contains("fceumm", ignoreCase = true) &&
            !coreName.contains("nestopia", ignoreCase = true) &&
            !coreName.contains("mesen", ignoreCase = true)
        ) {
            return
        }
        out["mesen_port1type"] = "Standard Controller"
        out["mesen_port2type"] = "Standard Controller"
        out["mesen_port3type"] = "Standard Controller"
        out["mesen_port4type"] = "Standard Controller"
        // Four Score turns port 2 into an adapter; P2 netplay then never sees a pad.
        out["nestopia_select_adapter"] = "disabled"
    }

    private fun applySnes(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "snes" &&
            !coreName.contains("snes9x", ignoreCase = true) &&
            !coreName.contains("bsnes", ignoreCase = true) &&
            !coreName.contains("mesen-s", ignoreCase = true)
        ) {
            return
        }
        out["bsnes_port_1"] = "Gamepad"
        out["bsnes_port_2"] = "Gamepad"
        out["mesen-s_port1type"] = "Standard Controller"
        out["mesen-s_port2type"] = "Standard Controller"
        out["mesen-s_port3type"] = "Standard Controller"
        out["mesen-s_port4type"] = "Standard Controller"
    }

    private fun applyN64(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "n64" &&
            !coreName.contains("mupen", ignoreCase = true) &&
            !coreName.contains("parallel_n64", ignoreCase = true)
        ) {
            return
        }
        // Offscreen EGL host is single-threaded; Mupen's GL worker crashes without a shared context.
        out["mupen64plus-ThreadedRenderer"] = "False"
        out["mupen64plus-rdp-plugin"] = "gliden64"
        out["mupen64plus-rsp-plugin"] = "hle"
        // Keep the PBuffer FBO modest — high internal res + glReadPixels OOMs weak GPUs.
        out["mupen64plus-43screensize"] = "640x480"
        out["mupen64plus-aspect"] = "4:3"
        out["mupen64plus-EnableNativeResFactor"] = "0"
        out["mupen64plus-FrameDuping"] = "False"
        out["mupen64plus-pak1"] = "memory"
        out["mupen64plus-pak2"] = "memory"
        out["mupen64plus-pak3"] = "memory"
        out["mupen64plus-pak4"] = "memory"
        out["parallel-n64-pak1"] = "memory"
        out["parallel-n64-pak2"] = "memory"
        out["parallel-n64-pak3"] = "memory"
        out["parallel-n64-pak4"] = "memory"
        // ParaLLEl: software RDP avoids Vulkan (unsupported by the XOrA GLES host).
        out["parallel-n64-gfx"] = "angrylion"
    }

    /**
     * Connect P1–P4 on PlayStation cores. SwanStation/DuckStation default extra
     * ports to None. Do **not** enable a multitap on port 2 — that replaces the
     * P2 DualShock with an adapter, so the joiner's pad never reaches the game.
     */
    private fun applyPs1(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "ps1" &&
            !coreName.contains("pcsx", ignoreCase = true) &&
            !coreName.contains("swanstation", ignoreCase = true) &&
            !coreName.contains("duckstation", ignoreCase = true) &&
            !coreName.contains("psx", ignoreCase = true)
        ) {
            return
        }
        out["pcsx_rearmed_pad1type"] = "analog"
        out["pcsx_rearmed_pad2type"] = "analog"
        out["pcsx_rearmed_pad3type"] = "analog"
        out["pcsx_rearmed_pad4type"] = "analog"
        out["pcsx_rearmed_multitap"] = "disabled"
        out["duckstation_Controller1.Type"] = "AnalogController"
        out["duckstation_Controller2.Type"] = "AnalogController"
        out["duckstation_Controller3.Type"] = "AnalogController"
        out["duckstation_Controller4.Type"] = "AnalogController"
        out["swanstation_Controller1.Type"] = "AnalogController"
        out["swanstation_Controller2.Type"] = "AnalogController"
        out["swanstation_Controller3.Type"] = "AnalogController"
        out["swanstation_Controller4.Type"] = "AnalogController"
        out["swanstation_Controller1_ForceAnalog"] = "true"
        out["swanstation_Controller2_ForceAnalog"] = "true"
        out["swanstation_Controller3_ForceAnalog"] = "true"
        out["swanstation_Controller4_ForceAnalog"] = "true"
        out["duckstation_Controller1_ForceAnalog"] = "true"
        out["duckstation_Controller2_ForceAnalog"] = "true"
        out["duckstation_Controller3_ForceAnalog"] = "true"
        out["duckstation_Controller4_ForceAnalog"] = "true"
        out["duckstation_Multitap.Mode"] = "Off"
        out["swanstation_Multitap.Mode"] = "Off"
        out["duckstation_ControllerPorts.MultitapMode"] = "Off"
        out["swanstation_ControllerPorts.MultitapMode"] = "Off"
        out["beetle_psx_pad1type"] = "analog"
        out["beetle_psx_pad2type"] = "analog"
        out["beetle_psx_pad3type"] = "analog"
        out["beetle_psx_pad4type"] = "analog"
        out["beetle_psx_hw_pad1type"] = "analog"
        out["beetle_psx_hw_pad2type"] = "analog"
        out["beetle_psx_hw_pad3type"] = "analog"
        out["beetle_psx_hw_pad4type"] = "analog"
    }

    /** Keep GameCube pads on ports 1–2 (not Wii 5–8) so the joiner is P2. */
    private fun applyGameCube(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "gamecube" &&
            platformId != "wii" &&
            !coreName.contains("dolphin", ignoreCase = true)
        ) {
            return
        }
        out["dolphin_alt_gc_ports_on_wii"] = "OFF"
        out["dolphin_port_1_type"] = "Standard Controller"
        out["dolphin_port_2_type"] = "Standard Controller"
        out["dolphin_port_3_type"] = "Standard Controller"
        out["dolphin_port_4_type"] = "Standard Controller"
    }

    /** Genesis / Mega Drive: 6-button pads on both ports. Team Player steals P2. */
    private fun applyGenesis(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "genesis" &&
            platformId != "megadrive" &&
            platformId != "sega32x" &&
            platformId != "segacd" &&
            platformId != "mastersystem" &&
            !coreName.contains("genesis_plus", ignoreCase = true) &&
            !coreName.contains("picodrive", ignoreCase = true)
        ) {
            return
        }
        out["picodrive_input1"] = "6 button pad"
        out["picodrive_input2"] = "6 button pad"
    }

    /** Saturn: keep port 2 as a pad. The 6-player adapter replaces P2. */
    private fun applySaturn(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "saturn" &&
            !coreName.contains("saturn", ignoreCase = true) &&
            !coreName.contains("yabause", ignoreCase = true) &&
            !coreName.contains("kronos", ignoreCase = true)
        ) {
            return
        }
        out["beetle_saturn_multitap_port1"] = "disabled"
        out["beetle_saturn_multitap_port2"] = "disabled"
        out["mednafen_saturn_multitap_port1"] = "disabled"
        out["mednafen_saturn_multitap_port2"] = "disabled"
        out["kronos_multitap_port1"] = "disabled"
        out["kronos_multitap_port2"] = "disabled"
    }

    /** PC Engine: a 5-player adapter replaces P2 the same way a SNES multitap would. */
    private fun applyPcEngine(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "pcengine" &&
            platformId != "tg16" &&
            !coreName.contains("pce", ignoreCase = true) &&
            !coreName.contains("sgx", ignoreCase = true) &&
            !coreName.contains("supergrafx", ignoreCase = true)
        ) {
            return
        }
        out["pce_multitap"] = "disabled"
        out["pce_fast_multitap"] = "disabled"
        out["sgx_multitap"] = "disabled"
    }

    /** Stella defaults extra ports off; both joysticks must be plugged for P2. */
    private fun applyAtari2600(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "atari2600" && !coreName.contains("stella", ignoreCase = true)) return
        out["stella_controller1"] = "Joystick"
        out["stella_controller2"] = "Joystick"
    }

    /** Opera only emulates as many 3DO pads as this count. Default is often 1. */
    private fun applyThreeDo(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "3do" && !coreName.contains("opera", ignoreCase = true)) return
        out["opera_active_devices"] = "4"
    }

    /** PUAE: both joystick ports as RetroPads so the joiner is P2. */
    private fun applyAmiga(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "amiga" && !coreName.contains("puae", ignoreCase = true)) return
        out["puae_joyport"] = "joystick"
        out["puae_joyport1"] = "RetroPad"
        out["puae_joyport2"] = "RetroPad"
    }

    /** Dreamcast always has four maple ports — keep them as standard controllers. */
    private fun applyDreamcast(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "dreamcast" &&
            !coreName.contains("flycast", ignoreCase = true) &&
            !coreName.contains("reicast", ignoreCase = true)
        ) {
            return
        }
        out["reicast_device1"] = "Gamepad"
        out["reicast_device2"] = "Gamepad"
        out["reicast_device3"] = "Gamepad"
        out["reicast_device4"] = "Gamepad"
        out["flycast_device1"] = "Gamepad"
        out["flycast_device2"] = "Gamepad"
        out["flycast_device3"] = "Gamepad"
        out["flycast_device4"] = "Gamepad"
    }

    private fun applyNds(
        coreName: String,
        settings: XoraEmulatorSettings,
        expandActive: Boolean,
        romPath: String,
        out: MutableMap<String, String>,
    ) {
        // Expand splits whatever packed frame the core emits. Forcing Top/Bottom here
        // is what left MelonDS DS stuck on stacked after the player picked Left/Right.
        val layout = settings.ndsScreenLayout
        val gap = if (expandActive) 0 else settings.ndsScreenGap.coerceIn(0, 100)
        val melon = layout.toMelonDsValue()
        val melonDs = layout.toMelonDsDsValue()
        val melonDsDsCore = coreName.contains("melondsds", ignoreCase = true) ||
            coreName.contains("melonds_ds", ignoreCase = true)

        out["melonds_screen_layout"] = melon
        out["melonds_screen_gap"] = gap.toString()
        out["melonds_ds_screen_gap"] = gap.toString()
        // melonDS DS keys are `melonds_screen_layout1` (not `melonds_ds_*`).
        out["melonds_screen_layout1"] = melonDs
        out["melonds_ds_screen_layout1"] = melonDs
        out["melonds_number_of_screen_layouts"] = "1"
        out["melonds_ds_number_of_screen_layouts"] = "1"
        out["melonds_secondary_screen_scale"] = "100"
        out["melonds_ds_secondary_screen_scale"] = "100"
        // Legacy melonDS matches "Touch"; melonDS DS matches kebab-case "touch".
        out["melonds_touch_mode"] = if (melonDsDsCore) "touch" else "Touch"
        out["melonds_ds_touch_mode"] = "touch"

        applyNdsConsoleMode(coreName, romPath, out)
        applyNdsWfc(settings, out)

        // Always write DeSmuME keys — unused keys are ignored by melonDS.
        out["desmume_screens_layout"] = when (layout) {
            DualScreenLayout.LeftRight, DualScreenLayout.RightLeft -> "left/right"
            DualScreenLayout.TopOnly -> "top only"
            DualScreenLayout.BottomOnly -> "bottom only"
            else -> "top/bottom"
        }
        out["desmume_screens_gap"] = gap.toString()
    }

    private fun apply3ds(
        coreName: String,
        settings: XoraEmulatorSettings,
        @Suppress("UNUSED_PARAMETER")
        expandActive: Boolean,
        out: MutableMap<String, String>,
    ) {
        val layout = settings.threeDsScreenLayout
        val citra = layout.toCitraValue()
        val panda = when (layout) {
            ThreeDsScreenLayout.SideBySide -> "side_by_side"
            ThreeDsScreenLayout.SingleScreen -> "single"
            else -> "top_bottom"
        }
        out["citra_layout_option"] = citra
        out["azahar_layout_option"] = citra
        out["citra2018_layout_option"] = citra
        out["lime3ds_layout_option"] = citra
        out["citra_swap_screen"] = "disabled"
        out["azahar_swap_screen"] = "disabled"
        out["citra_custom_layout"] = "disabled"
        out["azahar_custom_layout"] = "disabled"
        out["panda3ds_layout"] = panda
        out["panda3ds_screen_layout"] = panda
        // Android Azahar defaults to Vulkan and ignores GET_PREFERRED_HW_RENDER.
        // XOrA only implements GLES SET_HW_RENDER, so Auto/Vulkan prints
        // "Failed to set HW renderer" and aborts load. Force the GLES3 path.
        out["citra_graphics_api"] = "OpenGL"
        out["azahar_graphics_api"] = "OpenGL"
        if (settings.threeDsPretendoPrep) {
            out.putAll(AzaharPretendo.coreOptions())
        }
    }

    private fun applyResolution(
        platformId: String,
        coreName: String,
        settings: XoraEmulatorSettings,
        out: MutableMap<String, String>,
    ) {
        val factor = settings.internalResolution.toCitraFactor()
        if (platformId == "3ds" || coreName.contains("citra", ignoreCase = true) ||
            coreName.contains("azahar", ignoreCase = true)
        ) {
            out["citra_resolution_factor"] = factor
            out["azahar_resolution_factor"] = factor
        }
        // melonDS software renderer ignores most scale factors; leave layout-driven.
    }

    /**
     * Some cores already letterbox inside a frontend-sized framebuffer. The stage then
     * letterboxes that whole plate (it looks like the XMB/frontend ratio changed, not
     * the game). Push the same ratio into the core so the pixels themselves change.
     */
    private fun applyAspect(
        platformId: String,
        coreName: String,
        settings: XoraEmulatorSettings,
        out: MutableMap<String, String>,
    ) {
        val mode = settings.aspectMode
        val wide = mode == XoraAspectMode.Ratio16x9 ||
            mode == XoraAspectMode.Ratio16x10 ||
            mode == XoraAspectMode.Ratio21x9 ||
            mode == XoraAspectMode.Stretch
        val fourByThree = mode == XoraAspectMode.Ratio4x3 ||
            mode == XoraAspectMode.Ratio8x7 ||
            mode == XoraAspectMode.Ratio3x2
        val n64 = when {
            wide -> "16:9"
            fourByThree -> "4:3"
            else -> "adjust"
        }
        out["mupen64plus-aspect"] = n64
        out["parallel-n64-aspectratio"] = n64
        out["reicast_widescreen_hack"] = if (wide) "enabled" else "disabled"
        out["flycast_widescreen_hack"] = if (wide) "enabled" else "disabled"
        out["dolphin_widescreen_hack"] = if (wide) "enabled" else "disabled"
        when {
            platformId == "psp" || coreName.contains("ppsspp", ignoreCase = true) -> {
                out["ppsspp_aspect"] = when {
                    mode == XoraAspectMode.Stretch -> "stretched"
                    fourByThree -> "4:3"
                    else -> "16:9"
                }
            }
            platformId == "ps1" || coreName.contains("pcsx", ignoreCase = true) ||
                coreName.contains("swanstation", ignoreCase = true) ||
                coreName.contains("duckstation", ignoreCase = true) -> {
                val psx = when {
                    wide -> "16:9"
                    fourByThree -> "4:3"
                    else -> "auto"
                }
                out["duckstation_Display.AspectRatio"] = psx
                out["swanstation_Display.AspectRatio"] = psx
                out["beetle_psx_aspect_ratio"] = psx
                out["pcsx_rearmed_neon_enhancement_enable"] = "disabled"
            }
        }
    }

    /**
     * Regular DS carts must not boot in DSi mode — melonDS DS then demands a NAND dump
     * and shows the watermelon error. `.dsi` / DSiWare keep DSi so NAND users still work.
     *
     * Built-in BIOS + direct boot so a handheld without dumps can play Mario Kart DS.
     */
    private fun applyNdsConsoleMode(
        coreName: String,
        romPath: String,
        out: MutableMap<String, String>,
    ) {
        val dsi = ndsRomWantsDsiMode(romPath)
        val melonDsDs = coreName.contains("melondsds", ignoreCase = true) ||
            coreName.contains("melonds_ds", ignoreCase = true)
        out["melonds_console_mode"] = when {
            dsi && melonDsDs -> "dsi"
            dsi -> "DSi"
            melonDsDs -> "ds"
            else -> "DS"
        }
        out["melonds_ds_console_mode"] = if (dsi) "dsi" else "ds"
        out["melonds_sysfile_mode"] = "builtin"
        out["melonds_ds_sysfile_mode"] = "builtin"
        out["melonds_boot_mode"] = "direct"
        out["melonds_ds_boot_mode"] = "direct"
        out["melonds_boot_directly"] = "enabled"
    }

    /** True for DSiWare / DSi-exclusive dumps. Regular `.nds` carts stay in DS mode. */
    internal fun ndsRomWantsDsiMode(romPath: String): Boolean {
        val ext = romPath.substringAfterLast('.', "").lowercase()
        return ext == "dsi"
    }

    /**
     * melonDS DS (`melonds_firmware_wfc_dns`) plus the older melonDS prefix. Unused
     * keys are ignored by the loaded core.
     */
    private fun applyNdsWfc(
        settings: XoraEmulatorSettings,
        out: MutableMap<String, String>,
    ) {
        val off = settings.ndsWfcServer == NdsWfcServer.Off
        val dns = settings.ndsWfcServer.dns(settings.ndsWfcCustomDns)
        out["melonds_network_mode"] = if (off) "Disabled" else "Indirect"
        out["melonds_ds_network_mode"] = if (off) "disabled" else "indirect"
        out["melonds_firmware_wfc_dns"] = dns
        out["melonds_ds_firmware_wfc_dns"] = dns
        out["melonds_mac_address_mode"] = if (off) "firmware" else "from-username"
        out["melonds_ds_mac_address_mode"] = if (off) "firmware" else "from-username"
    }

    /**
     * PPSSPP AdHoc is not RetroArch netplay. Each phone runs PPSSPP; WLAN + the
     * built-in Pro AdHoc server (host) / host IP (joiners) is how Mario Kart / SOCOM
     * see other players.
     */
    private fun applyPsp(
        platformId: String,
        coreName: String,
        settings: XoraEmulatorSettings,
        netplay: NetplayContext,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "psp" && !coreName.contains("ppsspp", ignoreCase = true)) return
        val inLobby = netplay.hosting || netplay.joining
        if (!settings.pspAdhocEnabled && !inLobby) return
        out["ppsspp_enable_wlan"] = "enabled"
        val runServer = netplay.hosting || (!netplay.joining && settings.pspAdhocIsServer)
        val server = when {
            runServer -> "localhost"
            else -> adhocHostAddress(netplay.hostAddress, settings.netplayHostAddress)
        }
        out["ppsspp_enable_builtin_pro_ad_hoc_server"] = if (runServer) "enabled" else "disabled"
        out["ppsspp_change_pro_ad_hoc_server_address"] = server
        out["ppsspp_forced_first_connect"] = "enabled"
        out["ppsspp_enable_upnp"] = "disabled"
        pspAdhocAddressDigits(server).forEachIndexed { index, digit ->
            out["ppsspp_pro_ad_hoc_server_address${(index + 1).toString().padStart(2, '0')}"] = digit
        }
        macNibblesFromNickname(settings.netplayNickname).forEachIndexed { index, nibble ->
            out["ppsspp_change_mac_address${(index + 1).toString().padStart(2, '0')}"] = nibble
        }
    }

    internal fun adhocHostAddress(netplayHost: String, storedHost: String): String {
        val raw = netplayHost.trim().ifBlank { storedHost.trim() }
        val host = raw.substringBefore(':').trim()
        if (host.isBlank() || host.equals("localhost", ignoreCase = true) || host == "127.0.0.1") {
            return "localhost"
        }
        return host
    }

    /** PPSSPP splits IPv4 into 12 decimal digits (`192.168.001.010` → 1,9,2,…). */
    internal fun pspAdhocAddressDigits(address: String): List<String> {
        val host = address.trim().substringBefore(':')
        val ip = if (host.equals("localhost", ignoreCase = true) || host.isBlank()) {
            "127.0.0.1"
        } else {
            host
        }
        val octets = ip.split('.')
        return (0..3).flatMap { i ->
            val n = octets.getOrNull(i)?.toIntOrNull()?.coerceIn(0, 255) ?: 0
            n.toString().padStart(3, '0').map { it.toString() }
        }
    }

    /** 12 hex nibbles, locally-administered unicast, stable per nickname. */
    internal fun macNibblesFromNickname(nickname: String): List<String> {
        val seed = nickname.trim().ifBlank { "Player" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
        val nibbles = digest.flatMap { byte ->
            val v = byte.toInt() and 0xFF
            listOf(
                ((v shr 4) and 0xF).toString(16).uppercase(),
                (v and 0xF).toString(16).uppercase(),
            )
        }.take(12).toMutableList()
        val first = (nibbles[0].toInt(16) shl 4) or nibbles[1].toInt(16)
        val localUnicast = (first and 0xFE) or 0x02
        nibbles[0] = ((localUnicast shr 4) and 0xF).toString(16).uppercase()
        nibbles[1] = (localUnicast and 0xF).toString(16).uppercase()
        return nibbles
    }
}
