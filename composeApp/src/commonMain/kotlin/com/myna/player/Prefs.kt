package com.myna.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Every setting, saved on the device. Each one is Compose state, so screens update when it changes. */
class Prefs(private val p: Platform) {
    inner class Pref(private val key: String, def: Int) {
        var v by mutableIntStateOf(p.getInt(key, def))
            private set
        fun set(x: Int) { p.putInt(key, x); v = x }
        val on get() = v == 1
    }

    /** 0 = system, 1 = light, 2 = dark. */
    val theme = Pref("theme", 0)
    /** Living cover: 0 = on, 1 = Wi-Fi only, 2 = off. */
    val animCovers = Pref("animCovers", 0)
    val tint = Pref("tint", 1)
    val downloadCovers = Pref("downloadCovers", 1)
    val gapless = Pref("gapless", 1)
    /** How long a tap on Next fades, in ms. */
    val skipFadeMs = Pref("skipFadeMs", 1000)
    val phoneSongs = Pref("phoneSongs", 0)
    val hideShort = Pref("hideShort", 1)
    val merge = Pref("merge", 1)
    /** Sound space: 0 Off, 1 Studio, 2 Stage, 3 Big Hall, 4 Headset. */
    val soundFx = Pref("soundFx", 0)
    /** Equaliser: 0 Flat, 1 Bass, 2 Vocal, 3 Bright, 4 Warm. */
    val eqPreset = Pref("eqPreset", 0)
    val clearBass = Pref("clearBass", 0)
    val lyricsDl = Pref("lyricsDl", 1)

    /** Where Discover's charts come from: a code in [REGIONS], "" for Global. Starts as the device's country. */
    var region by mutableStateOf(p.getStr("region", REGIONS.firstOrNull { it.first == p.country }?.first ?: ""))
        private set

    fun pickRegion(code: String) { region = code; p.putStr("region", code); storeCountry = code.ifEmpty { "US" } }

    init { storeCountry = region.ifEmpty { "US" } }

    /** Folders (as the scan names them) whose songs are left out. */
    var excluded by mutableStateOf(p.getStr("excluded", "").split('\n').filter { it.isNotBlank() }.toSet())
        private set

    /** Recent searches, newest first. */
    var searches by mutableStateOf(p.getStr("searches", "").lines().filter { it.isNotBlank() })
        private set

    fun addSearch(q: String) {
        val s = q.trim().replace('\n', ' ')
        if (s.length < 2) return
        searches = (listOf(s) + searches.filter { !it.equals(s, true) }).take(8)
        p.putStr("searches", searches.joinToString("\n"))
    }

    fun clearSearches() { searches = emptyList(); p.putStr("searches", "") }

    fun toggleFolder(f: String) {
        excluded = if (f in excluded) excluded - f else excluded + f
        p.putStr("excluded", excluded.joinToString("\n"))
    }
}

/** A saved list of whole songs, newest first (liked songs, recently played), so online songs play again later. */
class TrackStore(private val p: Platform, private val store: String, private val cap: Int = 100_000) {
    var tracks by mutableStateOf(p.getStr(store, "").lines().mapNotNull { l ->
        val f = l.split('\t')
        if (f.size < 7) null else Track(f[0], f[1], f[2], f[3], f[4].toLongOrNull() ?: 0, f[5], f[6])
    })
        private set

    private fun key(t: Track) = "${t.title.lowercase()}|${t.artist.lowercase()}"
    fun has(t: Track) = tracks.any { key(it) == key(t) }

    fun toggle(t: Track) {
        tracks = if (has(t)) tracks.filter { key(it) != key(t) } else listOf(t) + tracks
        save()
    }

    /** Puts [t] first (moving it if it is already here). */
    fun push(t: Track) {
        tracks = (listOf(t) + tracks.filter { key(it) != key(t) }).take(cap)
        save()
    }

    /** Adds [t] at the end, if it is not here yet (playlists keep the order songs were added). */
    fun add(t: Track) { if (!has(t)) { tracks = tracks + t; save() } }
    fun remove(t: Track) { tracks = tracks.filter { key(it) != key(t) }; save() }
    /** Adds every song in [ts] not here yet, or with [on] false takes them all out (one save either way). */
    fun setAll(ts: List<Track>, on: Boolean) {
        val keys = ts.map(::key).toSet()
        tracks = if (on) tracks + ts.filter { !has(it) }.distinctBy(::key) else tracks.filter { key(it) !in keys }
        save()
    }
    fun clear() { tracks = emptyList(); save() }

    private fun save() {
        p.putStr(store, tracks.joinToString("\n") {
            listOf(it.id, it.title, it.artist, it.album, it.durationMs.toString(), it.uri, it.artRef)
                .joinToString("\t") { s -> s.replace('\t', ' ').replace('\n', ' ') }
        })
    }
}

/** The person's own playlists: a list of names, each with its own [TrackStore]. */
class Playlists(private val p: Platform) {
    var names by mutableStateOf(p.getStr("playlists", "").lines().filter { it.isNotBlank() })
        private set
    private val stores = HashMap<String, TrackStore>()

    fun songs(name: String): TrackStore = stores.getOrPut(name) { TrackStore(p, "pl:$name") }

    fun create(name: String) {
        val n = name.trim().replace('\n', ' ')
        if (n.isEmpty() || n in names) return
        names = names + n
        p.putStr("playlists", names.joinToString("\n"))
    }

    fun delete(name: String) {
        songs(name).clear()
        names = names - name
        p.putStr("playlists", names.joinToString("\n"))
    }
}
