package com.myna.player

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One song file. [uri] is what a [Deck] plays; [artRef] is what [Platform.art] reads the cover from. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val artRef: String,
    val trackNo: Int = 0,
    val addedAt: Long = 0,
    /** The folder it is in, for Settings › Library. */
    val folder: String = "",
    val year: Int = 0,
) {
    val albumKey: String get() = album.lowercase()
}

data class Album(val key: String, val title: String, val artist: String, val tracks: List<Track>)
data class Artist(val name: String, val tracks: List<Track>)

fun List<Track>.albums(): List<Album> = groupBy { it.albumKey }.map { (key, ts) ->
    val artist = ts.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
    Album(key, ts.first().album, artist, ts.sortedWith(compareBy({ it.trackNo }, { it.title.lowercase() })))
}.sortedBy { it.title.lowercase() }

fun List<Track>.artists(): List<Artist> =
    groupBy { it.artist }.map { Artist(it.key, it.value) }.sortedBy { it.name.lowercase() }

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

fun fmtTotal(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60} hr ${m % 60} min" else "$m min"
}

/** What the songs live on, for labels: "phone" on Android, "PC" on Windows. */
var device = "phone"

/** What each platform supplies: its music files, covers, a player deck and small settings. */
interface Platform {
    val isDesktop: Boolean
    suspend fun scan(): List<Track>
    suspend fun art(track: Track): ImageBitmap?
    fun newDeck(): Deck
    fun getInt(key: String, def: Int): Int
    fun putInt(key: String, value: Int)
    fun hour(): Int
    /** Where the music comes from, shown in Settings. */
    fun sourceLabel(): String
    /** Lets the user choose the music folder (desktop). Returns true when it changed. */
    fun pickFolder(): Boolean = false
    /** Called when the song or play state changes (Android keeps a notification). */
    fun onPlayback(track: Track?, playing: Boolean) {}
    fun getStr(key: String, def: String): String = def
    fun putStr(key: String, value: String) {}
    /** Online music (YouTube Music, Spotify and Apple Music lists), or null where it isn't built yet. */
    val online: Online? get() = null
    /** In-app updates from GitHub, or null where there are none. */
    val updater: Updater? get() = null
    fun nowMs(): Long = 0
    /** Year * 12 + month (0-11) of a time, for Your stats. */
    fun monthOf(ms: Long): Int = 0
    /** Whether the device is on Wi-Fi (animated covers can be Wi-Fi only). */
    fun onWifi(): Boolean = true
    /** MYNA's sound: space 0 Off, 1 Studio, 2 Stage, 3 Big Hall, 4 Headset; eq preset; Clear Bass 0-10. */
    fun applySound(space: Int, eq: Int, clearBass: Int) {}
    /** Whether the device has Dolby Atmos settings MYNA can open. */
    val hasDolby: Boolean get() = false
    fun openDolby(): Boolean = false
    /** Lyrics text (.lrc or plain) for a song, from the internet, or null. */
    suspend fun lyrics(title: String, artist: String, durationSec: Long): String? = null
    /** Opens the phone's own output picker (pairing, Cast). */
    fun openOutput() {}
    /** Speakers and headphones the music can play on right now. */
    fun outputs(): List<OutDevice> = emptyList()
    /** Id of the device the music plays on. */
    fun currentOutput(): Int = 0
    fun setOutput(id: Int) {}
    /** Media volume 0..1. */
    fun volume(): Float = 1f
    fun setVolume(v: Float) {}
    /** Wall-clock time [ms] in the user's format, e.g. "11:42 PM". */
    fun clock(ms: Long): String = ""
    /** A setup problem to show in the app, or null. */
    val problem: String? get() = null
}

/** A speaker or headphones. [kind]: 0 this phone, 1 Bluetooth, 2 wired, 3 USB. */
data class OutDevice(val id: Int, val name: String, val kind: Int)

class ArtCache(private val platform: Platform) {
    private val map = LinkedHashMap<String, ImageBitmap?>()
    suspend fun get(t: Track): ImageBitmap? {
        // Online songs each have their own picture; songs on the phone share their album's.
        val key = if (t.online) t.id else t.albumKey
        if (map.containsKey(key)) return map[key]
        // HD covers are big: keep the 80 most recently loaded.
        if (map.size >= 80) map.remove(map.keys.first())
        // A cover that failed to load (no network) is tried again next time.
        return platform.art(t).also { if (it != null) map[key] = it }
    }
}

class Library(private val platform: Platform, private val scope: CoroutineScope, val prefs: Prefs) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    /** Every song found on the device, shown or not (Discover still learns from them). */
    val all: StateFlow<List<Track>> = _tracks.asStateFlow()
    private val _visible = MutableStateFlow<List<Track>>(emptyList())
    /** The songs the app shows, after the Library settings (phone songs, short clips, folders, duplicates). */
    val tracks: StateFlow<List<Track>> = _visible.asStateFlow()
    val likes = TrackStore(platform, "liked")
    /** Online songs the user added to their library. */
    val saved = TrackStore(platform, "saved")
    val recent = TrackStore(platform, "recent", 200)
    val playlists = Playlists(platform)
    /** Every song MYNA knows: the library plus liked and recently played ones (which may be online). */
    fun known(lib: List<Track>): List<Track> = (lib + saved.tracks + likes.tracks + recent.tracks).distinctBy { it.id }
    var lastScan: Long = platform.getStr("lastScan", "0").toLongOrNull() ?: 0
        private set

    /** Runs again after any Library setting changes. */
    fun refilter() {
        if (!prefs.phoneSongs.on) { _visible.value = saved.tracks; return }
        var t = _tracks.value.filter { (!prefs.hideShort.on || it.durationMs >= 30_000) && it.folder !in prefs.excluded }
        if (prefs.merge.on) t = t.distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}|${it.durationMs / 3000}" }
        _visible.value = (t + saved.tracks).distinctBy { it.id }
    }

    /** Adds an online song to the library, or takes it out again. */
    fun toggleSaved(t: Track) { saved.toggle(t); refilter() }
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    val art = ArtCache(platform)
    val history = History(platform)
    val discover = Discover(platform, this, history, scope)

    fun rescan() {
        if (_scanning.value) return
        _scanning.value = true
        scope.launch {
            try {
                _tracks.value = platform.scan().sortedBy { it.title.lowercase() }
                lastScan = platform.nowMs()
                platform.putStr("lastScan", lastScan.toString())
                refilter()
            } catch (e: Exception) {
                println("MYNA scan failed: $e")
            } finally {
                _scanning.value = false
            }
        }
    }
}

/** Percent-encodes a search term for a URL. */
fun urlEncode(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = b.toInt().toChar()
        if (c.isLetterOrDigit() && b >= 0 || c in "-_.~") append(c)
        else append('%').append(((b.toInt() and 0xFF) + 0x100).toString(16).substring(1).uppercase())
    }
}
