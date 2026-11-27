package com.myna.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.max

/** How a sound space sounds: reverb amount, room size, damping, pre-delay and stereo width. Shared by both decks. */
class Space {
    @Volatile var wet = 0f
    @Volatile var room = 0.5f
    @Volatile var damp = 0.5f
    @Volatile var preMs = 0f
    @Volatile var width = 1f
}

/**
 * MYNA's sound spaces, inside the player itself so they work on every phone and speaker:
 * a Freeverb-style room (8 comb + 4 all-pass filters per side) and a mid/side widener.
 */
@UnstableApi
class SpaceProcessor(private val s: Space) : BaseAudioProcessor() {
    private class Comb(n: Int) {
        val buf = FloatArray(n); var i = 0; var store = 0f
        fun run(x: Float, fb: Float, damp: Float): Float {
            val y = buf[i]
            store = y * (1 - damp) + store * damp
            buf[i] = x + store * fb
            if (++i == buf.size) i = 0
            return y
        }
    }

    private class AllPass(n: Int) {
        val buf = FloatArray(n); var i = 0
        fun run(x: Float): Float {
            val b = buf[i]
            buf[i] = x + b * 0.5f
            if (++i == buf.size) i = 0
            return b - x
        }
    }

    private var combsL = emptyList<Comb>(); private var combsR = emptyList<Comb>()
    private var allL = emptyList<AllPass>(); private var allR = emptyList<AllPass>()
    private var pre = FloatArray(1); private var preI = 0
    private var rate = 44100

    override fun onConfigure(f: AudioFormat): AudioFormat {
        if (f.encoding != C.ENCODING_PCM_16BIT || f.channelCount != 2) return AudioFormat.NOT_SET
        rate = f.sampleRate
        build()
        return f
    }

    private fun build() {
        val k = rate / 44100f
        fun n(x: Int) = max(1, (x * k).toInt())
        combsL = COMBS.map { Comb(n(it)) }; combsR = COMBS.map { Comb(n(it + SPREAD)) }
        allL = ALLS.map { AllPass(n(it)) }; allR = ALLS.map { AllPass(n(it + SPREAD)) }
        pre = FloatArray(max(1, rate / 10)); preI = 0
    }

    override fun queueInput(input: ByteBuffer) {
        val out = replaceOutputBuffer(input.remaining())
        val wet = s.wet; val width = s.width
        if (wet == 0f && width == 1f) { out.put(input); out.flip(); return }
        val fb = s.room * 0.28f + 0.7f
        val damp = s.damp * 0.4f
        val delay = (s.preMs * rate / 1000).toInt().coerceIn(0, pre.size - 1)
        val w = wet * 3f
        val dry = 1f - wet * 0.5f
        while (input.remaining() >= 4) {
            val l = input.short / 32768f
            val r = input.short / 32768f
            // Pre-delay: the gap before the room answers, longer in bigger rooms.
            pre[preI] = (l + r) * 0.015f
            val x = pre[(preI - delay + pre.size) % pre.size]
            if (++preI == pre.size) preI = 0
            var rl = 0f; var rr = 0f
            for (c in combsL) rl += c.run(x, fb, damp)
            for (c in combsR) rr += c.run(x, fb, damp)
            for (a in allL) rl = a.run(rl)
            for (a in allR) rr = a.run(rr)
            var ol = l * dry + rl * w
            var or = r * dry + rr * w
            // Mid / side: more side makes the sound wider than the speakers.
            val m = (ol + or) * 0.5f; val sd = (ol - or) * 0.5f * width
            ol = m + sd; or = m - sd
            out.putShort((ol.coerceIn(-1f, 1f) * 32767).toInt().toShort())
            out.putShort((or.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        }
        out.flip()
    }

    override fun onFlush() { if (isActive) build() }

    private companion object {
        val COMBS = listOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLS = listOf(556, 441, 341, 225)
        const val SPREAD = 23
    }
}
