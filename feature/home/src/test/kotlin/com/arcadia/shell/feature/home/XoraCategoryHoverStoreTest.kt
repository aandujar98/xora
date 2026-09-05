package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class XoraCategoryHoverStoreTest {

    @Test
    fun `first visit uses the category default`() {
        val store = XoraCategoryHoverStore()
        assertEquals(
            GAMES_ITEM_RECENTS,
            store.restore(XoraXmbCategory.Games.ordinal, XoraXmbCategory.Games),
        )
        assertEquals(
            0,
            store.restore(XoraXmbCategory.Settings.ordinal, XoraXmbCategory.Settings),
        )
    }

    @Test
    fun `returning to a tab restores the hovered item`() {
        val store = XoraCategoryHoverStore()
        store.remember(XoraXmbCategory.Games.ordinal, GAMES_ITEM_LIBRARY)
        store.remember(XoraXmbCategory.Network.ordinal, 2)
        assertEquals(
            GAMES_ITEM_LIBRARY,
            store.restore(XoraXmbCategory.Games.ordinal, XoraXmbCategory.Games),
        )
        assertEquals(
            2,
            store.restore(XoraXmbCategory.Network.ordinal, XoraXmbCategory.Network),
        )
        assertEquals(
            0,
            store.restore(XoraXmbCategory.Media.ordinal, XoraXmbCategory.Media),
        )
    }

    @Test
    fun `a later hover replaces the previous one for that tab`() {
        val store = XoraCategoryHoverStore()
        store.remember(XoraXmbCategory.Games.ordinal, GAMES_ITEM_TROPHY)
        store.remember(XoraXmbCategory.Games.ordinal, GAMES_ITEM_LIBRARY)
        assertEquals(
            GAMES_ITEM_LIBRARY,
            store.restore(XoraXmbCategory.Games.ordinal, XoraXmbCategory.Games),
        )
    }
}
