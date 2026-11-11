package com.lscardinali.trill.audio

import kotlin.math.abs
import kotlin.math.max

class Waveform(
    /** Peak amplitude 0..1 for each [bucketMs] slice. */
    val peaks: FloatArray,
    val bucketMs: Int,
    val durationMs: Long,
) {
    /** Max peak in [fromMs, toMs). */
    fun peakBetween(fromMs: Double, toMs: Double): Float {
        val a = (fromMs / bucketMs).toInt().coerceIn(0, peaks.size - 1)
        val b = (toMs / bucketMs).toInt().coerceIn(a, peaks.size - 1)
        var m = 0f
        for (i in a..b) if (peaks[i] > m) m = peaks[i]
        return m
    }

    /** First moment the music actually starts (skips leading silence). */
    fun firstSoundMs(threshold: Float = 0.06f): Long {
        val idx = peaks.indexOfFirst { it > threshold }
        return if (idx < 0) 0 else (idx.toLong() * bucketMs - 100).coerceAtLeast(0)
    }

    companion object {
        const val BUCKET_MS = 20

        fun analyze(path: String, onProgress: (Float) -> Unit): Waveform {
            val decoder = PcmDecoder(path)
            val peaks = ArrayList<Float>(16_384)
            var bucketPeak = 0
            var framesInBucket = 0
            var framesPerBucket = -1
            var totalFrames = 0L
            var rate = 48_000
            var lastReport = 0L
            decoder.decode { pcm, frames, sampleRate, channels, ptsUs ->
                if (framesPerBucket < 0) {
                    rate = sampleRate
                    framesPerBucket = sampleRate * BUCKET_MS / 1000
                }
                var i = 0
                for (f in 0 until frames) {
                    var p = 0
                    for (c in 0 until channels) p = max(p, abs(pcm[i++].toInt()))
                    if (p > bucketPeak) bucketPeak = p
                    if (++framesInBucket >= framesPerBucket) {
                        peaks.add(bucketPeak / 32768f)
                        bucketPeak = 0
                        framesInBucket = 0
                    }
                }
                totalFrames += frames
                if (decoder.durationUs > 0 && ptsUs - lastReport > 500_000) {
                    lastReport = ptsUs
                    onProgress((ptsUs.toFloat() / decoder.durationUs).coerceIn(0f, 1f))
                }
                !Thread.currentThread().isInterrupted
            }
            if (framesInBucket > 0) peaks.add(bucketPeak / 32768f)
            if (peaks.isEmpty()) peaks.add(0f)
            // Normalise display so quiet tracks still read well.
            val top = peaks.max().coerceAtLeast(0.05f)
            val arr = FloatArray(peaks.size) { (peaks[it] / top).coerceAtMost(1f) }
            return Waveform(arr, BUCKET_MS, totalFrames * 1000 / rate)
        }
    }
}
