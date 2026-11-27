package com.myna.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** One playing song. The engine uses two decks so the next song can fade in over the last one. */
interface Deck {
    fun load(uri: String, startMs: Long)
    fun play()
    fun pause()
    fun seek(ms: Long)
    var volume: Float
    val positionMs: Long
    /** -1 when not known yet. */
    val durationMs: Long
    val ended: Boolean
    fun release()
}

enum class Repeat { Off, All, One }

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val crossfadeSec: Int = 6,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.Off,
    val fading: Boolean = false,
    /** Time left on the sleep timer, or null. */
    val sleepLeftMs: Long? = null,
    /** The sleep timer stops the music when this song ends. */
    val sleepEndOfSong: Boolean = false,
    /** The sleep timer stops the music when this album ends. */
    val sleepEndOfAlbum: Boolean = false,
    /** Where the queue came from ("Liked songs", a playlist), shown on Now Playing. */
    val from: String = "",
) {
    val current: Track? get() = queue.getOrNull(index)
}

/**
 * Plays a queue with an Apple-style crossfade: [PlayerState.crossfadeSec] before a song ends, the next
 * one starts at zero volume and the two swap with an equal-power fade (no beat matching, just volume).
 * Songs that follow each other on the same album play back to back with no fade.
 * Skipping fades over the Skip fade setting.
 */
class Engine(private val platform: Platform, scope: CoroutineScope, private val prefs: Prefs) {
    private val _state = MutableStateFlow(PlayerState(crossfadeSec = platform.getInt("crossfade", 6)))
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var cur: Deck? = null
    private var nxt: Deck? = null
    private var fadeMark = TimeSource.Monotonic.markNow()
    private var fadeLen = 1L
    private var original: List<Track> = emptyList()
    private var prefetched: String? = null

    private var counted = false
    private var sleepAt: TimeSource.Monotonic.ValueTimeMark? = null

    /** Called once a song has played 30 seconds (the play history counts it then). */
    var onPlayed: (Track) -> Unit = {}

    /** Stops the music after [minutes] (fading out over the last 30 s), at the end of this song (-1), at the end of the album (-2), or turns it off (0). */
    fun sleep(minutes: Int) {
        sleepAt = if (minutes > 0) TimeSource.Monotonic.markNow() + minutes.minutes else null
        if (sleepAt == null) cur?.volume = 1f
        _state.update { it.copy(sleepLeftMs = if (minutes > 0) minutes * 60_000L else null, sleepEndOfSong = minutes == -1, sleepEndOfAlbum = minutes == -2) }
    }

    init {
        scope.launch {
            while (isActive) {
                tick()
                delay(40)
            }
        }
    }

    fun play(tracks: List<Track>, start: Int = 0, shuffle: Boolean = false, from: String = "") {
        if (tracks.isEmpty()) return
        val s = start.coerceIn(tracks.indices)
        original = tracks
        val queue = if (shuffle) listOf(tracks[s]) + tracks.filterIndexed { i, _ -> i != s }.shuffled() else tracks
        val index = if (shuffle) 0 else s
        releaseAll()
        _state.update {
            it.copy(queue = queue, index = index, shuffle = shuffle, positionMs = 0, from = from,
                durationMs = queue[index].durationMs, fading = false)
        }
        cur = open(queue[index], 1f)
        counted = false
        setPlaying(true)
    }

    fun toggle() {
        val c = cur ?: return
        if (_state.value.playing) {
            finishFade()
            cur?.pause()
            setPlaying(false)
        } else {
            if (c.ended) c.seek(0)
            c.play()
            setPlaying(true)
        }
    }

    fun next() {
        val i = nextIndex(auto = false) ?: return
        transition(i, skipFade)
    }

    private val skipFade get() = prefs.skipFadeMs.v.toLong()

    fun prev() {
        val s = _state.value
        if (s.positionMs > 3000 || s.index <= 0) seek(0) else transition(s.index - 1, skipFade)
    }

    fun jump(i: Int) {
        if (i in _state.value.queue.indices) transition(i, skipFade)
    }

