package com.arcadia.shell.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandDualDisplayMessagesTest {

    @Test
    fun hiddenExplainsBothScreensStayHere() {
        assertEquals(
            "Both screens on this display",
            ExpandDualDisplayMessages.forResult(SecondDisplayAttachResult.Hidden),
        )
    }

    @Test
    fun attachedExplainsBottomMoved() {
        assertEquals(
            "Bottom screen on the other display",
            ExpandDualDisplayMessages.forResult(SecondDisplayAttachResult.ShownPresentation),
        )
        assertEquals(
            "Bottom screen on the other display",
            ExpandDualDisplayMessages.forResult(SecondDisplayAttachResult.ShownOverlay),
        )
    }

    @Test
    fun missingDisplayAndPermissionTellTheUserWhatToDo() {
        assertTrue(
            ExpandDualDisplayMessages.forResult(SecondDisplayAttachResult.NoDisplay)
                .contains("No second display"),
        )
        assertTrue(
            ExpandDualDisplayMessages.forResult(SecondDisplayAttachResult.NeedsOverlayPermission)
                .contains("Display over other apps"),
        )
    }
}
