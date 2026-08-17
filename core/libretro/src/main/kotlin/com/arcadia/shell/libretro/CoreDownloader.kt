package com.arcadia.shell.libretro

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class CoreDownloadProgress(
    val core: String? = null,
    val running: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Downloads Libretro Android cores from the nightly buildbot (zip containing `*_libretro_android.so`).
 */
@Singleton
class CoreDownloader @Inject constructor(
    private val store: CoreStore,
    private val catalog: XoraCoreCatalog,
    @LibretroHttp private val http: OkHttpClient,
) {
    private val progress = MutableStateFlow(CoreDownloadProgress())
    val downloadProgress: StateFlow<CoreDownloadProgress> = progress.asStateFlow()

    suspend fun ensureCore(core: String): String? {
        store.resolveInstalledPath(core)?.let { return it }
        return downloadCore(core)
    }

    suspend fun downloadCore(core: String): String? = withContext(Dispatchers.IO) {
        progress.value = CoreDownloadProgress(core = core, running = true, message = "Downloading $core…")
        val abi = store.abiFolder()
        val url = "${catalog.repoBaseUrl.trimEnd('/')}/$abi/${store.coreFileName(core)}.zip"
        val tmpZip = File(store.corePath(core).parentFile, "${core}.download.zip")
        val tmpSo = File(store.corePath(core).parentFile, "${core}.download.so")

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "XOrA-Libretro/1.0")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    progress.value = CoreDownloadProgress(
                        core = core,
                        running = false,
                        error = "HTTP ${response.code} for $core",
                    )
                    return@withContext null
                }
                response.body.byteStream().use { input ->
                    tmpZip.outputStream().use { output -> input.copyTo(output) }
                }
            }

            val extracted = extractSoFromZip(tmpZip, tmpSo)
            if (extracted == null) {
                progress.value = CoreDownloadProgress(
                    core = core,
                    running = false,
                    error = "Zip for $core had no .so",
                )
                return@withContext null
            }

            val target = store.corePath(core)
            if (target.exists()) target.delete()
            if (!tmpSo.renameTo(target)) {
                tmpSo.copyTo(target, overwrite = true)
                tmpSo.delete()
            }
            store.refreshInstalled()
            progress.value = CoreDownloadProgress(
                core = core,
                running = false,
                message = "Installed $core",
            )
            target.absolutePath
        } catch (t: Throwable) {
            Log.w(TAG, "Download failed for $core", t)
            progress.value = CoreDownloadProgress(
                core = core,
                running = false,
                error = t.message ?: "Download failed",
            )
            null
        } finally {
            tmpZip.delete()
            tmpSo.delete()
        }
    }

    /** Download primary cores for every Phase-1 platform that is missing. */
    suspend fun downloadMissingPrimaries(platformIds: Collection<String> = catalog.supportedPlatformIds) {
        val cores = platformIds.distinct()
            .mapNotNull { catalog.primaryForPlatform(it)?.core }
            .distinct()
        for (core in cores) {
            if (!store.isInstalled(core)) {
                downloadCore(core)
            }
        }
        // Always try to pull every N64 Android buildbot core (GLES2/3 + ParaLLEl).
        for (entry in catalog.forPlatform("n64")) {
            if (!store.isInstalled(entry.core)) {
                downloadCore(entry.core)
            }
        }
        // GBA netplay uses gpSP's built-in Game Link, not the mGBA primary.
        if (!store.isInstalled("gpsp")) {
            downloadCore("gpsp")
        }
        progress.value = CoreDownloadProgress(
            running = false,
            message = "Core download finished",
        )
    }

    private fun extractSoFromZip(zipFile: File, outSo: File): File? {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && name.endsWith(".so", ignoreCase = true)) {
                    outSo.outputStream().use { zis.copyTo(it) }
                    return outSo.takeIf { it.length() > 0L }
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private companion object {
        const val TAG = "CoreDownloader"
    }
}
