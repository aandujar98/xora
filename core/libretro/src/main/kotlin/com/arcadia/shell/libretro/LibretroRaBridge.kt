package com.arcadia.shell.libretro

import android.util.Log
import androidx.annotation.Keep
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * JNI-facing RetroAchievements HTTP + event sink for [LibretroNative].
 * Methods are called from native threads; keep them allocation-light and exception-safe.
 */
class LibretroRaBridge(
    httpClient: OkHttpClient,
    private val onUnlocked: (
        id: Int,
        title: String,
        description: String,
        points: Int,
        badgeUrl: String,
        hardcore: Boolean,
    ) -> Unit,
    private val onStatusChanged: (String) -> Unit,
) {
    private val http = httpClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build(),
            )
        }
        .build()

    @Keep
    fun performHttp(url: String, postData: String?, contentType: String?): Array<Any> {
        return runCatching {
            val builder = Request.Builder()
                .url(url)
                // Must match launcher RA client — Cloudflare 403s unknown/empty UAs on dorequest.php.
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
            if (postData != null) {
                val media = (contentType ?: "application/x-www-form-urlencoded")
                    .toMediaTypeOrNull()
                builder.post(postData.toRequestBody(media))
            } else {
                builder.get()
            }
            http.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "RA HTTP ${response.code} for $url (${body.take(120)})")
                }
                arrayOf<Any>(Integer.valueOf(response.code), body)
            }
        }.getOrElse { error ->
            Log.w(TAG, "RA HTTP failed: ${error.message}")
            arrayOf<Any>(Integer.valueOf(-1), error.message.orEmpty())
        }
    }

    @Keep
    fun onAchievementUnlocked(
        id: Int,
        title: String?,
        description: String?,
        points: Int,
        badgeUrl: String?,
        hardcore: Boolean,
    ) {
        onUnlocked(
            id,
            title.orEmpty(),
            description.orEmpty(),
            points,
            badgeUrl.orEmpty(),
            hardcore,
        )
    }

    @Keep
    fun onStatus(message: String?) {
        if (!message.isNullOrBlank()) onStatusChanged(message)
    }

    private companion object {
        const val TAG = "LibretroRA"
        /** Same UA as [com.arcadia.shell.retroachievements.RetroAchievementsClient]. */
        const val USER_AGENT = "XOrA/1.0.0"
    }
}
