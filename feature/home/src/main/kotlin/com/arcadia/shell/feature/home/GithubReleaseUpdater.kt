package com.arcadia.shell.feature.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
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
 *
 * Handhelds often fail DNS for `api.github.com` while `github.com` still works, so the check
 * tries the API first and falls back to the public releases Atom feed.
 */
@Singleton
class GithubReleaseUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    http: OkHttpClient,
) {
    private val busy = AtomicBoolean(false)
    private val shared = http.newBuilder()
        .dns(Ipv4FirstDns)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
    private val apiHttp = shared
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
    private val downloadHttp = shared
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(30, TimeUnit.MINUTES)
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
        }.recoverCatching { throw friendlyNetworkError(it) }
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
                    val urls = downloadUrls(release)
                    var lastError: Throwable? = null
                    var downloaded = false
                    for (url in urls) {
                        val attempt = runCatching {
                            get(downloadHttp, url, accept = "*/*").use { response ->
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
                        }
                        if (attempt.isSuccess) {
                            downloaded = true
                            break
                        }
                        lastError = attempt.exceptionOrNull()
                        tmp.delete()
                    }
                    if (!downloaded) throw lastError ?: error("Download failed.")
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
                }.recoverCatching { throw friendlyNetworkError(it) }
            }
        } finally {
            busy.set(false)
        }
    }

    private fun fetchLatestApkRelease(): GithubApkRelease {
        val attempts = listOf(
            { parseRelease(apiJsonObject("$API_BASE/releases/latest")) },
            {
                val list = apiJsonArray("$API_BASE/releases?per_page=20")
                val parsed = (0 until list.length())
                    .mapNotNull { runCatching { parseRelease(list.getJSONObject(it)) }.getOrNull() }
                parsed.maxWithOrNull { a, b -> compareVersions(a.versionName, b.versionName) }
                    ?: error("No APK on the latest GitHub release.")
            },
            {
                parseAtomRelease(
                    get(apiHttp, ATOM_URL, accept = "application/atom+xml, application/xml, text/xml")
                        .use { response ->
                            if (!response.isSuccessful) githubHttpError(response.code)
                            response.body.string()
                        },
                )
            },
            { parseLatestRedirect() },
        )
        var last: Throwable? = null
        for (attempt in attempts) {
            val result = runCatching { attempt() }
            result.getOrNull()?.let { return it }
            last = result.exceptionOrNull()
        }
        throw last ?: error("Could not fetch the latest GitHub release.")
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

    /**
     * GitHub's public Atom feed lives on github.com, not api.github.com. Newest entry first.
     * APK names follow `XOrA-{version}-release.apk`.
     */
    private fun parseAtomRelease(xml: String): GithubApkRelease {
        val entry = ENTRY_REGEX.findAll(xml)
            .map { it.value }
            .firstOrNull { !it.contains("FONTS", ignoreCase = true) }
            ?: error("GitHub release feed was empty.")
        val tag = TAG_LINK_REGEX.find(entry)?.groupValues?.get(1)?.trim()
            ?: TITLE_REGEX.find(entry)?.groupValues?.get(1)?.trim()
            ?: error("Could not read the latest release.")
        return releaseFromTag(tag)
    }

    /** `/releases/latest` 302s to `/releases/tag/vX.Y.Z` without needing the JSON API. */
    private fun parseLatestRedirect(): GithubApkRelease {
        val client = apiHttp.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        get(client, "$REPO_WEB/releases/latest", accept = "text/html").use { response ->
            val location = response.header("Location").orEmpty()
            val tag = location.substringAfterLast('/').substringBefore('?').trim()
            if (tag.isBlank() || tag.equals("latest", ignoreCase = true)) {
                error("Could not follow the latest GitHub release.")
            }
            return releaseFromTag(tag)
        }
    }

    private fun releaseFromTag(tag: String): GithubApkRelease {
        val version = normalizeVersion(tag)
        if (version.isBlank()) error("Could not read the latest release.")
        val assetName = "XOrA-$version-release.apk"
        return GithubApkRelease(
            tag = tag,
            versionName = version,
            assetName = assetName,
            downloadUrl = "$REPO_WEB/releases/download/$tag/$assetName",
        )
    }

    private fun downloadUrls(release: GithubApkRelease): List<String> {
        val tag = release.tag.ifBlank { "v${release.versionName}" }
        val asset = release.assetName.ifBlank { "XOrA-${release.versionName}-release.apk" }
        return listOf(
            release.downloadUrl,
            "$REPO_WEB/releases/download/$tag/$asset",
            "https://github.com/aandujar98/xora/releases/download/v${release.versionName}/$asset",
        ).distinct()
    }

    private fun apiJsonObject(url: String): JSONObject =
        JSONObject(get(apiHttp, url).use { response ->
            if (!response.isSuccessful) githubHttpError(response.code)
            response.body.string()
        })

    private fun apiJsonArray(url: String): JSONArray =
        JSONArray(get(apiHttp, url).use { response ->
            if (!response.isSuccessful) githubHttpError(response.code)
            response.body.string()
        })

    private fun get(
        client: OkHttpClient,
        url: String,
        accept: String = "application/vnd.github+json",
    ): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", accept)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                return client.newCall(request).execute()
            } catch (error: UnknownHostException) {
                last = error
                if (attempt == 0) Thread.sleep(DNS_RETRY_MS)
            }
        }
        throw last ?: UnknownHostException(url)
    }

    private fun githubHttpError(code: Int): Nothing = error(
        if (code == 404) {
            "Could not fetch the latest GitHub release."
        } else {
            "GitHub HTTP $code"
        },
    )

    companion object {
        private const val API_BASE = "https://api.github.com/repos/aandujar98/xora"
        private const val REPO_WEB = "https://github.com/aandujar98/xora"
        private const val ATOM_URL = "$REPO_WEB/releases.atom"
        private const val USER_AGENT = "XOrA/1.0 (Android; https://github.com/aandujar98/xora)"
        private const val MIN_APK_BYTES = 1_000_000L
        private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        /** Coalesce progress callbacks so a 120 MB APK does not spam recomposition. */
        private const val PROGRESS_STEP_BYTES = 512 * 1024L
        private const val DNS_RETRY_MS = 400L
        private val ENTRY_REGEX = Regex("<entry[\\s\\S]*?</entry>", RegexOption.IGNORE_CASE)
        private val TAG_LINK_REGEX =
            Regex("""releases/tag/([^"'<\s]+)""", RegexOption.IGNORE_CASE)
        private val TITLE_REGEX = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)

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

        internal fun friendlyNetworkError(error: Throwable): Throwable {
            val chain = generateSequence(error) { it.cause }.toList()
            val dns = chain.any { it is UnknownHostException } ||
                chain.any {
                    it.message.orEmpty().contains("Unable to resolve host", ignoreCase = true)
                }
            if (dns) {
                return IllegalStateException(
                    "Can't reach GitHub. Check Wi-Fi, then try again.",
                    error,
                )
            }
            return error
        }
    }
}

/**
 * Many handheld firmwares try AAAA first and never recover when IPv6 is broken, which is the
 * usual “Unable to resolve host api.github.com” on a device that otherwise has internet.
 */
private object Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.size <= 1) return addresses
        return addresses.sortedBy { address -> if (address is Inet4Address) 0 else 1 }
    }
}
