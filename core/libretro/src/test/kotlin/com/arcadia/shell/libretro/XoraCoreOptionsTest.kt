package com.arcadia.shell.libretro

import com.arcadia.shell.datastore.DualScreenLayout
import com.arcadia.shell.datastore.KAERU_WFC_DNS
import com.arcadia.shell.datastore.NdsWfcServer
import com.arcadia.shell.datastore.ThreeDsScreenLayout
import com.arcadia.shell.datastore.WIIMMFI_WFC_DNS
import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.nextPublic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraCoreOptionsTest {

    private val settings = XoraEmulatorSettings()

    @Test
    fun nesDoesNotForcePsOrGcPadOptions() {
        val vars = XoraCoreOptions.variablesFor("nes", "fceumm", settings)
        assertFalse(vars.containsKey("pcsx_rearmed_pad2type"))
        assertFalse(vars.containsKey("dolphin_alt_gc_ports_on_wii"))
    }

    @Test
    fun nesAndSnesConnectStandardPadsOnAllFourPorts() {
        val mesen = XoraCoreOptions.variablesFor("nes", "mesen", settings)
        assertEquals("Standard Controller", mesen["mesen_port2type"])
        assertEquals("Standard Controller", mesen["mesen_port3type"])
        assertEquals("Standard Controller", mesen["mesen_port4type"])
        assertEquals("disabled", mesen["nestopia_select_adapter"])

        val bsnes = XoraCoreOptions.variablesFor("snes", "bsnes", settings)
        assertEquals("Gamepad", bsnes["bsnes_port_1"])
        assertEquals("Gamepad", bsnes["bsnes_port_2"])
        assertEquals("Standard Controller", bsnes["mesen-s_port3type"])
        assertEquals("Standard Controller", bsnes["mesen-s_port4type"])
    }

    @Test
    fun n64ConnectsControllerPaksOnAllFourPorts() {
        val mupen = XoraCoreOptions.variablesFor("n64", "mupen64plus_next_gles3", settings)
        assertEquals("memory", mupen["mupen64plus-pak1"])
        assertEquals("memory", mupen["mupen64plus-pak2"])
        assertEquals("memory", mupen["mupen64plus-pak3"])
        assertEquals("memory", mupen["mupen64plus-pak4"])

        val parallel = XoraCoreOptions.variablesFor("n64", "parallel_n64", settings)
        assertEquals("memory", parallel["parallel-n64-pak1"])
        assertEquals("memory", parallel["parallel-n64-pak4"])
    }

    @Test
    fun ps1ConnectsAnalogPadsOnAllFourPorts() {
        val pcsx = XoraCoreOptions.variablesFor("ps1", "pcsx_rearmed", settings)
        assertEquals("analog", pcsx["pcsx_rearmed_pad1type"])
        assertEquals("analog", pcsx["pcsx_rearmed_pad2type"])
        assertEquals("analog", pcsx["pcsx_rearmed_pad3type"])
        assertEquals("analog", pcsx["pcsx_rearmed_pad4type"])
        assertEquals("disabled", pcsx["pcsx_rearmed_multitap"])

        val swan = XoraCoreOptions.variablesFor("ps1", "swanstation", settings)
        assertEquals("AnalogController", swan["swanstation_Controller2.Type"])
        assertEquals("AnalogController", swan["swanstation_Controller4.Type"])
        assertEquals("true", swan["swanstation_Controller2_ForceAnalog"])
        assertEquals("AnalogController", swan["duckstation_Controller2.Type"])
        assertEquals("Off", swan["duckstation_Multitap.Mode"])
    }

    @Test
    fun gamecubeKeepsPadsOnAllFourPorts() {
        val vars = XoraCoreOptions.variablesFor("gamecube", "dolphin", settings)
        assertEquals("OFF", vars["dolphin_alt_gc_ports_on_wii"])
        assertEquals("Standard Controller", vars["dolphin_port_1_type"])
        assertEquals("Standard Controller", vars["dolphin_port_2_type"])
        assertEquals("Standard Controller", vars["dolphin_port_3_type"])
        assertEquals("Standard Controller", vars["dolphin_port_4_type"])
        assertTrue(vars["pcsx_rearmed_pad2type"].isNullOrEmpty())
    }

    @Test
    fun genesisUsesSixButtonPadsOnBothPorts() {
        val vars = XoraCoreOptions.variablesFor("genesis", "picodrive", settings)
        assertEquals("6 button pad", vars["picodrive_input1"])
        assertEquals("6 button pad", vars["picodrive_input2"])
    }

    @Test
    fun twoPlayerCoresNeverReplacePortTwoWithAnAdapter() {
        val platforms = listOf(
            "nes" to "fceumm",
            "snes" to "bsnes",
            "ps1" to "pcsx_rearmed",
            "ps1" to "swanstation",
            "genesis" to "picodrive",
            "saturn" to "mednafen_saturn",
        )
        for ((platform, core) in platforms) {
            val blob = XoraCoreOptions.variablesFor(platform, core, settings).values
                .joinToString(" ")
                .lowercase()
            assertFalse("$core must not put a multitap on P2", blob.contains("multitap"))
            assertFalse("$core must not put a team player on P2", blob.contains("teamplayer"))
            assertFalse("$core must not use Port2Only", blob.contains("port2only"))
        }
    }

    @Test
    fun gpspEnablesAutomaticLinkCable() {
        val vars = XoraCoreOptions.variablesFor("gba", "gpsp", settings)
        assertEquals("mul_poke", vars["gpsp_serial"])
        assertEquals("disabled", vars["gpsp_drc"])
    }

    @Test
    fun remainingHomeConsolesPlugSecondPads() {
        val pce = XoraCoreOptions.variablesFor("pcengine", "mednafen_pce_fast", settings)
        assertEquals("disabled", pce["pce_fast_multitap"])
        val stella = XoraCoreOptions.variablesFor("atari2600", "stella", settings)
        assertEquals("Joystick", stella["stella_controller2"])
        val opera = XoraCoreOptions.variablesFor("3do", "opera", settings)
        assertEquals("4", opera["opera_active_devices"])
        val amiga = XoraCoreOptions.variablesFor("amiga", "puae", settings)
        assertEquals("RetroPad", amiga["puae_joyport2"])
        val saturn = XoraCoreOptions.variablesFor("saturn", "mednafen_saturn", settings)
        assertEquals("disabled", saturn["beetle_saturn_multitap_port1"])
        assertEquals("disabled", saturn["beetle_saturn_multitap_port2"])
    }

    @Test
    fun ndsDefaultsToKaeruWfc() {
        val vars = XoraCoreOptions.variablesFor("nds", "melondsds", settings)
        assertEquals("Indirect", vars["melonds_network_mode"])
        assertEquals("indirect", vars["melonds_ds_network_mode"])
        assertEquals(KAERU_WFC_DNS, vars["melonds_firmware_wfc_dns"])
        assertEquals(KAERU_WFC_DNS, vars["melonds_ds_firmware_wfc_dns"])
        assertEquals("from-username", vars["melonds_mac_address_mode"])
        assertEquals("from-username", vars["melonds_ds_mac_address_mode"])
    }

    @Test
    fun ndsCartsForceDsModeAndBuiltinBios() {
        val melonDsDs = XoraCoreOptions.variablesFor(
            "nds",
            "melondsds",
            settings,
            romPath = "/storage/ROMs/NDS/Mario Kart DS.nds",
        )
        assertEquals("ds", melonDsDs["melonds_console_mode"])
        assertEquals("ds", melonDsDs["melonds_ds_console_mode"])
        assertEquals("builtin", melonDsDs["melonds_sysfile_mode"])
        assertEquals("direct", melonDsDs["melonds_boot_mode"])
        assertFalse(XoraCoreOptions.ndsRomWantsDsiMode("/storage/ROMs/NDS/Mario Kart DS.nds"))

        val legacy = XoraCoreOptions.variablesFor(
            "nds",
            "melonds",
            settings,
            romPath = "pokemon.NDS",
        )
        assertEquals("DS", legacy["melonds_console_mode"])
        assertEquals("ds", legacy["melonds_ds_console_mode"])
    }

    @Test
    fun dsiWareKeepsDsiConsoleMode() {
        val vars = XoraCoreOptions.variablesFor(
            "nds",
            "melondsds",
            settings,
            romPath = "/storage/ROMs/DSi/Flipnote Studio.dsi",
        )
        assertEquals("dsi", vars["melonds_console_mode"])
        assertEquals("dsi", vars["melonds_ds_console_mode"])
        assertTrue(XoraCoreOptions.ndsRomWantsDsiMode("Flipnote Studio.dsi"))
        val legacy = XoraCoreOptions.variablesFor(
            "nds",
            "melonds",
            settings,
            romPath = "game.dsi",
        )
        assertEquals("DSi", legacy["melonds_console_mode"])
    }

    @Test
    fun ndsCanSwitchToWiimmfiOrOff() {
        val wiimmfi = XoraCoreOptions.variablesFor(
            "nds",
            "melonds",
            settings.copy(ndsWfcServer = NdsWfcServer.Wiimmfi),
        )
        assertEquals(WIIMMFI_WFC_DNS, wiimmfi["melonds_firmware_wfc_dns"])
        val off = XoraCoreOptions.variablesFor(
            "nds",
            "melonds",
            settings.copy(ndsWfcServer = NdsWfcServer.Off),
        )
        assertEquals("Disabled", off["melonds_network_mode"])
        assertEquals("0.0.0.0", off["melonds_firmware_wfc_dns"])
        assertEquals("firmware", off["melonds_mac_address_mode"])
    }

    @Test
    fun ndsPublicWfcCycleSkipsCustom() {
        assertEquals(NdsWfcServer.Wiimmfi, NdsWfcServer.Kaeru.nextPublic())
        assertEquals(NdsWfcServer.AltWfc, NdsWfcServer.Wiimmfi.nextPublic())
        assertEquals(NdsWfcServer.Off, NdsWfcServer.AltWfc.nextPublic())
        assertEquals(NdsWfcServer.Kaeru, NdsWfcServer.Off.nextPublic())
        assertEquals(NdsWfcServer.Kaeru, NdsWfcServer.Custom.nextPublic())
    }

    @Test
    fun pspEnablesAdhocAndUniqueMac() {
        val host = XoraCoreOptions.variablesFor(
            "psp",
            "ppsspp",
            settings.copy(netplayNickname = "FlipDS"),
            netplay = XoraCoreOptions.NetplayContext(hosting = true),
        )
        assertEquals("enabled", host["ppsspp_enable_wlan"])
        assertEquals("enabled", host["ppsspp_enable_builtin_pro_ad_hoc_server"])
        assertEquals("localhost", host["ppsspp_change_pro_ad_hoc_server_address"])
        val joiner = XoraCoreOptions.variablesFor(
            "psp",
            "ppsspp",
            settings.copy(netplayNickname = "RgCube"),
            netplay = XoraCoreOptions.NetplayContext(
                joining = true,
                hostAddress = "192.168.1.20",
            ),
        )
        assertEquals("disabled", joiner["ppsspp_enable_builtin_pro_ad_hoc_server"])
        assertEquals("192.168.1.20", joiner["ppsspp_change_pro_ad_hoc_server_address"])
        assertEquals("1", joiner["ppsspp_pro_ad_hoc_server_address01"])
        assertEquals("9", joiner["ppsspp_pro_ad_hoc_server_address02"])
        assertEquals("2", joiner["ppsspp_pro_ad_hoc_server_address03"])
        assertNotEquals(host["ppsspp_change_mac_address01"] + host["ppsspp_change_mac_address02"],
            joiner["ppsspp_change_mac_address01"] + joiner["ppsspp_change_mac_address02"])
        val firstByte = (
            (host.getValue("ppsspp_change_mac_address01").toInt(16) shl 4) or
                host.getValue("ppsspp_change_mac_address02").toInt(16)
            )
        assertEquals(0, firstByte and 0x01)
        assertEquals(0x02, firstByte and 0x02)
    }

    @Test
    fun azaharGetsTheSameLayoutKeysAsCitra() {
        val vars = XoraCoreOptions.variablesFor("3ds", "azahar", settings)
        assertEquals(vars["citra_layout_option"], vars["azahar_layout_option"])
        assertEquals(vars["citra_resolution_factor"], vars["azahar_resolution_factor"])
    }

    @Test
    fun azaharForcesOpenGlInsteadOfAndroidVulkanDefault() {
        val vars = XoraCoreOptions.variablesFor("3ds", "azahar", settings)
        assertEquals("OpenGL", vars["citra_graphics_api"])
        assertEquals("OpenGL", vars["azahar_graphics_api"])
        val citra = XoraCoreOptions.variablesFor("3ds", "citra", settings)
        assertEquals("OpenGL", citra["citra_graphics_api"])
    }

    @Test
    fun pretendoPrepPinsNew3dsVirtualSdAndDoesNotDropOpenGl() {
        val vars = XoraCoreOptions.variablesFor(
            "3ds",
            "azahar",
            settings.copy(threeDsPretendoPrep = true),
        )
        assertEquals("OpenGL", vars["azahar_graphics_api"])
        assertEquals("OpenGL", vars["citra_graphics_api"])
        assertEquals("New 3DS", vars["azahar_is_new_3ds"])
        assertEquals("enabled", vars["azahar_use_virtual_sd"])
        assertEquals("LibRetro Default", vars["azahar_use_libretro_save_path"])
        assertEquals("New 3DS", vars["citra_is_new_3ds"])
        assertEquals("enabled", vars["citra_use_virtual_sd"])
        assertEquals("LibRetro Default", vars["citra_use_libretro_save_path"])
    }

    @Test
    fun pretendoPrepOffDoesNotPinLibretroSavePath() {
        val vars = XoraCoreOptions.variablesFor("3ds", "azahar", settings)
        assertTrue(vars["azahar_use_libretro_save_path"].isNullOrEmpty())
        assertTrue(vars["azahar_is_new_3ds"].isNullOrEmpty())
        assertEquals("OpenGL", vars["azahar_graphics_api"])
    }

    @Test
    fun expandKeepsChosenNdsLayout() {
        val vars = XoraCoreOptions.variablesFor(
            "nds",
            "melonds",
            settings.copy(ndsScreenLayout = DualScreenLayout.LeftRight),
            expandActive = true,
        )
        assertEquals("Left/Right", vars["melonds_screen_layout"])
        assertEquals("left-right", vars["melonds_screen_layout1"])
        assertEquals("left-right", vars["melonds_ds_screen_layout1"])
        assertEquals("0", vars["melonds_screen_gap"])
        assertEquals("left/right", vars["desmume_screens_layout"])
        assertEquals("Touch", vars["melonds_touch_mode"])
        assertEquals("touch", vars["melonds_ds_touch_mode"])

        val melonDsDs = XoraCoreOptions.variablesFor(
            "nds",
            "melondsds",
            settings.copy(ndsScreenLayout = DualScreenLayout.BottomOnly),
            expandActive = true,
        )
        assertEquals("bottom", melonDsDs["melonds_screen_layout1"])
        assertEquals("touch", melonDsDs["melonds_touch_mode"])
        assertEquals("1", melonDsDs["melonds_number_of_screen_layouts"])
    }

    @Test
    fun expandWritesDesmumeLayoutForEveryNdsCore() {
        val vars = XoraCoreOptions.variablesFor(
            "nds",
            "desmume",
            settings.copy(ndsScreenLayout = DualScreenLayout.BottomOnly),
            expandActive = true,
        )
        assertEquals("bottom only", vars["desmume_screens_layout"])
        assertEquals("Bottom Only", vars["melonds_screen_layout"])
    }

    @Test
    fun expandKeepsChosen3dsLayout() {
        val vars = XoraCoreOptions.variablesFor(
            "3ds",
            "azahar",
            settings.copy(threeDsScreenLayout = ThreeDsScreenLayout.SideBySide),
            expandActive = true,
        )
        assertEquals("Side by Side", vars["azahar_layout_option"])
        assertEquals("Side by Side", vars["citra_layout_option"])
        assertEquals("Side by Side", vars["citra2018_layout_option"])
        assertEquals("side_by_side", vars["panda3ds_layout"])
        assertEquals("disabled", vars["citra_swap_screen"])
    }

    @Test
    fun aspectWritesCoreKeysInsteadOfFrontendOnly() {
        val wide = XoraCoreOptions.variablesFor(
            "n64",
            "mupen64plus_next_gles3",
            settings.copy(aspectMode = XoraAspectMode.Ratio16x9),
        )
        assertEquals("16:9", wide["mupen64plus-aspect"])
        assertEquals("16:9", wide["parallel-n64-aspectratio"])

        val psp = XoraCoreOptions.variablesFor(
            "psp",
            "ppsspp",
            settings.copy(aspectMode = XoraAspectMode.Stretch),
        )
        assertEquals("stretched", psp["ppsspp_aspect"])

        val ps1 = XoraCoreOptions.variablesFor(
            "ps1",
            "swanstation",
            settings.copy(aspectMode = XoraAspectMode.Ratio4x3),
        )
        assertEquals("4:3", ps1["swanstation_Display.AspectRatio"])
        assertEquals("4:3", ps1["duckstation_Display.AspectRatio"])
    }

    @Test
    fun expandKeepsChosenPanda3dsLayout() {
        val vars = XoraCoreOptions.variablesFor(
            "3ds",
            "panda3ds",
            settings.copy(threeDsScreenLayout = ThreeDsScreenLayout.SingleScreen),
            expandActive = true,
        )
        assertEquals("single", vars["panda3ds_layout"])
        assertEquals("Single Screen Only", vars["azahar_layout_option"])
    }
}
