package com.arcadia.shell.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class XoraSwipeNavigateTest {

    @Test
    fun invertedFlipsEachAxisSoTheXmbFollowsTheFinger() {
        assertEquals(XoraSwipeDirection.Right, XoraSwipeDirection.Left.inverted())
        assertEquals(XoraSwipeDirection.Left, XoraSwipeDirection.Right.inverted())
        assertEquals(XoraSwipeDirection.Down, XoraSwipeDirection.Up.inverted())
        assertEquals(XoraSwipeDirection.Up, XoraSwipeDirection.Down.inverted())
    }
}
