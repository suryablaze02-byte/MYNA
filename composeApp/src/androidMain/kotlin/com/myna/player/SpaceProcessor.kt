package com.myna.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** Runs [SpaceDsp] inside ExoPlayer, on 16-bit stereo. */
@UnstableApi
class SpaceProcessor(s: Space) : BaseAudioProcessor() {
    private val dsp = SpaceDsp(s)

    override fun onConfigure(f: AudioFormat): AudioFormat {
        if (f.encoding != C.ENCODING_PCM_16BIT || f.channelCount != 2) return AudioFormat.NOT_SET
        dsp.reset(f.sampleRate)
        return f
    }

    override fun queueInput(input: ByteBuffer) {
        val out = replaceOutputBuffer(input.remaining())
        dsp.process(input, out)
        out.flip()
    }

    override fun onFlush() { if (isActive) dsp.reset() }
}
