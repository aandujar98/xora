package com.arcadia.shell.feature.home.rss

import com.arcadia.shell.feature.home.RssFeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class RssFeed(
    val title: String,
    val items: List<RssFeedItem>,
)

/**
 * Fetches and parses a public RSS/Atom feed. Defaults to Nintendo Life gaming news — no API key.
 */
@Singleton
class RssFeedClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    suspend fun fetch(url: String = DEFAULT_FEED_URL): Result<RssFeed> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Could not load feed (HTTP ${response.code}).")
                }
                val body = response.body.string()
                if (body.isBlank()) error("Feed was empty.")
                parseFeed(body)
            }
        }
    }

    companion object {
        /** Well-known public gaming news RSS; works without credentials. */
        const val DEFAULT_FEED_URL = "https://www.nintendolife.com/feeds/latest"

        private const val USER_AGENT = "SORA/1.0 (Android; Arcadia Shell)"
    }
}

internal fun parseFeed(xml: String): RssFeed {
    val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
    val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

    var channelTitle = "News"
    val items = mutableListOf<RssFeedItem>()

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
            when (parser.name.lowercase(Locale.US)) {
                "channel" -> {
                    val channel = parseRssChannel(parser)
                    channelTitle = channel.title.ifBlank { channelTitle }
                    items += channel.items
                }
                "feed" -> {
                    val atom = parseAtomFeed(parser)
                    channelTitle = atom.title.ifBlank { channelTitle }
                    items += atom.items
                }
                "item" -> items += parseRssItem(parser, channelTitle)
                "entry" -> items += parseAtomEntry(parser, channelTitle)
            }
        }
        event = parser.next()
    }

    return RssFeed(
        title = channelTitle,
        items = items.distinctBy { it.link.ifBlank { it.id } }.take(MAX_ITEMS),
    )
}

private data class ParsedChannel(val title: String, val items: List<RssFeedItem>)

private fun parseRssChannel(parser: XmlPullParser): ParsedChannel {
    var title = ""
    val items = mutableListOf<RssFeedItem>()
    while (true) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.US)) {
                "title" -> if (title.isBlank()) title = parser.nextText().orEmpty().trim()
                "item" -> items += parseRssItem(parser, title.ifBlank { "News" })
            }
            XmlPullParser.END_TAG -> if (parser.name.equals("channel", ignoreCase = true)) break
            XmlPullParser.END_DOCUMENT -> break
        }
    }
    return ParsedChannel(title, items)
}

private fun parseAtomFeed(parser: XmlPullParser): ParsedChannel {
    var title = ""
    val items = mutableListOf<RssFeedItem>()
    while (true) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.US)) {
                "title" -> if (title.isBlank()) title = parser.nextText().orEmpty().trim()
                "entry" -> items += parseAtomEntry(parser, title.ifBlank { "News" })
            }
            XmlPullParser.END_TAG -> if (parser.name.equals("feed", ignoreCase = true)) break
            XmlPullParser.END_DOCUMENT -> break
        }
    }
    return ParsedChannel(title, items)
}

private fun parseRssItem(parser: XmlPullParser, source: String): RssFeedItem {
    var title = ""
    var link = ""
    var pubDate: String? = null
    var description: String? = null
    var imageUrl: String? = null
    var videoUrl: String? = null
    var guid: String? = null

    while (true) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> {
                val name = parser.name.lowercase(Locale.US)
                when (name) {
                    "title" -> title = parser.nextText().orEmpty().trim()
                    "link" -> if (link.isBlank()) link = parser.nextText().orEmpty().trim()
                    "guid" -> guid = parser.nextText().orEmpty().trim()
                    "pubdate", "published", "updated", "dc:date" ->
                        pubDate = parser.nextText().orEmpty().trim().ifBlank { null }
                    "description", "content:encoded", "summary" ->
                        description = parser.nextText().orEmpty()
                    "enclosure" -> {
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val url = parser.getAttributeValue(null, "url")
                        if (url != null) {
                            when {
                                type.startsWith("video/") || looksLikeVideoUrl(url) ->
                                    videoUrl = videoUrl ?: url
                                type.startsWith("image/") || looksLikeImageUrl(url) ->
                                    imageUrl = imageUrl ?: url
                            }
                        }
                    }
                    "thumbnail", "mediathumbnail" -> {
                        val url = parser.getAttributeValue(null, "url")
                            ?: parser.getAttributeValue(null, "href")
                        if (url != null && looksLikeImageUrl(url)) {
                            imageUrl = imageUrl ?: url
                        }
                    }
                    "content", "mediacontent" -> {
                        val url = parser.getAttributeValue(null, "url")
                            ?: parser.getAttributeValue(null, "href")
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val medium = parser.getAttributeValue(null, "medium").orEmpty()
                        if (url != null) {
                            when {
                                medium == "video" || type.startsWith("video/") ||
                                    looksLikeVideoUrl(url) -> videoUrl = videoUrl ?: url
                                medium == "image" || type.startsWith("image/") ||
                                    looksLikeImageUrl(url) -> imageUrl = imageUrl ?: url
                            }
                        }
                    }
                }
            }
            XmlPullParser.END_TAG -> if (parser.name.equals("item", ignoreCase = true)) break
            XmlPullParser.END_DOCUMENT -> break
        }
    }

    if (imageUrl == null) {
        imageUrl = extractImageFromHtml(description)
    }
    if (videoUrl == null) {
        videoUrl = extractVideoFromHtml(description)
    }

    val id = guid?.takeIf { it.isNotBlank() } ?: link.ifBlank { title }
    return RssFeedItem(
        id = id,
        title = title.ifBlank { "Untitled" },
        link = link,
        source = source,
        publishedAt = formatDate(pubDate),
        imageUrl = imageUrl,
        description = cleanDescription(description),
        videoUrl = videoUrl,
    )
}

