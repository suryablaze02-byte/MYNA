package com.myna.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A Spotify or Apple Music playlist MYNA knows: its songs are read from the public page and played from YouTube. */
/** A service's playlist. [region] is the country it is for ("" = everywhere). */
data class OnlineList(val source: String, val name: String, val ref: String, val tags: String = "", val region: String = "")

/** The regions Discover can be tailored to: country code and name. "" is Global. */
val REGIONS = listOf("IN" to "India", "US" to "United States", "GB" to "United Kingdom", "CA" to "Canada", "AU" to "Australia", "" to "Global")

/** An album (or single / EP) found online. [kind] is "album", "single", "ep" or "compile". */
data class OnlineAlbum(val id: String, val title: String, val artist: String, val year: Int, val cover: String, val kind: String) {
    /** Stands in for the album where a [Track] is needed, such as its cover. */
    val coverTrack: Track get() = Track("dzal:$id", title, artist, title, 0, matchUri(title, artist, null), cover)
}

/** An artist's whole catalogue found online: their most played songs and every release. */
data class OnlineArtist(val name: String, val picture: String, val top: List<Track>, val albums: List<OnlineAlbum>) {
    val pictureTrack: Track get() = Track("dzar:$name", name, name, "", 0, matchUri(name, name, null), picture)
}

/** A row on Discover. */
data class Shelf(val title: String, val source: String, val why: String, val tracks: List<Track>)

/**
 * Music from the internet, played the way FireTheDJ does: YouTube Music is searched and streamed;
 * Spotify and Apple Music playlists give the song names, and each song is looked up on YouTube when it plays.
 */
interface Online {
    /** Why online music can't work here, or null. */
    val problem: String?
    suspend fun songs(query: String, max: Int = 15): List<Track>
    suspend fun list(l: OnlineList): List<Track>
    /** The artist best matching [name] (its first artist, when it names several), or null. */
    suspend fun artist(name: String): OnlineArtist? = null
    /** Albums (film soundtracks too) whose name matches [query], best known first. */
    suspend fun albums(query: String): List<OnlineAlbum> = emptyList()
    /** The songs on [a], in order. */
    suspend fun album(a: OnlineAlbum): List<Track> = emptyList()
    /** The album [t] is on (its full album over a single, when there is one), or null. */
    suspend fun albumOf(t: Track): OnlineAlbum? = null
    /** Starts finding the stream early, so the crossfade into this song isn't late. */
    fun prefetch(t: Track) {}
}

