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
)

sealed interface GithubUpdateResult {
    data class AlreadyCurrent(val versionName: String) : GithubUpdateResult
    data class Downloaded(val apk: File, val versionName: String) : GithubUpdateResult
}

/**
 * Pulls the newest APK asset from [aandujar98/xora] GitHub Releases.
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
        .callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val isBusy: Boolean get() = busy.get()

    suspend fun downloadLatest(installedVersionName: String?): Result<GithubUpdateResult> {
        if (!busy.compareAndSet(false, true)) {
            return Result.failure(IllegalStateException("An update is already downloading."))
        }
        return try {
            withContext(Dispatchers.IO) {
                val release = fetchLatestApkRelease()
                val remote = release.versionName
                if (!installedVersionName.isNullOrBlank() &&
                    normalizeVersion(installedVersionName) == normalizeVersion(remote)
                ) {
                    return@withContext Result.success(GithubUpdateResult.AlreadyCurrent(remote))
                }
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "XOrA-latest.apk")
                val tmp = File(dir, "XOrA-latest.apk.part")
                tmp.delete()
                apiGet(downloadHttp, release.downloadUrl, accept = "*/*").use { response ->
                    if (!response.isSuccessful) {
                        error("Download failed (HTTP ${response.code}).")
                    }
                    val body = response.body
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
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
                Result.success(GithubUpdateResult.Downloaded(apk, remote))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            busy.set(false)
        }
    }

    private fun fetchLatestApkRelease(): GithubApkRelease {
        runCatching { parseRelease(apiJsonObject("$API_BASE/releases/latest")) }
            .getOrNull()
            ?.let { return it }
        val list = apiJsonArray("$API_BASE/releases?per_page=20")
        for (i in 0 until list.length()) {
            val parsed = runCatching { parseRelease(list.getJSONObject(i)) }.getOrNull()
            if (parsed != null) return parsed
        }
        error("No APK on the latest GitHub release.")
    }

    private fun parseRelease(json: JSONObject): GithubApkRelease {
        val tag = json.optString("tag_name").ifBlank { json.optString("name") }
        if (tag.equals("FONTS", ignoreCase = true)) error("skip fonts tag")
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

        fun normalizeVersion(raw: String): String =
            raw.trim().removePrefix("v").removePrefix("V")
                .removePrefix("XOrA ")
                .substringBefore("-release")
                .substringBefore(" ")
                .trim()
    }
}
