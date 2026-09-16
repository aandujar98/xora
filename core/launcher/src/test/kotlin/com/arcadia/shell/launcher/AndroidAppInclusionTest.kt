package com.arcadia.shell.launcher

import com.arcadia.shell.datastore.AndroidAppInclusionMode
import com.arcadia.shell.datastore.ShellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAppInclusionTest {

    private val chrome = InstalledApp("com.android.chrome", "Chrome")
    private val maps = InstalledApp("com.google.android.apps.maps", "Maps")
    private val photos = InstalledApp("com.google.android.apps.photos", "Photos")
    private val apps = listOf(chrome, maps, photos)
    private val allPackages = apps.map { it.packageName }.toSet()

    @Test
    fun syncOffYieldsNoApps() {
        val settings = ShellSettings(androidAppSyncEnabled = false)
        assertTrue(settings.includedAndroidApps(apps).isEmpty())
    }

    @Test
    fun allModeKeepsEveryLaunchableApp() {
        val settings = ShellSettings(
            androidAppSyncEnabled = true,
            androidAppInclusionMode = AndroidAppInclusionMode.All,
        )
        assertEquals(apps, settings.includedAndroidApps(apps))
    }

    @Test
    fun allowlistKeepsOnlyChosenPackages() {
        val settings = ShellSettings(
            androidAppInclusionMode = AndroidAppInclusionMode.Allowlist,
            androidAppAllowlist = setOf(maps.packageName),
        )
        assertEquals(listOf(maps), settings.includedAndroidApps(apps))
    }

    @Test
    fun emptyAllowlistHidesThePlatform() {
        val settings = ShellSettings(
            androidAppInclusionMode = AndroidAppInclusionMode.Allowlist,
            androidAppAllowlist = emptySet(),
        )
        assertTrue(settings.includedAndroidApps(apps).isEmpty())
    }

    @Test
    fun selectingEveryPackageStoresAllMode() {
        val (mode, allowlist) = resolveAndroidAppInclusion(allPackages, allPackages)
        assertEquals(AndroidAppInclusionMode.All, mode)
        assertTrue(allowlist.isEmpty())
    }

    @Test
    fun selectingNoneStoresEmptyAllowlist() {
        val (mode, allowlist) = resolveAndroidAppInclusion(allPackages, emptySet())
        assertEquals(AndroidAppInclusionMode.Allowlist, mode)
        assertTrue(allowlist.isEmpty())
    }

    @Test
    fun selectingASubsetStoresAllowlist() {
        val picked = setOf(chrome.packageName, photos.packageName)
        val (mode, allowlist) = resolveAndroidAppInclusion(allPackages, picked)
        assertEquals(AndroidAppInclusionMode.Allowlist, mode)
        assertEquals(picked, allowlist)
    }

    @Test
    fun togglingOffOneAppFromAllBecomesAllowlist() {
        val (mode, allowlist) = toggleAndroidAppInclusion(
            mode = AndroidAppInclusionMode.All,
            allowlist = emptySet(),
            allPackages = allPackages,
            packageName = maps.packageName,
            selected = false,
        )
        assertEquals(AndroidAppInclusionMode.Allowlist, mode)
        assertEquals(setOf(chrome.packageName, photos.packageName), allowlist)
    }

    @Test
    fun togglingLastMissingAppBackOnReturnsToAll() {
        val (mode, allowlist) = toggleAndroidAppInclusion(
            mode = AndroidAppInclusionMode.Allowlist,
            allowlist = setOf(chrome.packageName, photos.packageName),
            allPackages = allPackages,
            packageName = maps.packageName,
            selected = true,
        )
        assertEquals(AndroidAppInclusionMode.All, mode)
        assertTrue(allowlist.isEmpty())
    }
}
