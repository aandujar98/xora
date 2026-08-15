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
        return out
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
        out["parallel-n64-pak1"] = "memory"
        out["parallel-n64-pak2"] = "memory"
        // ParaLLEl: software RDP avoids Vulkan (unsupported by the XOrA GLES host).
        out["parallel-n64-gfx"] = "angrylion"
    }

    /**
     * Connect P1 and P2 on PlayStation cores. SwanStation/DuckStation default
     * port 2 to None; PCSX-ReARMed needs an explicit pad2 type.
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
        out["duckstation_Controller1.Type"] = "AnalogController"
        out["duckstation_Controller2.Type"] = "AnalogController"
        out["swanstation_Controller1.Type"] = "AnalogController"
        out["swanstation_Controller2.Type"] = "AnalogController"
        out["swanstation_Controller1_ForceAnalog"] = "true"
        out["swanstation_Controller2_ForceAnalog"] = "true"
        out["duckstation_Controller1_ForceAnalog"] = "true"
        out["duckstation_Controller2_ForceAnalog"] = "true"
        out["beetle_psx_pad1type"] = "analog"
        out["beetle_psx_pad2type"] = "analog"
        out["beetle_psx_hw_pad1type"] = "analog"
        out["beetle_psx_hw_pad2type"] = "analog"
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
