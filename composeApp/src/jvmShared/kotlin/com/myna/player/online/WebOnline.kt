package com.myna.player.online

import com.myna.player.Online
import com.myna.player.OnlineAlbum
import com.myna.player.OnlineArtist
import com.myna.player.OnlineList
import com.myna.player.Track
import com.myna.player.matchUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Online music for the JVM apps (Android and Windows): iTunes, Deezer and YouTube. */
class WebOnline : Online {
    override val problem: String?
        get() = if (YouTubeStreams.supported()) null else "Online music needs Android 13 or newer"

    override suspend fun songs(query: String, max: Int): List<Track> = withContext(Dispatchers.IO) {
        YouTubeSearch.songs(query, max).map { h ->
            Track(
                id = "yt:${h.videoId}", title = h.title, artist = h.artist, album = "",
                durationMs = ((h.durationSec ?: 0.0) * 1000).toLong(), uri = YouTubeLinks.watchUrl(h.videoId),
                // Not the YouTube thumbnail: the cover is looked up as official album art (see officialArt).
                artRef = "",
            )
        }
    }

    override suspend fun list(l: OnlineList): List<Track> = withContext(Dispatchers.IO) {
        val (_, songs) = if (l.source == "Spotify") spotify(get("https://open.spotify.com/embed/playlist/${l.ref}"))
        else apple(get("https://music.apple.com/in/playlist/${l.ref}"))
        songs.take(100).map { s ->
            Track(
                id = "${l.source}:${s.title}|${s.artist}", title = s.title, artist = s.artist, album = l.name,
                durationMs = ((s.sec ?: 0.0) * 1000).toLong(), uri = matchUri(s.title, s.artist, s.sec), artRef = s.art.orEmpty(),
            )
        }
    }

    // Apple's free iTunes API: an artist's top songs and every release. Songs play by being matched on YouTube.
    // (Deezer's catalogue comes back empty in some countries, India among them.)
    override suspend fun artist(name: String): OnlineArtist? = withContext(Dispatchers.IO) {
        val main = name.split(Regex(""",|&| feat\.? | ft\.? | x """, RegexOption.IGNORE_CASE)).first().trim()
        if (norm(main).length < 2) return@withContext null
        artists[norm(main)]?.let { return@withContext it }
        runCatching {
            // iTunes lists the best known artist first, so look-alike names ("Emine'm") lose.
            val hit = JSONObject(get("https://itunes.apple.com/search?entity=musicArtist&limit=10$STORE&term=" + java.net.URLEncoder.encode(main, "UTF-8")))
                .optJSONArray("results")?.objects().orEmpty().let { hits ->
                    hits.firstOrNull { norm(it.optString("artistName")) == norm(main) }
                        ?: hits.firstOrNull { norm(it.optString("artistName")).startsWith(norm(main)) }
                } ?: return@runCatching null
            val id = hit.optLong("artistId")
            val artistName = hit.optString("artistName")
            val top = JSONObject(get("https://itunes.apple.com/lookup?entity=song&limit=25$STORE&id=$id")).optJSONArray("results")?.objects().orEmpty()
                .filter { it.optString("wrapperType") == "track" && it.optLong("artistId") == id }
                .map { itTrack(it, null) }.distinctBy { norm(cleanTitle(it.title)) }.take(20)
            val albums = JSONObject(get("https://itunes.apple.com/lookup?entity=album&limit=200$STORE&id=$id")).optJSONArray("results")?.objects().orEmpty()
                .filter { it.optString("wrapperType") == "collection" }
                .map { itAlbum(it, artistName) }
                .sortedByDescending { it.year }.distinctBy { norm(it.title) + it.kind }
            // iTunes has no artist photos: the cover of their best known song stands in.
            OnlineArtist(artistName, top.firstOrNull()?.artRef ?: albums.firstOrNull()?.cover.orEmpty(), top, albums)
        }.getOrNull()?.also { artists[norm(main)] = it }
    }

    override suspend fun albums(query: String): List<OnlineAlbum> = withContext(Dispatchers.IO) {
        runCatching {
            JSONObject(get("https://itunes.apple.com/search?entity=album&limit=15$STORE&term=" + java.net.URLEncoder.encode(query, "UTF-8")))
                .optJSONArray("results")?.objects().orEmpty().map { itAlbum(it, null) }
                // Full albums (a film's whole soundtrack) before singles; one of each name.
                .sortedBy { if (it.kind == "album") 0 else 1 }.distinctBy { it.title.lowercase() + it.kind }
        }.getOrDefault(emptyList())
    }

