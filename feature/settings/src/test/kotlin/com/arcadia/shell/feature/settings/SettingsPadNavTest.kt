package com.arcadia.shell.feature.settings

import com.arcadia.shell.input.NavAction
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPadNavTest {

    @Test
    fun shouldersChangeTabsAndLandOnTheFirstControl() {
        val start = SettingsPadNavState(1, SettingsPadZone.Tabs, controlIndex = 4)
        val left = settingsPadAfterAction(start, NavAction.PreviousPlatform, sectionCount = 7, controlCount = 5)
        assertEquals(0, left.sectionIndex)
        assertEquals(SettingsPadZone.Controls, left.zone)
        assertEquals(0, left.controlIndex)

        val right = settingsPadAfterAction(
            start,
            NavAction.NextPlatform,
            sectionCount = 7,
            controlCount = 5,
        )
        assertEquals(2, right.sectionIndex)
        assertEquals(SettingsPadZone.Controls, right.zone)
        assertEquals(0, right.controlIndex)

        val wrap = settingsPadAfterAction(
            SettingsPadNavState(0, SettingsPadZone.Controls, 0),
            NavAction.PreviousPlatform,
            sectionCount = 7,
            controlCount = 5,
        )
        assertEquals(6, wrap.sectionIndex)
    }

    @Test
    fun downLeavesTheTabsAndWalksEveryControlThenStops() {
        val into = settingsPadAfterAction(
            SettingsPadNavState(0, SettingsPadZone.Tabs, 0),
            NavAction.Down,
            sectionCount = 7,
            controlCount = 3,
        )
        assertEquals(SettingsPadZone.Controls, into.zone)
        assertEquals(0, into.controlIndex)

        val next = settingsPadAfterAction(into, NavAction.Down, sectionCount = 7, controlCount = 3)
        assertEquals(1, next.controlIndex)

        val last = settingsPadAfterAction(
            SettingsPadNavState(0, SettingsPadZone.Controls, 2),
            NavAction.Down,
            sectionCount = 7,
            controlCount = 3,
        )
        assertEquals(2, last.controlIndex)
    }

    @Test
    fun upFromTheFirstControlReachesTabsThenDone() {
        val tabs = settingsPadAfterAction(
            SettingsPadNavState(0, SettingsPadZone.Controls, 0),
            NavAction.Up,
            sectionCount = 7,
            controlCount = 3,
        )
        assertEquals(SettingsPadZone.Tabs, tabs.zone)
        assertEquals(SettingsPadIds.Tabs, settingsPadFocusId(tabs, listOf("a", "b")))

        val done = settingsPadAfterAction(tabs, NavAction.Up, sectionCount = 7, controlCount = 3)
        assertEquals(SettingsPadZone.Done, done.zone)
        assertEquals(SettingsPadIds.Done, settingsPadFocusId(done, listOf("a", "b")))
    }

    @Test
    fun leftAndRightOnControlsWalkTheListWhenTheControlIsNotASlider() {
        val mid = SettingsPadNavState(0, SettingsPadZone.Controls, 1)
        val left = settingsPadAfterAction(mid, NavAction.Left, sectionCount = 7, controlCount = 4)
        assertEquals(0, left.controlIndex)
        val right = settingsPadAfterAction(mid, NavAction.Right, sectionCount = 7, controlCount = 4)
        assertEquals(2, right.controlIndex)
    }

    @Test
    fun coerceKeepsTheCursorOnALiveControlWhenTheListShrinks() {
        val shrunk = settingsPadCoerce(
            SettingsPadNavState(0, SettingsPadZone.Controls, 8),
            controlCount = 3,
        )
        assertEquals(2, shrunk.controlIndex)
        val empty = settingsPadCoerce(
            SettingsPadNavState(0, SettingsPadZone.Controls, 1),
            controlCount = 0,
        )
        assertEquals(SettingsPadZone.Tabs, empty.zone)
    }

    @Test
    fun downOnTheLastControlStaysPutSoTheHostCanScrollThePage() {
        val last = SettingsPadNavState(0, SettingsPadZone.Controls, 4)
        val still = settingsPadAfterAction(last, NavAction.Down, sectionCount = 7, controlCount = 5)
        assertEquals(last, still)
        assertEquals(true, settingsPadShouldScrollPage(last, still, NavAction.Down))
        assertEquals(
            false,
            settingsPadShouldScrollPage(
                last,
                settingsPadAfterAction(last, NavAction.Up, sectionCount = 7, controlCount = 5),
                NavAction.Up,
            ),
        )
    }

    @Test
    fun confirmOnTabsDropsIntoTheFirstControlAndConfirmOnAControlStays() {
        val into = settingsPadAfterAction(
            SettingsPadNavState(0, SettingsPadZone.Tabs, 0),
            NavAction.Confirm,
            sectionCount = 7,
            controlCount = 4,
        )
        assertEquals(SettingsPadZone.Controls, into.zone)
        assertEquals(0, into.controlIndex)
        assertEquals(
            SettingsPadNavState(0, SettingsPadZone.Controls, 1),
            settingsPadAfterAction(
                SettingsPadNavState(0, SettingsPadZone.Controls, 1),
                NavAction.Confirm,
                sectionCount = 7,
                controlCount = 4,
            ),
        )
    }
}
