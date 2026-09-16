package com.arcadia.shell.feature.settings

import com.arcadia.shell.input.NavAction
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPadNavTest {

    private val threeByTwo = SettingsPadLayout(
        listOf(
            listOf("a", "b", "c"),
            listOf("d", "e"),
        ),
    )

    @Test
    fun shouldersChangeTabsAndLandOnTheFirstCell() {
        val start = SettingsPadNavState(1, SettingsPadZone.Tabs, rowIndex = 4, colIndex = 2)
        val left = settingsPadAfterAction(start, NavAction.PreviousPlatform, sectionCount = 7, layout = threeByTwo)
        assertEquals(0, left.sectionIndex)
        assertEquals(SettingsPadZone.Controls, left.zone)
        assertEquals(0, left.rowIndex)
        assertEquals(0, left.colIndex)

        val right = settingsPadAfterAction(
            start,
            NavAction.NextPlatform,
            sectionCount = 7,
            layout = threeByTwo,
        )
        assertEquals(2, right.sectionIndex)
        assertEquals(SettingsPadZone.Controls, right.zone)

        val wrap = settingsPadAfterAction(
            SettingsPadNavState(0, SettingsPadZone.Controls, 0, 0),
            NavAction.PreviousPlatform,
            sectionCount = 7,
            layout = threeByTwo,
        )
        assertEquals(6, wrap.sectionIndex)
    }

    @Test
    fun downLeavesTheTabsAndWalksRowsNotCells() {
        val into = settingsPadAfterAction(
            SettingsPadNavState(0, SettingsPadZone.Tabs, 0, 0),
            NavAction.Down,
            sectionCount = 7,
            layout = threeByTwo,
        )
        assertEquals(SettingsPadZone.Controls, into.zone)
        assertEquals(0, into.rowIndex)
        assertEquals(0, into.colIndex)
        assertEquals("a", settingsPadFocusId(into, threeByTwo))

        val nextRow = settingsPadAfterAction(into, NavAction.Down, sectionCount = 7, layout = threeByTwo)
        assertEquals(1, nextRow.rowIndex)
        assertEquals(0, nextRow.colIndex)
        assertEquals("d", settingsPadFocusId(nextRow, threeByTwo))
    }

    @Test
    fun upFromTheFirstRowReachesTabsThenDone() {
        val tabs = settingsPadAfterAction(
            SettingsPadNavState(0, SettingsPadZone.Controls, 0, 1),
            NavAction.Up,
            sectionCount = 7,
            layout = threeByTwo,
        )
        assertEquals(SettingsPadZone.Tabs, tabs.zone)
        assertEquals(SettingsPadIds.Tabs, settingsPadFocusId(tabs, threeByTwo))

        val done = settingsPadAfterAction(tabs, NavAction.Up, sectionCount = 7, layout = threeByTwo)
        assertEquals(SettingsPadZone.Done, done.zone)
        assertEquals(SettingsPadIds.Done, settingsPadFocusId(done, threeByTwo))
    }

    @Test
    fun leftAndRightStayOnTheRowInsteadOfWalkingTheFlatList() {
        val mid = SettingsPadNavState(0, SettingsPadZone.Controls, rowIndex = 0, colIndex = 1)
        val left = settingsPadAfterAction(mid, NavAction.Left, sectionCount = 7, layout = threeByTwo)
        assertEquals(0, left.rowIndex)
        assertEquals(0, left.colIndex)
        assertEquals("a", settingsPadFocusId(left, threeByTwo))

        val right = settingsPadAfterAction(mid, NavAction.Right, sectionCount = 7, layout = threeByTwo)
        assertEquals(0, right.rowIndex)
        assertEquals(2, right.colIndex)
        assertEquals("c", settingsPadFocusId(right, threeByTwo))

        val edge = settingsPadAfterAction(left, NavAction.Left, sectionCount = 7, layout = threeByTwo)
        assertEquals(0, edge.rowIndex)
        assertEquals(0, edge.colIndex)
    }

    @Test
    fun downFromAMiddleChipLandsOnTheSameColumnOfTheNextRow() {
        val light = SettingsPadNavState(0, SettingsPadZone.Controls, rowIndex = 0, colIndex = 1)
        val down = settingsPadAfterAction(light, NavAction.Down, sectionCount = 7, layout = threeByTwo)
        assertEquals(1, down.rowIndex)
        assertEquals(1, down.colIndex)
        assertEquals("e", settingsPadFocusId(down, threeByTwo))

        val clamped = settingsPadAfterAction(
            SettingsPadNavState(0, SettingsPadZone.Controls, rowIndex = 0, colIndex = 2),
            NavAction.Down,
            sectionCount = 7,
            layout = threeByTwo,
        )
        assertEquals(1, clamped.rowIndex)
        assertEquals(1, clamped.colIndex)
        assertEquals("e", settingsPadFocusId(clamped, threeByTwo))
    }

    @Test
    fun coerceKeepsTheCursorOnALiveCellWhenTheListShrinks() {
        val shrunk = settingsPadCoerce(
            SettingsPadNavState(0, SettingsPadZone.Controls, rowIndex = 8, colIndex = 4),
            threeByTwo,
        )
        assertEquals(1, shrunk.rowIndex)
        assertEquals(1, shrunk.colIndex)
        val empty = settingsPadCoerce(
            SettingsPadNavState(0, SettingsPadZone.Controls, 1, 1),
            SettingsPadLayout(),
        )
        assertEquals(SettingsPadZone.Tabs, empty.zone)
    }

    @Test
    fun downOnTheLastRowStaysPutSoTheHostCanScrollThePage() {
        val last = SettingsPadNavState(0, SettingsPadZone.Controls, rowIndex = 1, colIndex = 0)
        val still = settingsPadAfterAction(last, NavAction.Down, sectionCount = 7, layout = threeByTwo)
        assertEquals(last, still)
        assertEquals(true, settingsPadShouldScrollPage(last, still, NavAction.Down))
        assertEquals(
            false,
            settingsPadShouldScrollPage(
                last,
                settingsPadAfterAction(last, NavAction.Up, sectionCount = 7, layout = threeByTwo),
                NavAction.Up,
            ),
        )
    }

    @Test
    fun confirmOnTabsDropsIntoTheFirstCellAndConfirmOnAControlStays() {
        val into = settingsPadAfterAction(
            SettingsPadNavState(0, SettingsPadZone.Tabs, 0, 0),
            NavAction.Confirm,
            sectionCount = 7,
            layout = threeByTwo,
        )
        assertEquals(SettingsPadZone.Controls, into.zone)
        assertEquals(0, into.rowIndex)
        assertEquals(0, into.colIndex)
        assertEquals(
            SettingsPadNavState(0, SettingsPadZone.Controls, 0, 1),
            settingsPadAfterAction(
                SettingsPadNavState(0, SettingsPadZone.Controls, 0, 1),
                NavAction.Confirm,
                sectionCount = 7,
                layout = threeByTwo,
            ),
        )
    }
}
