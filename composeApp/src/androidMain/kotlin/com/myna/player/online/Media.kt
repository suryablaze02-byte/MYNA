package com.myna.player.online

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import kotlin.math.min

/** googlevideo paces open-ended reads to playback speed but serves bounded ranges at line rate. */
private const val RANGE_BYTES = 1024L * 1024
private val REFUSED = setOf(403, 404, 410)

/**
 * What decks load songs through: files and content uris as before; YouTube watch links and
 * `myna-match:` songs (Spotify / Apple Music, looked up on YouTube) resolved when the deck opens them.
 */
@UnstableApi
object OnlineMedia {
    fun dataSourceFactory(context: Context): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory().setConnectTimeoutMs(10_000).setReadTimeoutMs(15_000)
        val resolving = ResolvingDataSource.Factory(ChunkedDataSource.Factory(http)) { spec ->
            // Runs on the loader thread, where blocking is fine.
            val videoId = videoIdFor(spec.uri.toString()) ?: return@Factory spec
            val stream = YouTubeStreams.resolve(videoId)
            spec.buildUpon().setUri(Uri.parse(stream.url))
                .setHttpRequestHeaders(spec.httpRequestHeaders + stream.headers).build()
        }
        return DefaultDataSource.Factory(context, resolving)
    }

    private val matched = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The YouTube video for a song's uri, looking a Spotify / Apple Music song up once. Blocking. */
    fun videoIdFor(uri: String): String? {
        YouTubeLinks.videoId(uri)?.let { return it }
        if (!uri.startsWith(MATCH)) return null
        matched[uri]?.let { return it }
        val p = queryParams(Uri.parse(uri).encodedQuery)
        val hit = YouTubeSearch.match(p["t"].orEmpty(), p["a"].orEmpty(), p["d"]?.toDoubleOrNull())
            ?: throw IOException("Couldn't find this song on YouTube")
        matched[uri] = hit.videoId
        return hit.videoId
    }

    const val MATCH = "myna-match://song"
}

/** Reads a googlevideo URL as consecutive ranges while looking like one stream to the player. */
@UnstableApi
private class ChunkedDataSource(private val upstream: DataSource) : DataSource {
    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource())
    }

    private var spec: DataSpec? = null
    private var passthrough = false
    private var upstreamOpen = false
    private var position = 0L
    private var remaining = 0L
    private var chunkLeft = 0L

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val total = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
        if (total == null || dataSpec.uri.host?.endsWith(".googlevideo.com") != true) {
            passthrough = true
            upstreamOpen = true
            return upstream.open(dataSpec)
        }
        passthrough = false
        spec = dataSpec
        position = dataSpec.position
        val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) total else min(total, position + dataSpec.length)
        remaining = (end - position).coerceAtLeast(0)
        chunkLeft = 0
        if (remaining > 0) openChunk()
        return remaining
    }

    private fun openChunk() {
        val base = spec!!
        val length = min(RANGE_BYTES, remaining)
        try {
            upstream.open(base.buildUpon().setPosition(position).setLength(length).build())
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode in REFUSED) YouTubeStreams.forgetUrl(base.uri.toString())
            throw e
        }
        upstreamOpen = true
        chunkLeft = length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (chunkLeft == 0L) {
            upstream.close()
            upstreamOpen = false
            openChunk()
        }
        val n = upstream.read(buffer, offset, min(length.toLong(), chunkLeft).toInt())
        if (n == C.RESULT_END_OF_INPUT) throw IOException("Stream ended $chunkLeft bytes early at $position")
        position += n
        remaining -= n
        chunkLeft -= n
        return n
    }

    override fun getUri(): Uri? = if (passthrough) upstream.uri else spec?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        if (upstreamOpen) upstream.close()
        upstreamOpen = false
        spec = null
    }
}
