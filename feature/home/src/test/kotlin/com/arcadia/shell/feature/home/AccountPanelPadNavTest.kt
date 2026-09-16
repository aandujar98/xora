package com.arcadia.shell.feature.home

import com.arcadia.shell.input.NavAction
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountPanelPadNavTest {

    private val size = 9 // notifications + 5 pins + 3 friends

    @Test
    fun downFromNotificationsLandsOnTheFirstPinNotTheFriendList() {
        val down = accountPanelAfterAction(0, NavAction.Down, rowCount = size, notificationsOpen = false)
        assertEquals(1, down.index)
        assertEquals(0, down.tabDelta)
    }

    @Test
    fun leftAndRightWalkPinsWithoutChangingTabs() {
        val right = accountPanelAfterAction(1, NavAction.Right, rowCount = size, notificationsOpen = false)
        assertEquals(2, right.index)
        assertEquals(0, right.tabDelta)
        val left = accountPanelAfterAction(2, NavAction.Left, rowCount = size, notificationsOpen = false)
        assertEquals(1, left.index)
        val edge = accountPanelAfterAction(1, NavAction.Left, rowCount = size, notificationsOpen = false)
        assertEquals(1, edge.index)
    }

    @Test
    fun downFromAPinJumpsToTheFriendListWithoutScrubbingTheRow() {
        val fromMid = accountPanelAfterAction(3, NavAction.Down, rowCount = size, notificationsOpen = false)
        assertEquals(6, fromMid.index)
        val fromLast = accountPanelAfterAction(5, NavAction.Down, rowCount = size, notificationsOpen = false)
        assertEquals(6, fromLast.index)
    }

    @Test
    fun upFromTheFirstFriendReturnsToThePinRow() {
        val up = accountPanelAfterAction(6, NavAction.Up, rowCount = size, notificationsOpen = false)
        assertEquals(1, up.index)
        val notif = accountPanelAfterAction(1, NavAction.Up, rowCount = size, notificationsOpen = false)
        assertEquals(0, notif.index)
    }

    @Test
    fun leftAndRightOnTheFriendListCycleTabs() {
        val left = accountPanelAfterAction(7, NavAction.Left, rowCount = size, notificationsOpen = false)
        assertEquals(7, left.index)
        assertEquals(-1, left.tabDelta)
        val right = accountPanelAfterAction(0, NavAction.Right, rowCount = size, notificationsOpen = false)
        assertEquals(1, right.tabDelta)
    }

    @Test
    fun shouldersAlwaysCycleTabsAndLeftRightDoNotWhileNotificationsAreOpen() {
        val shoulder = accountPanelAfterAction(
            3,
            NavAction.NextPlatform,
            rowCount = size,
            notificationsOpen = false,
        )
        assertEquals(1, shoulder.tabDelta)
        val blocked = accountPanelAfterAction(
            7,
            NavAction.Left,
            rowCount = size,
            notificationsOpen = true,
        )
        assertEquals(0, blocked.tabDelta)
        assertEquals(7, blocked.index)
    }
}
