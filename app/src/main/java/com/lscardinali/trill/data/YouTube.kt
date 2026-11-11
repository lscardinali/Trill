package com.lscardinali.trill.data

import com.lscardinali.trill.TrillApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

data class Track(
    val url: String,
    val title: String,
    val channel: String,
    val durationSec: Long,
    val thumbnail: String?,
)

data class AudioSource(
    val url: String,
    val extension: String,
    val bitrate: Int,
)

object YouTube {

    private val sourceCache = ConcurrentHashMap<String, AudioSource>()

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val extractor = ServiceList.YouTube.getSearchExtractor(
            query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "",
        )
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .filter { it.streamType != StreamType.LIVE_STREAM && it.duration in 1..(3 * 3600) }
            .map { item ->
                Track(
                    url = item.url,
                    title = item.name,
                    channel = item.uploaderName.orEmpty(),
                    durationSec = item.duration,
                    thumbnail = item.thumbnails.maxByOrNull { it.height }?.url,
                )
            }
    }

    /** Picks the best progressive, original-language audio-only stream. */
    suspend fun resolve(videoUrl: String): AudioSource = withContext(Dispatchers.IO) {
        sourceCache[videoUrl]?.let { return@withContext it }
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val candidates = info.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL)
        }.ifEmpty { info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP } }

        // MP4/AAC first: it has a reliable seek index for ExoPlayer and MediaExtractor.
        val best: AudioStream = candidates
            .sortedWith(
                compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                    .thenByDescending { it.averageBitrate.coerceAtMost(160) }
            )
            .firstOrNull() ?: throw IOException("No playable audio stream found")

        val ext = when (best.format) {
            MediaFormat.M4A -> "m4a"
            MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS -> "webm"
            else -> best.format?.suffix ?: "audio"
        }
        AudioSource(best.content, ext, best.averageBitrate).also { sourceCache[videoUrl] = it }
    }

    /** Headers googlevideo expects; mirrors NewPipe's YoutubeHttpDataSource. */
    fun streamHeaders(url: String): Map<String, String> = buildMap {
        put(
            "User-Agent",
            if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) YoutubeParsingHelper.getVisionOsUserAgent(null)
            else OkDownloader.USER_AGENT,
        )
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            put("Origin", "https://www.youtube.com")
            put("Referer", "https://www.youtube.com")
            put("Sec-Fetch-Dest", "empty")
            put("Sec-Fetch-Mode", "cors")
            put("Sec-Fetch-Site", "cross-site")
        }
    }

    val POST_BODY = byteArrayOf(0x78, 0)

    /**
     * Downloads the audio in ranged chunks (large single requests get throttled).
     */
    suspend fun download(src: AudioSource, dest: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val chunk = 1L shl 20
        var position = 0L
        var total = -1L
        val headers = streamHeaders(src.url)
        dest.outputStream().buffered().use { out ->
            while (total < 0 || position < total) {
                coroutineContext.ensureActive()
                val req = okhttp3.Request.Builder()
                    .url(src.url)
                    .post(POST_BODY.toRequestBody())
                    .header("Range", "bytes=$position-${position + chunk - 1}")
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                TrillApp.http.newCall(req).execute().use { r ->
                    if (r.code == 416) return@withContext
                    if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code})")
                    val range = r.header("Content-Range")
                    total = range?.substringAfter('/')?.toLongOrNull()
                        ?: r.body.contentLength().takeIf { r.code == 200 } ?: -1L
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    r.body.byteStream().use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress(((position + read).toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    position += read
                    if (r.code == 200 || read == 0L) total = position
                }
            }
        }
    }
}