private fun parseAtomEntry(parser: XmlPullParser, source: String): RssFeedItem {
    var title = ""
    var link = ""
    var published: String? = null
    var summary: String? = null
    var imageUrl: String? = null
    var videoUrl: String? = null
    var id: String? = null

    while (true) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> {
                val name = parser.name.lowercase(Locale.US)
                when (name) {
                    "title" -> title = parser.nextText().orEmpty().trim()
                    "id" -> id = parser.nextText().orEmpty().trim()
                    "link" -> {
                        val rel = parser.getAttributeValue(null, "rel").orEmpty()
                        val href = parser.getAttributeValue(null, "href")
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        if (href != null) {
                            when {
                                type.startsWith("video/") || looksLikeVideoUrl(href) ->
                                    videoUrl = videoUrl ?: href
                                type.startsWith("image/") || looksLikeImageUrl(href) ->
                                    imageUrl = imageUrl ?: href
                                rel.isEmpty() || rel == "alternate" ->
                                    if (link.isBlank()) link = href
                            }
                        }
                    }
                    "published", "updated" ->
                        published = parser.nextText().orEmpty().trim().ifBlank { null }
                    "summary", "content" -> summary = parser.nextText().orEmpty()
                }
            }
            XmlPullParser.END_TAG -> if (parser.name.equals("entry", ignoreCase = true)) break
            XmlPullParser.END_DOCUMENT -> break
        }
    }

    if (imageUrl == null) {
        imageUrl = extractImageFromHtml(summary)
    }
    if (videoUrl == null) {
        videoUrl = extractVideoFromHtml(summary)
    }

    val resolvedId = id?.takeIf { it.isNotBlank() } ?: link.ifBlank { title }
    return RssFeedItem(
        id = resolvedId,
        title = title.ifBlank { "Untitled" },
        link = link,
        source = source,
        publishedAt = formatDate(published),
        imageUrl = imageUrl,
        description = cleanDescription(summary),
        videoUrl = videoUrl,
    )
}

private fun extractImageFromHtml(html: String?): String? {
    if (html.isNullOrBlank()) return null
    val match = IMG_SRC_REGEX.find(html) ?: return null
    return match.groupValues.getOrNull(1)?.takeIf { looksLikeImageUrl(it) }
}

private fun extractVideoFromHtml(html: String?): String? {
    if (html.isNullOrBlank()) return null
    IFRAME_SRC_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { src ->
        if (looksLikeVideoUrl(src) || src.contains("youtube", ignoreCase = true)) return src
    }
    YOUTUBE_URL_REGEX.find(html)?.value?.let { return it }
    return null
}

private fun cleanDescription(html: String?): String? {
    if (html.isNullOrBlank()) return null
    return html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
        .replace(Regex("&quot;", RegexOption.IGNORE_CASE), "\"")
        .replace(Regex("&#39;"), "'")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(480)
        .ifBlank { null }
}

private fun looksLikeImageUrl(url: String): Boolean {
    val lower = url.lowercase(Locale.US)
    return lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".png") ||
        lower.endsWith(".webp") ||
        lower.endsWith(".gif") ||
        "image" in lower ||
        "/media/" in lower ||
        "/thumb" in lower
}

private fun looksLikeVideoUrl(url: String): Boolean {
    val lower = url.lowercase(Locale.US)
    return lower.endsWith(".mp4") ||
        lower.endsWith(".webm") ||
        lower.endsWith(".m3u8") ||
        "youtube.com" in lower ||
        "youtu.be" in lower ||
        "vimeo.com" in lower
}

private fun formatDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    // Keep the feed's own short form readable; full RFC parsing is unnecessary for a hint line.
    return raw
        .removePrefix("Published: ")
        .take(32)
        .trim()
        .ifBlank { null }
}

private val IMG_SRC_REGEX = Regex(
    """<img[^>]+src=["']([^"']+)["']""",
    RegexOption.IGNORE_CASE,
)

private val IFRAME_SRC_REGEX = Regex(
    """<iframe[^>]+src=["']([^"']+)["']""",
    RegexOption.IGNORE_CASE,
)

private val YOUTUBE_URL_REGEX = Regex(
    """https?://(?:www\.)?(?:youtube\.com/watch\?v=|youtu\.be/)[\w\-]+""",
    RegexOption.IGNORE_CASE,
)

private const val MAX_ITEMS = 48