/** The same song wherever it comes from: its name without "(…)" or "feat. …", and its first artist. */
val Track.songKey: String get() {
    val t = title.lowercase().replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").replace(Regex("""\s+(ft\.?|feat\.?|featuring)\s.*$"""), "")
    val a = artist.lowercase().split(',', '&').first()
    return t.filter { it.isLetterOrDigit() } + "|" + a.filter { it.isLetterOrDigit() }
}

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** The album's own name: "Pushpa - The Rise (Original Motion Picture Soundtrack)" is "Pushpa". */
val OnlineAlbum.baseTitle: String get() = title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").substringBefore(" - ").trim()

/** True when [q] is this album's name, or (with [start]) the start of it. */
fun OnlineAlbum.named(q: String, start: Boolean = false) = norm(baseTitle).let { it == norm(q) || start && norm(q).length >= 3 && it.startsWith(norm(q)) }

/** True when [q] names this artist. */
fun OnlineArtist.named(q: String) = name.lowercase().filter { it.isLetterOrDigit() } == q.lowercase().filter { it.isLetterOrDigit() }

val Track.online: Boolean get() = uri.startsWith("http") || uri.startsWith("myna-match:")

/** The uri of a Spotify / Apple Music song: the deck finds it on YouTube when it opens it. */
fun matchUri(title: String, artist: String, durationSec: Double?) =
    "myna-match://song?t=${urlEncode(title)}&a=${urlEncode(artist)}&d=${durationSec ?: ""}"

/** Spotify and Apple Music's own playlists, checked against their public pages (from FireTheDJ's catalogue). */
object Catalogue {
    private const val SP = "Spotify"
    private const val AM = "Apple Music"
    val lists = listOf(
        OnlineList(SP, "Top 50 - India", "37i9dQZEVXbLZ52XmnySJg", "india chart top", "IN"),
        OnlineList(SP, "Top 50 - USA", "37i9dQZEVXbLRQDuF5jeBp", "usa us america chart top", "US"),
        OnlineList(SP, "Top 50 - United Kingdom", "37i9dQZEVXbLnolsZ8PSNw", "uk britain chart top", "GB"),
        OnlineList(SP, "Top 50 - Canada", "37i9dQZEVXbKj23U1GF4IR", "canada chart top", "CA"),
        OnlineList(SP, "Top 50 - Australia", "37i9dQZEVXbJPcfkRz0wJ0", "australia chart top", "AU"),
        OnlineList(SP, "Top 50 - Global", "37i9dQZEVXbMDoHDwVN2tF", "global chart top world"),
        OnlineList(SP, "Today's Top Hits", "37i9dQZF1DXcBWIGoYBM5M", "pop hits chart trending"),
        OnlineList(SP, "Viral Hits", "37i9dQZF1DX2L0iB23Enbq", "viral trending new"),
        OnlineList(SP, "New Music Friday", "37i9dQZF1DX4JAvHpjipBk", "new releases"),
        OnlineList(SP, "Hot Hits Telugu", "37i9dQZF1DX6XE7HRLM75P", "telugu tollywood india", "IN"),
        OnlineList(SP, "Hot Hits Tamil", "37i9dQZF1DX1i3hvzHpcQV", "tamil kollywood india", "IN"),
        OnlineList(SP, "Bollywood Dance Music", "37i9dQZF1DX8xfQRRX1PDm", "bollywood hindi dance india", "IN"),
        OnlineList(SP, "Dance Hits", "37i9dQZF1DX0BcQWzuB7ZO", "dance party edm club workout gym"),
        OnlineList(SP, "Chill Hits", "37i9dQZF1DX4WYpdgoIcn6", "chill relax calm focus study"),
        OnlineList(AM, "Today's Hits", "todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb", "pop hits chart trending"),
        OnlineList(AM, "Top 100: India", "top-100-india/pl.c0e98d2423e54c39b3df955c24df3cc5", "india chart top", "IN"),
        OnlineList(AM, "Top 100: Global", "top-100-global/pl.d25f5d1181894928af76c85c967f8f31", "global chart top world"),
        OnlineList(AM, "Telugu Hits", "telugu-hits/pl.1be89625ddd94a80a1dff804b41efd63", "telugu tollywood india", "IN"),
        OnlineList(AM, "Tamil Hits", "tamil-hits/pl.c8d5311e407f42c89d0fce075b5aaa43", "tamil kollywood india", "IN"),
        OnlineList(AM, "Bollywood Hits", "bollywood-hits/pl.d60caf02fcce4d7e9788fe01243b7c2c", "bollywood hindi india", "IN"),
        OnlineList(AM, "Hip-Hop Hits", "hip-hop-hits/pl.87c7af5767764860a0e3368d0bef9a6f", "hip hop rap workout gym"),
        OnlineList(AM, "danceXL", "dancexl/pl.6bf4415b83ce4f3789614ac4c3675740", "dance party edm club"),
        OnlineList(AM, "Today's Chill", "todays-chill/pl.2bb29727dbc34a63936787297305c37c", "chill relax calm"),
    )

    /** Playlists whose name or tags hold every word of [q] (a word may be the start of one). */
    fun search(q: String): List<OnlineList> {
        val words = q.lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return lists.filter { l ->
            val text = "${l.name} ${l.source} ${l.tags}".lowercase().split(Regex("""[^\p{L}\p{N}']+"""))
            words.all { w -> text.any { it.startsWith(w) } }
        }
    }
}

/** One play: counted once a song has played 30 seconds. [at] is when, [ms] how long the song is. */
data class Play(val artist: String, val title: String, val at: Long, val ms: Long)

