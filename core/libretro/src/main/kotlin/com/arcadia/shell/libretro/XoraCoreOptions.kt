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
        applyN64(platformId, coreName, out)
        return out
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
        out["parallel-n64-gfx"] = "angrylion"
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
