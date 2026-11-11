package com.lscardinali.trill.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.IOException
import java.nio.ByteOrder

/**
 * Streams 16-bit interleaved PCM out of any container/codec Android can decode.
 */
class PcmDecoder(private val path: String) {

    var durationUs: Long = 0
        private set

    /**
     * @param onPcm receives (interleaved samples, frame count, sampleRate, channels, ptsUs of first frame);
     *              return false to stop early.
     */
    fun decode(
        startUs: Long = 0,
        endUs: Long = Long.MAX_VALUE,
        onPcm: (ShortArray, Int, Int, Int, Long) -> Boolean,
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        val trackIndex = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run { extractor.release(); throw IOException("No audio track") }

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var buffer = ShortArray(0)

        try {
            while (true) {
                if (Thread.currentThread().isInterrupted) return
                var fedAny = false
                while (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(0)
                    if (inIdx < 0) break
                    fedAny = true
                    run {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        val pastEnd = endUs != Long.MAX_VALUE && extractor.sampleTime > endUs + 200_000
                        if (size < 0 || pastEnd) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                drain@ while (true) {
                val outIdx = codec.dequeueOutputBuffer(info, if (fedAny) 0 else 2_000)
                if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break@drain
                fedAny = true
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIdx >= 0 -> {
                        val out = codec.getOutputBuffer(outIdx)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        out.order(ByteOrder.nativeOrder())
                        val samples: Int
                        if (floatPcm) {
                            val fb = out.asFloatBuffer()
                            samples = fb.remaining()
                            if (buffer.size < samples) buffer = ShortArray(samples)
                            for (i in 0 until samples) buffer[i] = (fb.get(i).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        } else {
                            val sb = out.asShortBuffer()
                            samples = sb.remaining()
                            if (buffer.size < samples) buffer = ShortArray(samples)
                            sb.get(buffer, 0, samples)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (samples > 0) {
                            val keepGoing = onPcm(buffer, samples / channels, sampleRate, channels, info.presentationTimeUs)
                            if (!keepGoing) return
                        }
                        if (eos || info.presentationTimeUs > endUs) return
                    }
                }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
    }
}
