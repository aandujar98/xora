package com.arcadia.shell.libretro

import com.arcadia.shell.datastore.XoraEmulatorSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("auto", mesen["nestopia_select_adapter"])

        val bsnes = XoraCoreOptions.variablesFor("snes", "bsnes", settings)
        assertEquals("Gamepad", bsnes["bsnes_port_1"])
        assertEquals("Super Multitap", bsnes["bsnes_port_2"])
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
        assertEquals("port2", pcsx["pcsx_rearmed_multitap"])

        val swan = XoraCoreOptions.variablesFor("ps1", "swanstation", settings)
        assertEquals("AnalogController", swan["swanstation_Controller2.Type"])
        assertEquals("AnalogController", swan["swanstation_Controller4.Type"])
        assertEquals("true", swan["swanstation_Controller2_ForceAnalog"])
        assertEquals("AnalogController", swan["duckstation_Controller2.Type"])
        assertEquals("Port2Only", swan["duckstation_Multitap.Mode"])
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
    fun genesisEnablesTeamPlayerOnPortTwo() {
        val vars = XoraCoreOptions.variablesFor("genesis", "picodrive", settings)
        assertEquals("6 button pad", vars["picodrive_input1"])
        assertEquals("teamplayer", vars["picodrive_input2"])
    }
}
