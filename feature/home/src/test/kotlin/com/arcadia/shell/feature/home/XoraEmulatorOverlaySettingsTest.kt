package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.DualScreenLayout
import com.arcadia.shell.datastore.ThreeDsScreenLayout
import com.arcadia.shell.datastore.next
import com.arcadia.shell.datastore.nextNdsScreenGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraEmulatorOverlaySettingsTest {

    @Test
    fun dualScreenRowsStayOnXoraHostOverlay() {
        assertEquals(
            listOf("nds-layout", "nds-gap", "g-dual"),
            xoraOverlayDualScreenIds("nds"),
        )
        assertEquals(
            listOf("3ds-layout", "g-res", "g-dual"),
            xoraOverlayDualScreenIds("3ds"),
        )
        assertTrue(xoraOverlayDualScreenIds("nes").isEmpty())
        assertTrue(xoraOverlayDualScreenIds("psp").isEmpty())
        assertFalse(isXoraDualScreenPlatform("snes"))
    }

    @Test
    fun netplayIdentityLivesOnTheOverlay() {
        assertEquals(listOf("np-nick", "np-listen-port"), xoraOverlayNetplayIdentityIds())
    }

    @Test
    fun dsAnd3dsLayoutsCycleOnTheHost() {
        assertEquals(DualScreenLayout.BottomTop, DualScreenLayout.TopBottom.next())
        assertEquals(DualScreenLayout.TopBottom, DualScreenLayout.HybridBottom.next())
        assertEquals(ThreeDsScreenLayout.SideBySide, ThreeDsScreenLayout.TopBottom.next())
        assertEquals(ThreeDsScreenLayout.TopBottom, ThreeDsScreenLayout.LargeSmall.next())
        assertEquals(8, nextNdsScreenGap(0))
        assertEquals(0, nextNdsScreenGap(64))
        assertEquals(16, nextNdsScreenGap(10))
    }
}
