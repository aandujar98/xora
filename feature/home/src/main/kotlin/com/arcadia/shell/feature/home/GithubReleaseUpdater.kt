package com.arcadia.shell.feature.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class GithubApkRelease(
    val tag: String,
    val versionName: String,
    val assetName: String,
    val downloadUrl: String,
    /** Asset size from the GitHub API, or 0 when the release did not report one. */
    val sizeBytes: Long = 0L,
    val notes: String = "",
)

/** Outcome of a version-only check against GitHub Releases (nothing is downloaded). */
data class GithubUpdateCheck(
    val release: GithubApkRelease,
    val installedVersionName: String,
    val updateAvailable: Boolean,
)

/**
 * Pulls the newest APK asset from [aandujar98/xora] GitHub Releases.
 *
 * Checking and downloading are separate so the shell can compare versions cheaply on resume
 * (and notify) without pulling a ~120 MB APK the user never asked for.
 */
@Singleton
class GithubReleaseUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    http: OkHttpClient,
) {
    private val busy = AtomicBoolean(false)
    private val apiHttp = http.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val downloadHttp = http.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(30, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val isBusy: Boolean get() = busy.get()

    /** Version name of the running build, or a blank string when PackageManager refuses. */
    fun installedVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.let { normalizeVersion(it) }.orEmpty()

    /** Reads the newest release and compares it to the installed build. Downloads nothing. */
    suspend fun check(): Result<GithubUpdateCheck> = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatestApkRelease()
            val installed = installedVersionName()
            GithubUpdateCheck(
                release = release,
                installedVersionName = installed,
                updateAvailable = isNewer(release.versionName, installed),
            )
        }
    }

    /**
     * Streams [release] into the update cache, reporting `(bytesRead, totalBytes)` as it goes.
     * `totalBytes` is 0 while the size is unknown.
     */
    suspend fun download(
        release: GithubApkRelease,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<File> {
        if (!busy.compareAndSet(false, true)) {
            return Result.failure(IllegalStateException("An update is already downloading."))
        }
        return try {
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val apk = File(dir, "XOrA-latest.apk")
                    val tmp = File(dir, "XOrA-latest.apk.part")
                    tmp.delete()
                    apiGet(downloadHttp, release.downloadUrl, accept = "*/*").use { response ->
                        if (!response.isSuccessful) {
                            error("Download failed (HTTP ${response.code}).")
                        }
                        val body = response.body
                        val total = body.contentLength()
                            .takeIf { it > 0L }
                            ?: release.sizeBytes
                        onProgress(0L, total)
                        body.byteStream().use { input ->
                            tmp.outputStream().use { output ->
                                val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                                var copied = 0L
                                var lastReported = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    copied += read
                                    if (copied - lastReported >= PROGRESS_STEP_BYTES) {
                                        lastReported = copied
                                        onProgress(copied, total)
                                    }
                                }
                                output.flush()
                                onProgress(copied, total)
                            }
                        }
                    }
                    if (tmp.length() < MIN_APK_BYTES) {
                        tmp.delete()
                        error("Downloaded file was too small to be an APK.")
                    }
                    if (apk.exists()) apk.delete()
                    if (!tmp.renameTo(apk)) {
                        tmp.copyTo(apk, overwrite = true)
                        tmp.delete()
                    }
                    apk
                }
            }
        } finally {
            busy.set(false)
        }
    }

    private fun fetchLatestApkRelease(): GithubApkRelease {
        runCatching { parseRelease(apiJsonObject("$API_BASE/releases/latest")) }
            .getOrNull()
            ?.let { return it }
        val list = apiJsonArray("$API_BASE/releases?per_page=20")
        val parsed = (0 until list.length())
            .mapNotNull { runCatching { parseRelease(list.getJSONObject(it)) }.getOrNull() }
        // The list endpoint is creation-ordered, which is not always version order.
        parsed.maxWithOrNull { a, b -> compareVersions(a.versionName, b.versionName) }
            ?.let { return it }
        error("No APK on the latest GitHub release.")
    }

    private fun parseRelease(json: JSONObject): GithubApkRelease {
        val tag = json.optString("tag_name").ifBlank { json.optString("name") }
        if (tag.equals("FONTS", ignoreCase = true)) error("skip fonts tag")
        if (json.optBoolean("draft")) error("skip draft release")
        val assets = json.optJSONArray("assets") ?: error("Release has no assets.")
        val asset = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: error("Release has no APK.")
        val url = asset.optString("browser_download_url")
        if (url.isBlank()) error("APK has no download URL.")
        return GithubApkRelease(
            tag = tag,
            versionName = normalizeVersion(tag),
            assetName = asset.optString("name"),
            downloadUrl = url,
            sizeBytes = asset.optLong("size", 0L),
            notes = json.optString("body").trim(),
        )
    }

    private fun apiJsonObject(url: String): JSONObject =
        JSONObject(apiGet(apiHttp, url).use { response ->
            if (!response.isSuccessful) githubHttpError(response.code)
            response.body.string()
        })

    private fun apiJsonArray(url: String): JSONArray =
        JSONArray(apiGet(apiHttp, url).use { response ->
            if (!response.isSuccessful) githubHttpError(response.code)
            response.body.string()
        })

    private fun apiGet(
        client: OkHttpClient,
        url: String,
        accept: String = "application/vnd.github+json",
    ) = client.newCall(
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", accept)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build(),
    ).execute()

    private fun githubHttpError(code: Int): Nothing = error(
        if (code == 404) {
            "Could not fetch the latest GitHub release."
        } else {
            "GitHub HTTP $code"
        },
    )

    companion object {
        private const val API_BASE = "https://api.github.com/repos/aandujar98/xora"
        private const val USER_AGENT = "XOrA/1.0 (Android; https://github.com/aandujar98/xora)"
        private const val MIN_APK_BYTES = 1_000_000L
        private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        /** Coalesce progress callbacks so a 120 MB APK does not spam recomposition. */
        private const val PROGRESS_STEP_BYTES = 512 * 1024L

        fun normalizeVersion(raw: String): String =
            raw.trim().removePrefix("v").removePrefix("V")
                .removePrefix("XOrA ")
                .substringBefore("-release")
                .substringBefore(" ")
                .trim()

        /** True when [remote] is a strictly higher version than [installed]. */
        fun isNewer(remote: String, installed: String): Boolean {
            if (installed.isBlank()) return remote.isNotBlank()
            return compareVersions(remote, installed) > 0
        }

        /**
         * Numeric dotted compare so 0.2.9 sorts below 0.2.10 (a string compare says otherwise,
         * which is how an older release could look like an update).
         */
        fun compareVersions(left: String, right: String): Int {
            val a = versionParts(left)
            val b = versionParts(right)
            for (i in 0 until maxOf(a.size, b.size)) {
                val diff = (a.getOrNull(i) ?: 0).compareTo(b.getOrNull(i) ?: 0)
                if (diff != 0) return diff
            }
            return 0
        }

        private fun versionParts(raw: String): List<Int> =
            normalizeVersion(raw)
                .split('.', '-', '_')
                .mapNotNull { part ->
                    part.takeWhile { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
                }
    }
}
