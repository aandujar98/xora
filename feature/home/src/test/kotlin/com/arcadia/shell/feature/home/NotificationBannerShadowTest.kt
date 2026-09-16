package com.arcadia.shell.feature.home

import com.arcadia.shell.designsystem.XoraForegroundShadow
import com.arcadia.shell.feature.home.component.BannerShadowBlur
import com.arcadia.shell.feature.home.component.BannerShadowOffset
import com.arcadia.shell.feature.home.component.bannerCapsuleRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The toast shadow has to hug the capsule. The 10/15 XMB glyph drop reads as a square. */
class NotificationBannerShadowTest {

    @Test
    fun bannerShadowStaysCloserThanTheXmbGlyphShadow() {
        assertEquals(4f, BannerShadowOffset.value)
        assertEquals(4f, BannerShadowBlur.value)
        assertTrue(BannerShadowOffset < XoraForegroundShadow.OffsetX)
        assertTrue(BannerShadowBlur < XoraForegroundShadow.Blur)
        assertEquals(0f, XoraForegroundShadow.Spread.value)
    }

    @Test
    fun capsuleRadiusFollowsTheShortSideNotHalfTheWidth() {
        assertEquals(24f, bannerCapsuleRadius(width = 280f, height = 48f), 0.01f)
        assertEquals(24f, bannerCapsuleRadius(width = 48f, height = 280f), 0.01f)
        assertTrue(bannerCapsuleRadius(280f, 48f) < 280f / 2f)
    }
}
