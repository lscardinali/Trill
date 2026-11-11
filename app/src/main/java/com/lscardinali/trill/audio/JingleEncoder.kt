package com.lscardinali.trill.audio

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Quality(val label: String, val channels: Int, val bitrate: Int) {
    val detail get() = "${bitrate / 1000} kbps ${if (channels == 1) "mono" else "stereo"}"

    companion object {
        val ALL = listOf(
            Quality("Tiny", 1, 16_000),
            Quality("Small", 1, 24_000),
            Quality("Clear", 2, 40_000),
            Quality("Rich", 2, 64_000),
        )
        const val DEFAULT = 2 // Clear
    }
}

data class EncodeSpec(
    val startMs: Long,
    val endMs: Long,
    val fadeInMs: Int,
    val fadeOutMs: Int,
    val normalize: Boolean,
    val quality: Quality,
)

/**
 * Cuts a section of the source, applies fades/normalisation and encodes it as Ogg Opus
 * (the most efficient codec Android ships an encoder for; ~3 KB/s at 24 kbps).
 */
object JingleEncoder {

    private const val OUT_RATE = 48_000

    fun encode(sourcePath: String, spec: EncodeSpec, out: File): Long {
        val channels = spec.quality.channels
        val pcm = readRange(sourcePath, spec.startMs * 1000, spec.endMs * 1000, channels)
        applyFadesAndGain(pcm, spec)
        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, out.name + ".part")
        writeOpus(pcm, channels, spec.quality.bitrate, tmp)
        if (out.exists()) out.delete()
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true); tmp.delete()
        }
        return out.length()
    }

    /** Planar float channels at 48 kHz. */
    private fun readRange(path: String, startUs: Long, endUs: Long, outChannels: Int): Array<FloatArray> {
        var srcRate = 0
        var srcChannels = 0
        val collected = Array(2) { FloatList() }
        PcmDecoder(path).decode(startUs, endUs) { buf, frames, rate, ch, ptsUs ->
            srcRate = rate; srcChannels = ch
            val usPerFrame = 1_000_000.0 / rate
            for (f in 0 until frames) {
                val t = ptsUs + f * usPerFrame
                if (t < startUs) continue
                if (t >= endUs) return@decode false
                val l = buf[f * ch] / 32768f
                val r = if (ch > 1) buf[f * ch + 1] / 32768f else l
                collected[0].add(l); collected[1].add(r)
            }
            true
        }
        if (collected[0].size == 0) throw IOException("Nothing decoded in that range")

        val planes: Array<FloatArray> = if (outChannels == 1) {
            val l = collected[0]; val r = collected[1]
            arrayOf(FloatArray(l.size) { (l[it] + r[it]) * 0.5f })
        } else arrayOf(collected[0].toArray(), collected[1].toArray())

        return if (srcRate == OUT_RATE) planes else Array(planes.size) { resample(planes[it], srcRate, OUT_RATE) }
            .also { check(srcChannels > 0) }
    }

    /** Catmull-Rom interpolation; plenty for a launcher jingle going into a lossy codec. */
    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        val outLen = (input.size.toLong() * to / from).toInt()
        val step = from.toDouble() / to
        val out = FloatArray(outLen)
        val last = input.size - 1
        for (i in 0 until outLen) {
            val x = i * step
            val i1 = x.toInt()
            val t = (x - i1).toFloat()
            val p0 = input[max(i1 - 1, 0)]
            val p1 = input[min(i1, last)]
            val p2 = input[min(i1 + 1, last)]
            val p3 = input[min(i1 + 2, last)]
            out[i] = p1 + 0.5f * t * (p2 - p0 + t * (2f * p0 - 5f * p1 + 4f * p2 - p3 + t * (3f * (p1 - p2) + p3 - p0)))
        }
        return out
    }

    private fun applyFadesAndGain(planes: Array<FloatArray>, spec: EncodeSpec) {
        val n = planes[0].size
        var gain = 1f
        if (spec.normalize) {
            var peak = 0f
            for (p in planes) for (s in p) peak = max(peak, abs(s))
            if (peak > 0.001f) gain = (0.89f / peak).coerceAtMost(4f) // -1 dBFS, max +12 dB
        }
        val fadeIn = (spec.fadeInMs * OUT_RATE / 1000).coerceAtMost(n / 2)
        val fadeOut = (spec.fadeOutMs * OUT_RATE / 1000).coerceAtMost(n / 2)
        for (p in planes) for (i in 0 until n) {
            var g = gain
            if (i < fadeIn) g *= i.toFloat() / fadeIn
            val fromEnd = n - 1 - i
            if (fromEnd < fadeOut) {
                val x = fromEnd.toFloat() / fadeOut
                g *= x * x // smooth-ish tail
            }
            p[i] = (p[i] * g).coerceIn(-1f, 1f)
        }
    }

    private fun writeOpus(planes: Array<FloatArray>, channels: Int, bitrate: Int, out: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, OUT_RATE, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_COMPLEXITY, 10)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = try {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        } catch (e: Exception) {
            throw IOException("This device has no Opus encoder", e)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val frames = planes[0].size
        var fed = 0
        var inputDone = false
        val info = MediaCodec.BufferInfo()
        var preSkip = -1
        var writer: OggOpusWriter? = null
        val stream = out.outputStream().buffered()
        try {
            loop@ while (true) {
                var progressed = false
                while (!inputDone) {
                    val idx = codec.dequeueInputBuffer(0)
                    if (idx < 0) break
                    progressed = true
                    run {
                        val buf: ByteBuffer = codec.getInputBuffer(idx)!!
                        buf.clear()
                        buf.order(ByteOrder.LITTLE_ENDIAN)
                        val canFit = min(buf.capacity() / (2 * channels), 960 * 4)
                        val count = min(canFit, frames - fed)
                        val ptsUs = fed * 1_000_000L / OUT_RATE
                        if (count <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            for (f in fed until fed + count) for (c in 0 until channels) {
                                buf.putShort((planes[c][f] * 32767f).toInt().toShort())
                            }
                            codec.queueInputBuffer(idx, 0, count * 2 * channels, ptsUs, 0)
                            fed += count
                        }
                    }
                }
                while (true) {
                    val outIdx = codec.dequeueOutputBuffer(info, if (progressed) 0 else 2_000)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    progressed = true
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (preSkip < 0) preSkip = preSkipFromFormat(codec.outputFormat)
                        continue
                    }
                    if (outIdx < 0) continue
                    val data = codec.getOutputBuffer(outIdx)!!
                    data.position(info.offset); data.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size).also { data.get(it) }
                    codec.releaseOutputBuffer(outIdx, false)
                    val limit = ((preSkip.takeIf { it >= 0 } ?: 312) + frames).toLong()
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        preSkipFromOpusHead(bytes)?.let { preSkip = it }
                    } else if (bytes.isNotEmpty()) {
                        val w = writer ?: OggOpusWriter(stream, channels, preSkip.takeIf { it >= 0 } ?: 312).also { writer = it }
                        w.writePacket(bytes, limit)
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        val w = writer ?: OggOpusWriter(stream, channels, preSkip.takeIf { it >= 0 } ?: 312)
                        w.finish((preSkip.takeIf { it >= 0 } ?: 312).toLong() + frames)
                        break@loop
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            stream.close()
        }
    }

    /** csd-1 carries the encoder delay in nanoseconds on Android's Opus codecs. */
    private fun preSkipFromFormat(f: MediaFormat): Int {
        val csd = f.getByteBuffer("csd-1") ?: return -1
        if (csd.remaining() < 8) return -1
        val ns = csd.duplicate().order(ByteOrder.LITTLE_ENDIAN).long
        return (ns * OUT_RATE / 1_000_000_000L).toInt().takeIf { it in 1..20_000 } ?: -1
    }

    /** Codec-config buffers embed an "OpusHead"; pre-skip is the u16 at offset 10. */
    private fun preSkipFromOpusHead(b: ByteArray): Int? {
        val magic = "OpusHead".toByteArray()
        for (i in 0..b.size - 19) {
            if ((magic.indices).all { b[i + it] == magic[it] }) {
                return (b[i + 10].toInt() and 0xFF) or ((b[i + 11].toInt() and 0xFF) shl 8)
            }
        }
        return null
    }

    private class FloatList {
        var data = FloatArray(1 shl 16)
        var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        operator fun get(i: Int) = data[i]
        fun toArray() = data.copyOf(size)
    }
}
