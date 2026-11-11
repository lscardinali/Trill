package com.lscardinali.trill

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lscardinali.trill.audio.EncodeSpec
import com.lscardinali.trill.audio.JingleEncoder
import com.lscardinali.trill.audio.Quality
import com.lscardinali.trill.audio.Waveform
import com.lscardinali.trill.data.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PipelineTest {
    @Test
    fun searchDownloadTrimEncode() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val results = YouTube.search("Rayman 2 title theme")
        Log.i("PipelineTest", "results=${results.size} first=${results.firstOrNull()}")
        assertTrue(results.isNotEmpty())
        val track = results.first { it.durationSec in 30..900 }
        val src = YouTube.resolve(track.url)
        Log.i("PipelineTest", "src ext=${src.extension} br=${src.bitrate}")
        val file = File(ctx.cacheDir, "test-src.${src.extension}")
        val t0 = System.currentTimeMillis()
        YouTube.download(src, file) { }
        Log.i("PipelineTest", "downloaded ${file.length()} bytes in ${System.currentTimeMillis() - t0} ms")
        assertTrue(file.length() > 100_000)
        val t1 = System.currentTimeMillis()
        val wave = Waveform.analyze(file.absolutePath) { }
        Log.i("PipelineTest", "wave dur=${wave.durationMs} peaks=${wave.peaks.size} firstSound=${wave.firstSoundMs()} in ${System.currentTimeMillis() - t1} ms")
        assertTrue(wave.durationMs > 20_000)
        for (q in Quality.ALL) {
            val out = File(ctx.cacheDir, "test-${q.label}.ogg")
            val start = wave.firstSoundMs()
            val te = System.currentTimeMillis()
            val bytes = JingleEncoder.encode(file.absolutePath, EncodeSpec(start, start + 8_000, 60, 2000, true, q), out)
            Log.i("PipelineTest", "${q.label} encoded in ${System.currentTimeMillis() - te} ms")
            val ex = MediaExtractor().apply { setDataSource(out.absolutePath) }
            val fmt = ex.getTrackFormat(0)
            Log.i("PipelineTest", "${q.label}: $bytes bytes, mime=${fmt.getString(MediaFormat.KEY_MIME)} dur=${fmt.getLong(MediaFormat.KEY_DURATION)} ch=${fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)}")
            assertEquals("audio/opus", fmt.getString(MediaFormat.KEY_MIME))
            assertTrue(fmt.getLong(MediaFormat.KEY_DURATION) in 7_500_000..8_500_000)
            ex.release()
            val back = Waveform.analyze(out.absolutePath) { }
            Log.i("PipelineTest", "${q.label} decodes back: ${back.durationMs} ms")
            assertTrue(back.durationMs in 7_900..8_100)
        }
    }
}
