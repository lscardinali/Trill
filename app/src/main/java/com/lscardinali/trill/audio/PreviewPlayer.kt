package com.lscardinali.trill.audio

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lscardinali.trill.TrillApp
import com.lscardinali.trill.data.YouTube
import java.io.File

/** One shared ExoPlayer for YouTube previews, trim previews and saved jingles. */
@OptIn(UnstableApi::class)
class PreviewPlayer(context: Context) {

    var onEnded: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val http = OkHttpDataSource.Factory(TrillApp.http).setUserAgent(null)

    // googlevideo wants POST + specific headers, exactly like NewPipe's data source.
    private val youtube = ResolvingDataSource.Factory(http) { spec ->
        val host = spec.uri.host.orEmpty()
        if (host.endsWith("googlevideo.com")) {
            spec.buildUpon()
                .setHttpMethod(DataSpec.HTTP_METHOD_POST)
                .setHttpBody(YouTube.POST_BODY)
                .setHttpRequestHeaders(YouTube.streamHeaders(spec.uri.toString()))
                .build()
        } else spec
    }

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, youtube)))
        .build()
        .apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) onEnded?.invoke()
                }

                override fun onPlayerError(error: PlaybackException) {
                    onError?.invoke(error.errorCodeName)
                }
            })
        }

    fun playUrl(url: String, startMs: Long = 0) = play(MediaItem.fromUri(url), startMs)

    fun playFile(file: File) = play(MediaItem.fromUri(Uri.fromFile(file)), 0)

    private fun play(item: MediaItem, startMs: Long) {
        player.setMediaItem(item, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun seekBy(deltaMs: Long) {
        val d = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, d))
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    val positionMs get() = player.currentPosition
    val durationMs get() = player.duration.coerceAtLeast(0)
    val isPlaying get() = player.isPlaying

    fun release() = player.release()
}
