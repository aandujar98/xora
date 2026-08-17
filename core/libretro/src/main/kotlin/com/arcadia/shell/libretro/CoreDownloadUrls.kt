package com.arcadia.shell.libretro

/**
 * Libretro Android buildbot zip names. Most cores ship as
 * `{core}_libretro_android.so.zip`; a few (Azahar) omit the `_android` suffix.
 */
object CoreDownloadUrls {

    fun zipFileNames(core: String): List<String> {
        val name = core.trim()
        if (name.isEmpty()) return emptyList()
        return listOf(
            "${name}_libretro_android.so.zip",
            "${name}_libretro.so.zip",
        ).distinct()
    }

    fun zipUrls(repoBaseUrl: String, abi: String, core: String): List<String> {
        val base = repoBaseUrl.trimEnd('/')
        val folder = abi.trim().trim('/')
        return zipFileNames(core).map { "$base/$folder/$it" }
    }
}