/** What the person has played, newest first, kept between runs. Discover and Your stats are built from it. */
class History(private val platform: Platform) {
    var rows: List<Play> = platform.getStr("history", "").lines().mapNotNull { l ->
        val f = l.split('\t')
        if (f.size < 2) null else Play(f[0], f[1], f.getOrNull(2)?.toLongOrNull() ?: 0, f.getOrNull(3)?.toLongOrNull() ?: 0)
    }
        private set

    fun add(t: Track) {
        if (t.artist.isBlank()) return
        rows = (listOf(Play(t.artist, t.title, platform.nowMs(), t.durationMs)) + rows).take(3000)
        platform.putStr("history", rows.joinToString("\n") { "${it.artist}\t${it.title}\t${it.at}\t${it.ms}" })
    }

    private val known get() = rows.filter { it.artist != "Unknown artist" }

    /** Artists played most, most first. */
    fun topArtists(n: Int): List<String> = known.groupingBy { it.artist }.eachCount()
        .entries.sortedByDescending { it.value }.take(n).map { it.key }

    val artists: Set<String> get() = known.map { it.artist.lowercase() }.toSet()
    val isEmpty get() = rows.isEmpty()
}

class Discover(private val platform: Platform, private val library: Library, private val history: History, private val scope: CoroutineScope) {
    private val _shelves = MutableStateFlow<List<Shelf>>(emptyList())
    val shelves: StateFlow<List<Shelf>> = _shelves.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refresh() {
        val online = platform.online ?: return
        if (_loading.value || online.problem != null) return
        _loading.value = true
        scope.launch {
            try {
                _shelves.value = build(online)
            } catch (e: Exception) {
                println("MYNA discover failed: $e")
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun build(online: Online): List<Shelf> = coroutineScope {
        // Seeds: the artists played most; before any history, the artists with most songs on the phone.
        val seeds = history.topArtists(4).ifEmpty {
            library.all.value.artists().filter { it.name != "Unknown artist" }
                .sortedByDescending { it.tracks.size }.take(3).map { it.name }
        }
        val mine = history.artists + seeds.map { it.lowercase() }
        fun yours(t: Track) = mine.any { a -> a.length > 2 && t.artist.lowercase().contains(a) }

        val yt = seeds.map { a ->
            async { runCatching { Shelf("Because you play $a", "YouTube Music", "Songs by and like $a", online.songs(a, 15).distinctBy { it.songKey }) }.getOrNull() }
        }
        // Charts for the chosen region, plus the ones for everywhere.
        val region = library.prefs.region
        val lists = Catalogue.lists.filter { it.region.isEmpty() || it.region == region }.map { l -> async { runCatching { l to online.list(l) }.getOrNull() } }

        val ytShelves = yt.awaitAll().filterNotNull().filter { it.tracks.isNotEmpty() }
        val scored = lists.awaitAll().filterNotNull().filter { it.second.isNotEmpty() }.map { (l, ts) ->
            val one = ts.distinctBy { it.songKey }
            val n = one.count(::yours)
            val sorted = one.sortedByDescending { yours(it) }
            Triple(l, n, sorted)
        }
        // Per service: the region's own charts first, then the playlists with most of your artists; three each.
        val local = compareByDescending<Triple<OnlineList, Int, List<Track>>> { it.first.region.isNotEmpty() }.thenByDescending { it.second }
        val listShelves = scored.groupBy { it.first.source }.values.flatMap { group ->
            group.sortedWith(local).take(3)
        }.sortedWith(local).map { (l, n, ts) ->
            Shelf(l.name, l.source, if (n == 1) "1 song by an artist you play" else if (n > 0) "$n songs by artists you play" else "Popular right now", ts)
        }

        val mix = (ytShelves.flatMap { it.tracks.take(4) } + scored.flatMap { s -> s.third.filter(::yours) })
            .distinctBy { it.songKey }.shuffled().take(30)
        val mixShelf = if (mix.size >= 5) listOf(Shelf("Your mix", "YouTube Music · Spotify · Apple Music", "Made from what you play", mix)) else emptyList()
        mixShelf + ytShelves + listShelves
    }
}
