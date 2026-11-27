package com.myna.player.online

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.math.abs

// Ported from FireTheDJ (com.autodj.remote.YouTubeMusic / Spotify): the same NewPipe setup, stream
// resolving and song matching, trimmed to what MYNA plays.

private const val TAG = "MYNA"

/** Link handling for YouTube tracks; no network. */
object YouTubeLinks {
    private val HOSTS = setOf("music.youtube.com", "youtube.com", "m.youtube.com", "youtu.be")
    private val VIDEO_ID = Regex("""[A-Za-z0-9_\-]{11}""")
    private val UPLOAD_NOTE = Regex(
        """\s*[(\[](?:official\s*)?(?:music\s*)?(?:video|audio|lyrics?|lyric\s*video|visualizer|hd|hq|4k)[)\]]""",
        RegexOption.IGNORE_CASE,
    )

    fun watchUrl(videoId: String) = "https://music.youtube.com/watch?v=$videoId"

    fun videoId(link: String): String? {
        val uri = runCatching { URI(link) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host !in HOSTS) return null
        val id = if (host == "youtu.be") uri.rawPath?.removePrefix("/")
        else if (uri.rawPath != "/watch") null else queryParams(uri.rawQuery)["v"]
        return id?.takeIf { VIDEO_ID.matches(it) }
    }

    fun cleanTitle(name: String) = name.replace(UPLOAD_NOTE, "").trim().ifEmpty { name.trim() }
}

fun queryParams(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrEmpty()) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (pair in rawQuery.split('&')) {
        if (pair.isEmpty()) continue
        out.putIfAbsent(dec(pair.substringBefore('=')), dec(pair.substringAfter('=', "")))
    }
    return out
}

private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

/** `myna-match:` songs (Spotify / Apple Music) are looked up on YouTube when they play. */
object SongLinks {
    const val MATCH = "myna-match://song"
    private val matched = ConcurrentHashMap<String, String>()

    /** The YouTube video for a song's uri, looking a Spotify / Apple Music song up once. Blocking. */
    fun videoIdFor(uri: String): String? {
        YouTubeLinks.videoId(uri)?.let { return it }
        if (!uri.startsWith(MATCH)) return null
        matched[uri]?.let { return it }
        val p = queryParams(uri.substringAfter('?', ""))
        val hit = YouTubeSearch.match(p["t"].orEmpty(), p["a"].orEmpty(), p["d"]?.toDoubleOrNull())
            ?: throw IOException("Couldn't find this song on YouTube")
        matched[uri] = hit.videoId
        return hit.videoId
    }
}

class ResolvedStream(val url: String, val headers: Map<String, String>, val contentLength: Long?)

/** A YouTube result: what a search or a song match gives back. */
class YtHit(val videoId: String, val title: String, val artist: String, val durationSec: Double?, val thumb: String?)

/**
 * Turns a YouTube video id into a playable audio URL with NewPipeExtractor, which solves the
 * signature cipher and the `n` throttling parameter. One extraction at a time; results are kept
 * for 20 minutes so seeks and the crossfade's early open don't extract again.
 */
object YouTubeStreams {
    private const val ATTEMPTS = 3
    private const val TTL_MS = 20 * 60 * 1000L
    private val gate = Any()
    private val recent = ConcurrentHashMap<String, Pair<ResolvedStream, Long>>()
    private val init by lazy { NewPipe.init(NewPipeDownloader(), Localization.DEFAULT) }

    /** NewPipe 0.26 needs Android 13 (it uses URLEncoder.encode(String, Charset)); Android sets this. */
    @Volatile var supported: () -> Boolean = { true }

    fun requireSupported() {
        if (!supported()) throw IOException("Online music needs Android 13 or newer")
        init
    }

    /** Blocking; run off the main thread. */
    fun resolve(videoId: String): ResolvedStream {
        cached(videoId)?.let { return it }
        requireSupported()
        synchronized(gate) {
            cached(videoId)?.let { return it }
            var failure: Exception? = null
            repeat(ATTEMPTS) { attempt ->
                if (attempt > 0) Thread.sleep(1000L * attempt)
                try {
                    val s = extract(videoId)
                    if (recent.size > 64) recent.clear()
                    recent[videoId] = s to System.currentTimeMillis()
                    return s
                } catch (e: ContentNotAvailableException) {
                    throw IOException(e.message ?: "This song isn't available", e)
                } catch (e: LinkageError) {
                    throw IOException("YouTube can't run on this device: $e", e)
                } catch (e: Exception) {
                    System.err.println("$TAG YouTube $videoId: attempt ${attempt + 1} failed: ${e.message}")
                    failure = e
                }
            }
            throw IOException("Couldn't get audio from YouTube: ${failure?.message}", failure)
        }
    }

    fun forgetUrl(url: String) {
        recent.entries.removeAll { it.value.first.url == url }
    }

    private fun cached(videoId: String) =
        recent[videoId]?.takeIf { System.currentTimeMillis() - it.second < TTL_MS }?.first

