package com.myna.player

import com.myna.player.online.SongLinks
import com.myna.player.online.YouTubeStreams
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * A Windows deck: FFmpeg decodes the song (any file format, or a YouTube stream) to 44.1 kHz
 * 16-bit stereo, MYNA's sound space is added, and Java Sound plays it.
 * [master] is MYNA's own volume, on top of the deck's crossfade [volume].
 */
class FfDeck(private val space: Space, private val master: () -> Float) : Deck {
    @Volatile private var song: Song? = null
    @Volatile override var volume = 1f

    override fun load(uri: String, startMs: Long) {
        song?.finish()
        song = Song(uri, startMs).also { it.start() }
    }

    override fun play() { song?.playing = true }
    override fun pause() { song?.playing = false }
    override fun seek(ms: Long) { song?.let { it.seekTo = ms; it.position = ms } }
    override val positionMs get() = song?.position ?: 0
    override val durationMs get() = song?.duration ?: -1
    // A song that can't play (offline, not on YouTube) counts as ended, so the queue moves on.
    override val ended get() = song?.ended ?: false
    override fun release() { song?.finish(); song = null }

    private inner class Song(private val uri: String, startMs: Long) : Thread("MYNA deck") {
        @Volatile var playing = false
        @Volatile var seekTo = if (startMs > 0) startMs else -1L
        @Volatile var position = startMs
        @Volatile var duration = -1L
        @Volatile var ended = false
        @Volatile private var done = false

        init { isDaemon = true }

        fun finish() { done = true }

        override fun run() {
            var g: FFmpegFrameGrabber? = null
            var line: SourceDataLine? = null
            try {
                g = open()
                g.start()
                g.lengthInTime.takeIf { it > 0 }?.let { duration = it / 1000 }
                val fmt = AudioFormat(RATE.toFloat(), 16, 2, true, false)
                // A quarter of a second of buffer: pause and seek answer quickly.
                line = AudioSystem.getSourceDataLine(fmt).apply { open(fmt, RATE) }
                val dsp = SpaceDsp(space).apply { reset(RATE) }
                var base = position
                var lineBase = 0L
                while (!done) {
                    val s = seekTo
                    if (s >= 0) {
                        seekTo = -1
                        g.setAudioTimestamp(s * 1000)
                        line.flush()
                        dsp.reset()
                        base = s; lineBase = line.longFramePosition; position = s
                    }
                    if (!playing) {
                        if (line.isRunning) line.stop()
                        sleep(15)
                        continue
                    }
                    if (!line.isRunning) line.start()
                    val f = g.grabSamples()
                    if (f == null) {
                        line.drain()
                        ended = true
                        break
                    }
                    val samples = f.samples?.firstOrNull() as? ShortBuffer ?: continue
                    val n = samples.remaining()
                    val input = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                    input.asShortBuffer().put(samples)
                    val out = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                    dsp.process(input, out, volume * master())
                    line.write(out.array(), 0, out.position())
                    if (seekTo < 0) position = base + (line.longFramePosition - lineBase) * 1000 / RATE
                }
            } catch (e: Throwable) {
                if (!done) {
                    DesktopPlatform.log("can't play $uri: ${e.stackTraceToString().take(1500)}")
                    ended = true
                }
            } finally {
                runCatching { line?.close() }
                runCatching { g?.close() }
            }
        }

        private fun open(): FFmpegFrameGrabber {
            val video = SongLinks.videoIdFor(uri)
            val g = if (video == null) FFmpegFrameGrabber(uri) else {
                val s = YouTubeStreams.resolve(video)
                FFmpegFrameGrabber(s.url).apply {
                    s.headers.forEach { (k, v) -> if (k.equals("User-Agent", true)) setOption("user_agent", v) }
                    val rest = s.headers.filterKeys { !it.equals("User-Agent", true) }
                    if (rest.isNotEmpty()) setOption("headers", rest.entries.joinToString("") { "${it.key}: ${it.value}\r\n" })
                    setOption("reconnect", "1")
                    setOption("reconnect_streamed", "1")
                    setOption("reconnect_delay_max", "5")
                }
            }
            return g.apply {
                sampleRate = RATE
                audioChannels = 2
                sampleFormat = avutil.AV_SAMPLE_FMT_S16
            }
        }
    }

    companion object { const val RATE = 44100 }
}