    /** Moves an Up next song (queue positions after the playing one) to another Up next position. */
    fun move(from: Int, to: Int) = _state.update { s ->
        val up = (s.index + 1) until s.queue.size
        if (from !in up || to !in up || from == to) return@update s
        s.copy(queue = s.queue.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Takes an Up next song out of the queue. */
    fun remove(i: Int) = _state.update { s ->
        if (i <= s.index || i !in s.queue.indices) s else s.copy(queue = s.queue.toMutableList().apply { removeAt(i) })
    }

    fun seek(ms: Long) {
        finishFade()
        cur?.seek(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun setCrossfade(sec: Int) {
        platform.putInt("crossfade", sec)
        _state.update { it.copy(crossfadeSec = sec) }
    }

    fun cycleRepeat() = _state.update {
        it.copy(repeat = when (it.repeat) { Repeat.Off -> Repeat.All; Repeat.All -> Repeat.One; Repeat.One -> Repeat.Off })
    }

    fun toggleShuffle() {
        val s = _state.value
        val t = s.current ?: return
        if (!s.shuffle) {
            val rest = s.queue.filterIndexed { i, _ -> i != s.index }.shuffled()
            _state.update { it.copy(queue = listOf(t) + rest, index = 0, shuffle = true) }
        } else {
            val q = original.ifEmpty { s.queue }
            _state.update { it.copy(queue = q, index = q.indexOf(t).coerceAtLeast(0), shuffle = false) }
        }
    }

    // ---------------------------------------------------------------- internals

    private fun open(t: Track, vol: Float): Deck = platform.newDeck().apply {
        load(t.uri, 0)
        volume = vol
        play()
    }

    private fun setPlaying(p: Boolean) {
        _state.update { it.copy(playing = p) }
        platform.onPlayback(_state.value.current, p)
    }

    private fun nextIndex(auto: Boolean): Int? {
        val s = _state.value
        if (s.queue.isEmpty()) return null
        if (auto && s.repeat == Repeat.One) return s.index
        return when {
            s.index + 1 < s.queue.size -> s.index + 1
            s.repeat != Repeat.Off -> 0
            else -> null
        }
    }

    private fun transition(i: Int, fadeMs: Long) {
        finishFade()
        val t = _state.value.queue[i]
        if (fadeMs <= 0 || cur == null || !_state.value.playing) {
            cur?.release()
            cur = open(t, 1f)
        } else {
            nxt = open(t, 0f)
            fadeLen = fadeMs.coerceAtLeast(300)
            fadeMark = TimeSource.Monotonic.markNow()
        }
        _state.update { it.copy(index = i, positionMs = 0, durationMs = t.durationMs, fading = nxt != null) }
        counted = false
        setPlaying(true)
    }

    private fun finishFade() {
        val n = nxt ?: return
        cur?.release()
        cur = n
        n.volume = 1f
        nxt = null
    }

    private fun releaseAll() {
        cur?.release(); nxt?.release()
        cur = null; nxt = null
    }

    private fun tick() {
        val c = cur ?: return
        nxt?.let { n ->
            val p = (fadeMark.elapsedNow().inWholeMilliseconds.toFloat() / fadeLen).coerceIn(0f, 1f)
            c.volume = cos(p * PI / 2).toFloat()
            n.volume = sin(p * PI / 2).toFloat()
            if (p >= 1f) finishFade()
        }
        val s = _state.value
        val t = s.current ?: return
        val d = nxt ?: cur ?: return
        val dur = d.durationMs.takeIf { it > 0 } ?: t.durationMs
        val pos = d.positionMs.coerceIn(0, dur.coerceAtLeast(0))
        if (s.playing && !counted && pos >= 30_000) {
            counted = true
            onPlayed(t)
        }
        sleepAt?.let { at ->
            val left = -at.elapsedNow().inWholeMilliseconds
            if (left <= 0) {
                sleepAt = null
                if (s.playing) { finishFade(); cur?.pause(); setPlaying(false) }
                cur?.volume = 1f
                _state.update { it.copy(sleepLeftMs = null) }
                return
            }
            // The last 30 seconds fade out.
            if (s.playing && nxt == null && left < SLEEP_FADE_MS) d.volume = left.toFloat() / SLEEP_FADE_MS
            if (s.sleepLeftMs?.div(1000) != left / 1000) _state.update { it.copy(sleepLeftMs = left) }
        }
        if (nxt == null && s.playing) {
            val ni = nextIndex(auto = true)
            val next = ni?.let { s.queue[it] }
            val stopHere = s.sleepEndOfSong || s.sleepEndOfAlbum && (next == null || t.album.isBlank() || next.albumKey != t.albumKey)
            if (d.ended && stopHere) {
                _state.update { it.copy(sleepEndOfSong = false, sleepEndOfAlbum = false) }
                setPlaying(false)
                return
            }
            if (d.ended) {
                if (ni != null) transition(ni, 0) else setPlaying(false)
                return
            }
            val fade = s.crossfadeSec * 1000L
            // An online song takes a moment to find: start on it well before the fade.
            if (next != null && next.online && prefetched != next.id && dur - pos <= fade + 30_000) {
                prefetched = next.id
                platform.online?.prefetch(next)
            }
            val gapless = stopHere || prefs.gapless.on && next != null && t.trackNo > 0 && next.albumKey == t.albumKey && next.trackNo == t.trackNo + 1
            if (next != null && fade > 0 && !gapless && dur > fade * 2 && dur - pos <= fade) {
                transition(ni, dur - pos)
                return
            }
        }
        _state.update { it.copy(positionMs = pos, durationMs = dur, fading = nxt != null) }
    }

    companion object {
        const val SLEEP_FADE_MS = 30_000L
    }
}
