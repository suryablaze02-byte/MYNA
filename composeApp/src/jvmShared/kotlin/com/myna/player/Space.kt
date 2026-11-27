package com.myna.player

import java.nio.ByteBuffer
import kotlin.math.max

/** How a sound space sounds: reverb amount, room size, damping, pre-delay and stereo width. Shared by both decks. */
class Space {
    @Volatile var wet = 0f
    @Volatile var room = 0.5f
    @Volatile var damp = 0.5f
    @Volatile var preMs = 0f
    @Volatile var width = 1f

    /** Sets the space: 0 Off, 1 Studio, 2 Stage, 3 Big Hall, 4 Headset. */
    fun set(space: Int) {
        // wet, room size, damping, pre-delay ms, width.
        // Studio: a small, close, bright room. Stage: live, a medium hall in front of you.
        // Big Hall: a large concert hall with a long tail. Headset: very wide, almost no room.
        val v = when (space) {
            1 -> floatArrayOf(0.10f, 0.30f, 0.65f, 4f, 1.15f)
            2 -> floatArrayOf(0.20f, 0.65f, 0.40f, 18f, 1.35f)
            3 -> floatArrayOf(0.30f, 0.90f, 0.20f, 40f, 1.45f)
            4 -> floatArrayOf(0.05f, 0.15f, 0.70f, 0f, 1.75f)
            else -> floatArrayOf(0f, 0.5f, 0.5f, 0f, 1f)
        }
        room = v[1]; damp = v[2]; preMs = v[3]; width = v[4]; wet = v[0]
    }
}

/**
 * MYNA's sound spaces, inside the player itself so they work on every device and speaker:
 * a Freeverb-style room (8 comb + 4 all-pass filters per side) and a mid/side widener,
 * on 16-bit little-endian stereo.
 */
class SpaceDsp(private val s: Space) {
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

    /** Clears the room's echo (after a seek) and sets the sample rate. */
    fun reset(sampleRate: Int = rate) {
        rate = sampleRate
        val k = rate / 44100f
        fun n(x: Int) = max(1, (x * k).toInt())
        combsL = COMBS.map { Comb(n(it)) }; combsR = COMBS.map { Comb(n(it + SPREAD)) }
        allL = ALLS.map { AllPass(n(it)) }; allR = ALLS.map { AllPass(n(it + SPREAD)) }
        pre = FloatArray(max(1, rate / 10)); preI = 0
    }

    init { reset() }

    /** Reads stereo frames from [input] and writes them, with the space and [gain], to [out]. */
    fun process(input: ByteBuffer, out: ByteBuffer, gain: Float = 1f) {
        val wet = s.wet; val width = s.width
        if (wet == 0f && width == 1f && gain == 1f) { out.put(input); return }
        val fb = s.room * 0.28f + 0.7f
        val damp = s.damp * 0.4f
        val delay = (s.preMs * rate / 1000).toInt().coerceIn(0, pre.size - 1)
        val w = wet * 3f
        val dry = 1f - wet * 0.5f
        val room = wet > 0f
        while (input.remaining() >= 4) {
            val l = input.short / 32768f
            val r = input.short / 32768f
            var rl = 0f; var rr = 0f
            if (room) {
                // Pre-delay: the gap before the room answers, longer in bigger rooms.
                pre[preI] = (l + r) * 0.015f
                val x = pre[(preI - delay + pre.size) % pre.size]
                if (++preI == pre.size) preI = 0
                for (c in combsL) rl += c.run(x, fb, damp)
                for (c in combsR) rr += c.run(x, fb, damp)
                for (a in allL) rl = a.run(rl)
                for (a in allR) rr = a.run(rr)
            }
            var ol = l * dry + rl * w
            var or = r * dry + rr * w
            // Mid / side: more side makes the sound wider than the speakers.
            val m = (ol + or) * 0.5f; val sd = (ol - or) * 0.5f * width
            ol = (m + sd) * gain; or = (m - sd) * gain
            out.putShort((ol.coerceIn(-1f, 1f) * 32767).toInt().toShort())
            out.putShort((or.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        }
    }

    private companion object {
        val COMBS = listOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLS = listOf(556, 441, 341, 225)
        const val SPREAD = 23
    }
}