    /** Some videos only give apps a ~1 minute preview; then another full-length upload is played. */
    private fun extract(videoId: String, allowSwap: Boolean = true): ResolvedStream {
        val ex = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        ex.fetchPage()
        val candidates = ex.audioStreams.filter {
            it.isUrl && !it.content.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        // AAC in MP4 first: its index makes seeks exact. Opus in WebM otherwise.
        val pick = best(candidates, MediaFormat.M4A) ?: best(candidates, MediaFormat.WEBMA_OPUS)
            ?: throw IOException("No playable audio for this song")
        val url = pick.content
        val params = queryParams(URI(url).rawQuery)
        val songSec = runCatching { ex.length }.getOrDefault(0L).toDouble()
        val audioSec = params["dur"]?.toDoubleOrNull()
        if (audioSec != null && songSec > 0 && audioSec < songSec * 0.6 && songSec - audioSec > 30) {
            if (!allowSwap) throw IOException("Only a preview")
            val q = listOf(runCatching { ex.name }.getOrNull().orEmpty(), runCatching { ex.uploaderName }.getOrNull().orEmpty().removeSuffix(" - Topic"))
                .filter { it.isNotBlank() }.joinToString(" ")
            val swap = YouTubeSearch.search(q, null, 6)
                .filter { it.videoId != videoId && it.durationSec != null && abs(it.durationSec - songSec) <= 15 }
                .take(3).firstNotNullOfOrNull { runCatching { extract(it.videoId, false) }.getOrNull() }
            return swap ?: throw IOException("YouTube only gives a preview of this song")
        }
        return ResolvedStream(url, mediaHeaders(url), params["clen"]?.toLongOrNull())
    }

    private fun best(streams: List<AudioStream>, format: MediaFormat) =
        streams.filter { it.format == format }.maxByOrNull { it.averageBitrate }

    /** googlevideo checks that the client named in the URL (`c=`) is the one fetching the bytes. */
    private fun mediaHeaders(url: String): Map<String, String> {
        val client = queryParams(URI(url).rawQuery)["c"]?.uppercase().orEmpty()
        val loc = NewPipe.getPreferredLocalization()
        return when {
            client.startsWith("IOS") -> mapOf("User-Agent" to YoutubeParsingHelper.getIosUserAgent(loc))
            client == "VISIONOS" -> mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(loc))
            client.startsWith("ANDROID") -> mapOf("User-Agent" to YoutubeParsingHelper.getAndroidUserAgent(loc))
            client == "WEB_REMIX" -> web("https://music.youtube.com")
            else -> web("https://www.youtube.com")
        }
    }

    private fun web(origin: String) = mapOf("User-Agent" to BROWSER_AGENT, "Origin" to origin, "Referer" to "$origin/")
}

object YouTubeSearch {
    /** YouTube Music songs (not videos) for a query. Blocking. */
    fun songs(query: String, max: Int): List<YtHit> =
        search(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, max * 2)
            .filter { (it.durationSec ?: 200.0) in 60.0..600.0 }
            .take(max)

    fun search(query: String, filter: String?, max: Int): List<YtHit> {
        YouTubeStreams.requireSupported()
        val ex = ServiceList.YouTube.getSearchExtractor(query, listOfNotNull(filter), "").apply { fetchPage() }
        return ex.initialPage.items.filterIsInstance<StreamInfoItem>().asSequence()
            .filter { it.streamType != StreamType.LIVE_STREAM && it.streamType != StreamType.AUDIO_LIVE_STREAM }
            .mapNotNull { item ->
                val id = YouTubeLinks.videoId(item.url) ?: return@mapNotNull null
                YtHit(
                    id, YouTubeLinks.cleanTitle(item.name.orEmpty()),
                    item.uploaderName.orEmpty().removeSuffix(" - Topic").trim(),
                    item.duration.takeIf { it > 0 }?.toDouble(),
                    item.thumbnails.maxByOrNull { it.width.toLong() * it.height }?.url?.takeIf { it.startsWith("https://") },
                )
            }
            .distinctBy { it.videoId }.take(max).toList()
    }

    /**
     * A Spotify or Apple Music song's YouTube upload: YouTube Music songs first, then YouTube,
     * taking the first result within a few seconds of the song's length.
     */
    fun match(title: String, artist: String, durationSec: Double?): YtHit? {
        val first = artist.substringBefore(',').trim()
        fun pick(items: List<YtHit>, slack: Double) =
            items.firstOrNull { durationSec == null || (it.durationSec != null && abs(it.durationSec - durationSec) <= slack) }
        return pick(search("$title $first", YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, 6), 8.0)
            ?: pick(search("$first - $title", null, 6), 15.0)
    }
}

const val BROWSER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

/** NewPipe's network access, over HttpURLConnection. */
private class NewPipeDownloader : Downloader() {
    override fun execute(request: Request): Response {
        // "What plays next" is slow and unused: answered empty but well-formed.
        if (NEXT in request.url()) return Response(200, "OK", emptyMap(), EMPTY_NEXT, request.url())
        val conn = URL(request.url()).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10_000
        conn.requestMethod = request.httpMethod()
        var hasAgent = false
        for ((name, values) in request.headers()) {
            if (name.equals("User-Agent", true) && values.isNotEmpty()) hasAgent = true
            values.forEachIndexed { i, v -> if (i == 0) conn.setRequestProperty(name, v) else conn.addRequestProperty(name, v) }
        }
        if (!hasAgent) conn.setRequestProperty("User-Agent", BROWSER_AGENT)
        try {
            request.dataToSend()?.let { body -> conn.doOutput = true; conn.outputStream.use { it.write(body) } }
            val code = conn.responseCode
            if (code == 429) throw ReCaptchaException("YouTube is rate limiting this device, try again later", request.url())
            val raw = if (code >= 400) conn.errorStream else conn.inputStream
            val body = raw?.let { s ->
                val input = if (conn.contentEncoding.equals("gzip", true)) GZIPInputStream(s) else s
                input.use { it.readBytes() }.decodeToString()
            }
            val headers = conn.headerFields.entries.mapNotNull { (k, v) -> k?.let { it to v } }.toMap()
            return Response(code, conn.responseMessage, headers, body, conn.url.toString())
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val NEXT = "/youtubei/v1/next"
        private const val EMPTY_NEXT = """{"responseContext":{},"contents":{},"currentVideoEndpoint":{},"trackingParams":""}"""
    }
}