    private fun itAlbum(a: JSONObject, artist: String?): OnlineAlbum {
        val raw = a.optString("collectionName")
        val kind = when { raw.endsWith(" - Single") -> "single"; raw.endsWith(" - EP") -> "ep"; else -> "album" }
        return OnlineAlbum(
            a.optLong("collectionId").toString(), raw.removeSuffix(" - Single").removeSuffix(" - EP"), artist ?: a.optString("artistName"),
            a.optString("releaseDate").take(4).toIntOrNull() ?: 0,
            a.optString("artworkUrl100").replace("100x100bb", "1000x1000bb"), kind,
        )
    }

    override suspend fun album(a: OnlineAlbum): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            JSONObject(get("https://itunes.apple.com/lookup?entity=song&limit=200$STORE&id=${a.id}")).optJSONArray("results")?.objects().orEmpty()
                .filter { it.optString("wrapperType") == "track" }
                .sortedWith(compareBy({ it.optInt("discNumber") }, { it.optInt("trackNumber") }))
                .mapIndexed { i, t -> itTrack(t, a).copy(trackNo = i + 1) }
        }.getOrDefault(emptyList())
    }

    private fun itTrack(t: JSONObject, a: OnlineAlbum?): Track {
        val title = t.optString("trackName").trim()
        val artist = t.optString("artistName").ifEmpty { a?.artist.orEmpty() }
        val sec = t.optLong("trackTimeMillis") / 1000.0
        return Track(
            id = "it:${t.optLong("trackId")}", title = title, artist = artist,
            album = a?.title ?: t.optString("collectionName").removeSuffix(" - Single").removeSuffix(" - EP"),
            durationMs = t.optLong("trackTimeMillis"), uri = matchUri(title, artist, sec.takeIf { it > 0 }),
            artRef = a?.cover ?: t.optString("artworkUrl100").replace("100x100bb", "1000x1000bb"),
            year = a?.year ?: (t.optString("releaseDate").take(4).toIntOrNull() ?: 0),
        )
    }

    override fun prefetch(t: Track) {
        pool.execute { runCatching { SongLinks.videoIdFor(t.uri)?.let(YouTubeStreams::resolve) } }
    }

    companion object {
        private val pool = Executors.newSingleThreadExecutor()
        /** The phone's own country store: Indian film soundtracks are in iTunes India, not the US one. */
        private val STORE = "&country=" + java.util.Locale.getDefault().country.ifEmpty { "US" }
        private val artists = java.util.concurrent.ConcurrentHashMap<String, OnlineArtist>()
        private const val PHONE_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"

        /** Lyrics text (.lrc or plain) from LRCLIB, or null. Blocking. */
        fun lyrics(title: String, artist: String, durationSec: Long): String? {
            fun get(url: String): String? = runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "MYNA music player"); c.connectTimeout = 8000; c.readTimeout = 8000
                if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
            }.getOrNull()
            fun pick(o: JSONObject): String? = listOf("syncedLyrics", "plainLyrics")
                .firstNotNullOfOrNull { k -> if (o.isNull(k)) null else o.optString(k).ifBlank { null } }
            fun q(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
            return get("https://lrclib.net/api/get?track_name=${q(title)}&artist_name=${q(artist)}&duration=$durationSec")
                ?.let { runCatching { pick(JSONObject(it)) }.getOrNull() }
                ?: get("https://lrclib.net/api/search?q=" + q("$title $artist"))?.let { body ->
                    runCatching { val a = JSONArray(body); (0 until a.length()).firstNotNullOfOrNull { pick(a.getJSONObject(it)) } }.getOrNull()
                }
        }

        /** A picture's bytes, for the platform to decode. */
        fun bytes(url: String): ByteArray? {
            if (!url.startsWith("https://")) return null
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10_000
            return try {
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }

        /** The YouTube video's own picture; the platform cuts out the square in its middle. */
        fun youTubeThumb(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        private fun get(url: String): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", PHONE_AGENT)
            try {
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} from ${URL(url).host}")
                return conn.inputStream.use { it.readBytes() }.decodeToString()
            } finally {
                conn.disconnect()
            }
        }

        /** Apple's artwork template ("…/{w}x{h}bb.{f}") at 1000 px. */
        private fun appleArt(template: String?) = template
            ?.replace("{w}", "1000")?.replace("{h}", "1000")?.replace("{f}", "jpg")?.replace("{c}", "bb")
            ?.takeIf { it.startsWith("https://") }

        private val artCache = java.util.concurrent.ConcurrentHashMap<String, String>()

        /**
         * The song's official album cover in HD: Deezer's 1000 px cover of the same artist's
         * same song, else iTunes at 1000 px. Null when neither knows it.
         */
        fun officialArt(rawTitle: String, artist: String): String? {
            val title = cleanTitle(rawTitle)
            val key = "${norm(title)}|${norm(artist)}"
            artCache[key]?.let { return it.ifEmpty { null } }
            // iTunes first: Deezer comes back empty in some countries (India among them).
            val url = runCatching { itunes(title, artist) }.getOrNull() ?: runCatching { deezer(title, artist) }.getOrNull()
            artCache[key] = url.orEmpty()
            return url
        }


        private fun same(a: String, b: String): Boolean {
            val x = norm(a); val y = norm(b)
            return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
        }

        private fun deezer(title: String, artist: String): String? {
            val first = artist.substringBefore(',').substringBefore('&').trim()
            for (q in listOf("artist:\"$first\" track:\"$title\"", "$first $title")) {
                val data = JSONObject(get("https://api.deezer.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&limit=10"))
                    .optJSONArray("data") ?: continue
                val hit = data.objects().firstOrNull {
                    same(it.optJSONObject("artist")?.optString("name").orEmpty(), first) && same(cleanTitle(it.optString("title")), title)
                } ?: continue
                hit.optJSONObject("album")?.optString("cover_xl")?.takeIf { it.startsWith("https://") }?.let { return it }
            }
            return null
        }

        private fun itunes(title: String, artist: String): String? {
            val first = artist.substringBefore(',').substringBefore('&').trim()
            val res = JSONObject(get("https://itunes.apple.com/search?media=music&entity=song&limit=10$STORE&term=" + java.net.URLEncoder.encode("$first $title", "UTF-8")))
                .optJSONArray("results") ?: return null
            val hit = res.objects().firstOrNull { same(it.optString("artistName"), first) && same(cleanTitle(it.optString("trackName")), title) } ?: return null
            return hit.optString("artworkUrl100").takeIf { it.isNotEmpty() }?.replace("100x100bb", "1000x1000bb")
        }

        private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        /** A listing title down to the song's name: no "(From …)", "[Lyrics]" or "feat. Someone". */
        private fun cleanTitle(t: String) = t.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "")
            .replace(Regex("""\s+(ft\.?|feat\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "").trim().ifEmpty { t }

        private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }

        /** A Spotify embed page: the cover and each song's title, artists and length (from FireTheDJ). */
        class Song(val title: String, val artist: String, val sec: Double?, val art: String? = null)

        fun spotify(html: String): Pair<String?, List<Song>> {
            val json = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1) ?: throw IOException("Spotify's page has changed")
            val entity = JSONObject(json).optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")
                ?.optJSONObject("data")?.optJSONObject("entity") ?: throw IOException("Spotify's page has changed")
            val songs = (entity.optJSONArray("trackList") ?: JSONArray()).objects().mapNotNull { t ->
                if (t.optString("entityType", "track") != "track" || !t.optBoolean("isPlayable", true)) return@mapNotNull null
                val title = t.optString("title").trim().ifEmpty { return@mapNotNull null }
                Song(title, t.optString("subtitle").trim(), t.optLong("duration", 0L).takeIf { it > 0 }?.let { it / 1000.0 })
            }
            val cover = entity.optJSONObject("coverArt")?.optJSONArray("sources")?.objects()
                ?.maxByOrNull { it.optInt("width") }?.optString("url")?.takeIf { it.startsWith("https://") }
            return cover to songs
        }

        /** An Apple Music playlist page, from the data it is drawn from (from FireTheDJ). */
        fun apple(html: String): Pair<String?, List<Song>> {
            val json = Regex("""<script type="application/json" id="serialized-server-data">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1) ?: throw IOException("Apple Music's page has changed")
            val sections = JSONObject(json).optJSONArray("data")?.optJSONObject(0)?.optJSONObject("data")?.optJSONArray("sections")
                ?: throw IOException("Apple Music's page has changed")
            var cover: String? = null
            val songs = ArrayList<Song>()
            for (s in sections.objects()) {
                val items = s.optJSONArray("items") ?: continue
                when (s.optString("itemKind")) {
                    "containerDetailHeaderLockup" -> cover = items.optJSONObject(0)?.optJSONObject("artwork")
                        ?.optJSONObject("dictionary")?.optString("url")
                        ?.replace("{w}", "600")?.replace("{h}", "600")?.replace("{f}", "jpg")?.replace("{c}", "bb")
                        ?.takeIf { it.startsWith("https://") }
                    "trackLockup" -> for (t in items.objects()) {
                        val kind = t.optJSONObject("contentDescriptor")?.optString("kind")
                        if (kind != null && kind != "song") continue
                        val title = t.optString("title").trim()
                        if (title.isEmpty()) continue
                        // Apple's own cover of the song's album, in HD.
                        val art = appleArt(t.optJSONObject("artwork")?.optJSONObject("dictionary")?.optString("url"))
                        songs += Song(title, t.optString("artistName").trim(), t.optLong("duration", 0L).takeIf { it > 0 }?.let { it / 1000.0 }, art)
                    }
                }
            }
            return cover to songs
        }
    }
}
