package com.arcadia.shell.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDownloadUrlsTest {

    @Test
    fun androidSuffixedZipComesFirst() {
        val names = CoreDownloadUrls.zipFileNames("ppsspp")
        assertEquals("ppsspp_libretro_android.so.zip", names.first())
        assertTrue(names.contains("ppsspp_libretro.so.zip"))
    }

    @Test
    fun azaharFallsBackToUnsuffixedSoZip() {
        val urls = CoreDownloadUrls.zipUrls(
            "https://buildbot.libretro.com/nightly/android/latest",
            "arm64-v8a",
            "azahar",
        )
        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/azahar_libretro_android.so.zip",
            urls[0],
        )
        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/azahar_libretro.so.zip",
            urls[1],
        )
    }
}
