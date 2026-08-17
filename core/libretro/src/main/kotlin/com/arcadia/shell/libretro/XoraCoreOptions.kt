package com.arcadia.shell.libretro

import com.arcadia.shell.datastore.DualScreenLayout
import com.arcadia.shell.datastore.ThreeDsScreenLayout
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.toCitraFactor
import com.arcadia.shell.datastore.toCitraValue
import com.arcadia.shell.datastore.toMelonDsDsValue
import com.arcadia.shell.datastore.toMelonDsValue

/**
 * Maps XOrA Emulator settings to Libretro core option key/value pairs
 * applied before [LibretroNative.nativeLoadGame].
 */
object XoraCoreOptions {

    fun variablesFor(
        platformId: String,
        coreName: String,
        settings: XoraEmulatorSettings,
        expandActive: Boolean = false,
    ): Map<String, String> {
        val out = linkedMapOf<String, String>()
        when (platformId) {
            "nds" -> applyNds(coreName, settings, expandActive, out)
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
        applyGba(platformId, coreName, out)
        return out
    }

    private fun applyGba(
        platformId: String,
        coreName: String,
        out: MutableMap<String, String>,
    ) {
        if (platformId != "gba" && !coreName.contains("gpsp", ignoreCase = true)) return
        // auto leaves most carts (Kirby, Mario Kart, …) with serial disabled.
        // mul_poke is gpSP's generic 2–4 player Game Link cable.
        out["gpsp_serial"] = "mul_poke"
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
        out["beetle_saturn_multitap_port2"] = "disabled"
        out["mednafen_saturn_multitap_port2"] = "disabled"
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
        out: MutableMap<String, String>,
    ) {
        val layout = if (expandActive) DualScreenLayout.TopBottom else settings.ndsScreenLayout
        val gap = if (expandActive) 0 else settings.ndsScreenGap.coerceIn(0, 100)
        val melon = layout.toMelonDsValue()
        val melonDs = layout.toMelonDsDsValue()

        out["melonds_screen_layout"] = melon
        out["melonds_screen_gap"] = gap.toString()
        out["melonds_ds_screen_layout1"] = melonDs
        out["melonds_ds_number_of_screen_layouts"] = "1"

        if (coreName.contains("desmume", ignoreCase = true)) {
            out["desmume_screens_layout"] = when (layout) {
                DualScreenLayout.LeftRight, DualScreenLayout.RightLeft -> "left/right"
                DualScreenLayout.TopOnly -> "top only"
                DualScreenLayout.BottomOnly -> "bottom only"
                else -> "top/bottom"
            }
        }
    }

    private fun apply3ds(
        coreName: String,
        settings: XoraEmulatorSettings,
        expandActive: Boolean,
        out: MutableMap<String, String>,
    ) {
        val layout = if (expandActive) ThreeDsScreenLayout.TopBottom else settings.threeDsScreenLayout
        val citra = layout.toCitraValue()
        out["citra_layout_option"] = citra
        out["azahar_layout_option"] = citra
        if (coreName.contains("panda", ignoreCase = true)) {
            out["panda3ds_layout"] = when (layout) {
                ThreeDsScreenLayout.SideBySide -> "side_by_side"
                ThreeDsScreenLayout.SingleScreen -> "single"
                else -> "top_bottom"
            }
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
}
