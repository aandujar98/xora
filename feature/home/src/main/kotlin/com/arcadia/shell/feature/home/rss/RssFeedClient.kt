package com.arcadia.shell.feature.home.rss

import com.arcadia.shell.feature.home.ArticleBlock
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
    /** Channel artwork, when the feed publishes one — the outlet bubble's picture. */
    val imageUrl: String? = null,
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
    var channelImage: String? = null
    val items = mutableListOf<RssFeedItem>()

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
            when (parser.name.lowercase(Locale.US)) {
                "channel" -> {
                    val channel = parseRssChannel(parser)
                    channelTitle = channel.title.ifBlank { channelTitle }
                    channelImage = channelImage ?: channel.imageUrl
                    items += channel.items
                }
                "feed" -> {
                    val atom = parseAtomFeed(parser)
                    channelTitle = atom.title.ifBlank { channelTitle }
                    channelImage = channelImage ?: atom.imageUrl
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
        imageUrl = channelImage,
    )
}

private data class ParsedChannel(
    val title: String,
    val items: List<RssFeedItem>,
    val imageUrl: String? = null,
)

private fun parseRssChannel(parser: XmlPullParser): ParsedChannel {
    var title = ""
    var imageUrl: String? = null
    val items = mutableListOf<RssFeedItem>()
    while (true) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.US)) {
                "title" -> if (title.isBlank()) title = parser.nextText().orEmpty().trim()
                // <image><url> is the channel logo. Only take it before any <item>, so an
                // article's own image cannot be mistaken for the outlet's.
                "image" -> if (items.isEmpty()) {
                    imageUrl = imageUrl ?: parseChannelImage(parser)
                }
                "icon", "logo" -> if (items.isEmpty() && imageUrl == null) {
                    imageUrl = parser.nextText().orEmpty().trim().takeIf { it.startsWith("http") }
                }
                "item" -> items += parseRssItem(parser, title.ifBlank { "News" })
            }
            XmlPullParser.END_TAG -> if (parser.name.equals("channel", ignoreCase = true)) break
            XmlPullParser.END_DOCUMENT -> break
        }
    }
    return ParsedChannel(title, items, imageUrl)
}

private fun parseChannelImage(parser: XmlPullParser): String? {
    var url: String? = null
    while (true) {
        when (parser.next()) {
            XmlPullParser.START_TAG ->
                if (parser.name.equals("url", ignoreCase = true)) {
                    url = parser.nextText().orEmpty().trim().takeIf { it.startsWith("http") }
                }
            XmlPullParser.END_TAG -> if (parser.name.equals("image", ignoreCase = true)) break
            XmlPullParser.END_DOCUMENT -> break
        }
    }
    return url
}

private fun parseAtomFeed(parser: XmlPullParser): ParsedChannel {
    var title = ""
    var imageUrl: String? = null
    val items = mutableListOf<RssFeedItem>()
    while (true) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.US)) {
                "title" -> if (title.isBlank()) title = parser.nextText().orEmpty().trim()
                "icon", "logo" -> if (items.isEmpty() && imageUrl == null) {
                    imageUrl = parser.nextText().orEmpty().trim().takeIf { it.startsWith("http") }
                }
                "entry" -> items += parseAtomEntry(parser, title.ifBlank { "News" })
            }
            XmlPullParser.END_TAG -> if (parser.name.equals("feed", ignoreCase = true)) break
            XmlPullParser.END_DOCUMENT -> break
        }
    }
    return ParsedChannel(title, items, imageUrl)
}

private fun parseRssItem(parser: XmlPullParser, source: String): RssFeedItem {
    var title = ""
    var link = ""
    var pubDate: String? = null
    var description: String? = null
    var contentHtml: String? = null
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
                    // content:encoded is the whole article when a feed ships it; description is
                    // usually just the teaser. Keep both so the reader is not stuck with the teaser.
                    "description", "summary" -> description = parser.nextText().orEmpty()
                    "content:encoded", "encoded" -> contentHtml = parser.nextText().orEmpty()
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

    val body = contentHtml?.takeIf { it.isNotBlank() } ?: description
    if (imageUrl == null) {
        imageUrl = extractImageFromHtml(body)
    }
    if (videoUrl == null) {
        videoUrl = extractVideoFromHtml(body)
    }

    val id = guid?.takeIf { it.isNotBlank() } ?: link.ifBlank { title }
    return RssFeedItem(
        id = id,
        title = title.ifBlank { "Untitled" },
        link = link,
        source = source,
        publishedAt = formatDate(pubDate),
        imageUrl = imageUrl,
        description = cleanDescription(description ?: contentHtml),
        videoUrl = videoUrl,
        blocks = articleBlocks(body),
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
        blocks = articleBlocks(summary),
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

/**
 * Split feed HTML into the reader's paragraph / image stream, in document order.
 *
 * Deliberately a small regex pass rather than a real HTML parser: feed bodies are a narrow,
 * well-behaved subset, and pulling in a parser to read a news item is not a trade worth making.
 * Anything it cannot classify ends up as text, which is the safe direction to fail.
 */
internal fun articleBlocks(html: String?): List<ArticleBlock> {
    if (html.isNullOrBlank()) return emptyList()
    val blocks = mutableListOf<ArticleBlock>()
    val imgPattern = Regex("<img[^>]+>", RegexOption.IGNORE_CASE)
    var cursor = 0
    for (match in imgPattern.findAll(html)) {
        appendArticleText(blocks, html.substring(cursor, match.range.first))
        val src = Regex("src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(match.value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!src.isNullOrBlank() && src.startsWith("http")) {
            blocks += ArticleBlock.Image(src)
        }
        cursor = match.range.last + 1
    }
    appendArticleText(blocks, html.substring(cursor))
    return blocks.take(MAX_ARTICLE_BLOCKS)
}

private const val MAX_ARTICLE_BLOCKS = 80

private fun appendArticleText(blocks: MutableList<ArticleBlock>, raw: String) {
    if (raw.isBlank()) return
    raw.split(Regex("(?i)</p>|<br\\s*/?>"))
        .map { stripHtml(it) }
        .filter { it.isNotBlank() }
        .forEach { blocks += ArticleBlock.Text(it) }
}

private fun stripHtml(html: String): String = html
    .replace(Regex("<[^>]+>"), " ")
    .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
    .replace(Regex("&quot;", RegexOption.IGNORE_CASE), "\"")
    .replace(Regex("&#8217;|&rsquo;"), "'")
    .replace(Regex("&#8216;|&lsquo;"), "'")
    .replace(Regex("&#8220;|&ldquo;|&#8221;|&rdquo;"), "\"")
    .replace(Regex("&#39;"), "'")
    .replace(Regex("&#\\d+;"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

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
