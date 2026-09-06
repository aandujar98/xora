package com.arcadia.shell.feature.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.arcadia.shell.feature.home.component.socialCardTextShadow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialCardTextShadowTest {

    @Test
    fun dropBehindTheLettersIsSolidBlack() {
        val shadow = socialCardTextShadow(4f)
        assertEquals(Color.Black, shadow.color)
        assertEquals(1f, shadow.color.alpha, 0f)
        assertEquals(Offset(4f, 4f), shadow.offset)
        assertEquals(4f, shadow.blurRadius)
    }

    @Test
    fun shadowSitsSouthEastOfTheGlyphNotOnTopOfIt() {
        val shadow = socialCardTextShadow(4f)
        assertTrue(shadow.offset.x > 0f)
        assertTrue(shadow.offset.y > 0f)
    }
}
