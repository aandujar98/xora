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
    fun nesAndSnesConnectStandardPadsOnBothPorts() {
        val mesen = XoraCoreOptions.variablesFor("nes", "mesen", settings)
        assertEquals("Standard Controller", mesen["mesen_port2type"])

        val bsnes = XoraCoreOptions.variablesFor("snes", "bsnes", settings)
        assertEquals("Gamepad", bsnes["bsnes_port_1"])
        assertEquals("Gamepad", bsnes["bsnes_port_2"])
    }

    @Test
    fun n64ConnectsControllerPaksOnBothPorts() {
        val mupen = XoraCoreOptions.variablesFor("n64", "mupen64plus_next_gles3", settings)
        assertEquals("memory", mupen["mupen64plus-pak1"])
        assertEquals("memory", mupen["mupen64plus-pak2"])

        val parallel = XoraCoreOptions.variablesFor("n64", "parallel_n64", settings)
        assertEquals("memory", parallel["parallel-n64-pak1"])
        assertEquals("memory", parallel["parallel-n64-pak2"])
    }

    @Test
    fun ps1ConnectsAnalogPadsOnBothPorts() {
        val pcsx = XoraCoreOptions.variablesFor("ps1", "pcsx_rearmed", settings)
        assertEquals("analog", pcsx["pcsx_rearmed_pad1type"])
        assertEquals("analog", pcsx["pcsx_rearmed_pad2type"])

        val swan = XoraCoreOptions.variablesFor("ps1", "swanstation", settings)
        assertEquals("AnalogController", swan["swanstation_Controller2.Type"])
        assertEquals("true", swan["swanstation_Controller2_ForceAnalog"])
        assertEquals("AnalogController", swan["duckstation_Controller2.Type"])
    }

    @Test
    fun gamecubeKeepsPadsOnPortsOneAndTwo() {
        val vars = XoraCoreOptions.variablesFor("gamecube", "dolphin", settings)
        assertEquals("OFF", vars["dolphin_alt_gc_ports_on_wii"])
        assertEquals("Standard Controller", vars["dolphin_port_1_type"])
        assertEquals("Standard Controller", vars["dolphin_port_2_type"])
        assertTrue(vars["pcsx_rearmed_pad2type"].isNullOrEmpty())
    }
}
