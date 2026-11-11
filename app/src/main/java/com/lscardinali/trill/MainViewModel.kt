package com.lscardinali.trill

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lscardinali.trill.audio.EncodeSpec
import com.lscardinali.trill.audio.JingleEncoder
import com.lscardinali.trill.audio.PreviewPlayer
import com.lscardinali.trill.audio.Quality
import com.lscardinali.trill.audio.Waveform
import com.lscardinali.trill.data.Game
import com.lscardinali.trill.ui.ButtonLabels
import com.lscardinali.trill.data.Library
import com.lscardinali.trill.data.Track
import com.lscardinali.trill.data.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab(val title: String, val subtitle: String) {
    Results("YouTube", "Theme songs & soundtracks"),
    Library("My games", "Pick from your ROMs"),
    Jingles("My jingles", "Everything you've made"),
    Settings("Settings", "Folder & defaults"),
}

enum class Flavor(val label: String, val suffix: String) {
    Title("Title theme", "title theme"),
    Main("Main theme", "main theme"),
    Ost("Soundtrack", "OST"),
    Menu("Menu music", "menu music"),
}

sealed interface Screen {
    data object Home : Screen
    data class Working(val title: String, val stage: String, val progress: Float?) : Screen
    data object Editor : Screen
}

enum class Handle { Start, End }

enum class FolderTarget { Output, Roms }

data class EditorState(
    val track: Track,
    val source: File,
    val wave: Waveform,
    val startMs: Long,
    val endMs: Long,
    val active: Handle = Handle.Start,
    val gameName: String,
)

data class Saved(val file: File, val bytes: Long, val seconds: Float)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("trill", Context.MODE_PRIVATE)
    val player = PreviewPlayer(app)

    // ---- Home ----
    var tab by mutableStateOf(Tab.Results)
    var query by mutableStateOf(prefs.getString("lastQuery", "") ?: "")
    var flavor by mutableStateOf(Flavor.entries[prefs.getInt("flavor", 0)])
    var results by mutableStateOf<List<Track>>(emptyList())
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)
    var searchedOnce by mutableStateOf(false)
    var resultsVersion by mutableStateOf(0)

    var games by mutableStateOf<List<Game>>(emptyList())
    var gamesLoaded by mutableStateOf(false)
    var platformFilter by mutableStateOf<String?>(null)
    var jingles by mutableStateOf<List<File>>(emptyList())

    // ---- Playback ----
    /** Key of what's playing: a video URL, "trim", or a file path. */
    var playingKey by mutableStateOf<String?>(null)
    var loadingKey by mutableStateOf<String?>(null)
    var playPos by mutableLongStateOf(0L)
    var playDur by mutableLongStateOf(0L)

    // ---- Flow ----
    var screen by mutableStateOf<Screen>(Screen.Home)
    var editor by mutableStateOf<EditorState?>(null)
    var saved by mutableStateOf<Saved?>(null)
    var toast by mutableStateOf<String?>(null)

    // ---- Settings ----
    var outputDir by mutableStateOf(prefs.getString("outputDir", null) ?: Library.defaultOutputDir(app))
    var romsDir by mutableStateOf(prefs.getString("romsDir", "") ?: "")
    /** Saved default (Settings). */
    var defaultQualityIndex by mutableStateOf(prefs.getInt("defaultQuality", Quality.DEFAULT))
    /** Quality for the jingle being edited; starts at the default each time. */
    var qualityIndex by mutableStateOf(defaultQualityIndex)
    var fadeIn by mutableStateOf(prefs.getBoolean("fadeIn", true))
    var fadeOut by mutableStateOf(prefs.getBoolean("fadeOut", true))
    var normalize by mutableStateOf(prefs.getBoolean("normalize", true))
    var hasStorage by mutableStateOf(Environment.isExternalStorageManager())
    var folderPicker by mutableStateOf<FolderTarget?>(null)
    var switchStyle by mutableStateOf(prefs.getBoolean("switchStyle", false))

    fun toggleSwitchStyle() {
        switchStyle = !switchStyle
        ButtonLabels.switchStyle = switchStyle
        prefs.edit().putBoolean("switchStyle", switchStyle).apply()
    }

    val quality get() = Quality.ALL[qualityIndex]

    private var workJob: Job? = null
    private var previewJob: Job? = null

    init {
        ButtonLabels.switchStyle = switchStyle
        player.onEnded = { playingKey = null }
        player.onError = { code ->
            playingKey = null; loadingKey = null
            toast = "Couldn't play that ($code)"
        }
        viewModelScope.launch {
            while (true) {
                delay(80)
                if (playingKey != null) {
                    playPos = player.positionMs
                    playDur = player.durationMs
                    if (loadingKey != null && player.isPlaying) loadingKey = null
                }
            }
        }
    }

    fun refreshPermission() {
        hasStorage = Environment.isExternalStorageManager()
    }

    fun openStoragePermission(ctx: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${ctx.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }.onFailure {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ================= Search =================

    fun search(q: String = query) {
        val text = q.trim()
        if (text.isEmpty()) return
        query = text
        tab = Tab.Results
        prefs.edit().putString("lastQuery", text).putInt("flavor", flavor.ordinal).apply()
        stopPlayback()
        searching = true
        searchError = null
        viewModelScope.launch {
            try {
                results = YouTube.search("$text ${flavor.suffix}")
                if (results.isEmpty()) searchError = "No results. Try another flavor or spelling."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchError = "Search failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                searching = false
                searchedOnce = true
                resultsVersion++
            }
        }
    }

    fun pickFlavor(f: Flavor) {
        if (f == flavor) return
        flavor = f
        if (query.isNotBlank()) search()
    }

    fun togglePreview(track: Track) {
        if (playingKey == track.url) { stopPlayback(); return }
        stopPlayback()
        playingKey = track.url
        loadingKey = track.url
        playPos = 0; playDur = track.durationSec * 1000
        previewJob = viewModelScope.launch {
            try {
                val src = YouTube.resolve(track.url)
                if (playingKey == track.url) player.playUrl(src.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (playingKey == track.url) { playingKey = null; loadingKey = null }
                toast = "Preview failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun seekPreview(deltaMs: Long) {
        if (playingKey != null) player.seekBy(deltaMs)
    }

    fun stopPlayback() {
        previewJob?.cancel()
        player.stop()
        playingKey = null
        loadingKey = null
    }

    // ================= Library / jingles =================

    fun loadGames(force: Boolean = false) {
        if (gamesLoaded && !force) return
        viewModelScope.launch {
            games = withContext(Dispatchers.IO) { Library.scan(romsDir) }
            gamesLoaded = true
        }
    }

    fun pickGame(g: Game) {
        query = g.name
        search(g.name)
    }

    fun loadJingles() {
        viewModelScope.launch {
            jingles = withContext(Dispatchers.IO) {
                File(outputDir).listFiles { f -> f.isFile && f.extension.lowercase() in setOf("ogg", "opus", "mp3", "wav") }
                    .orEmpty().sortedByDescending { it.lastModified() }
            }
        }
    }

    fun toggleFile(file: File) {
        if (playingKey == file.absolutePath) { stopPlayback(); return }
        stopPlayback()
        playingKey = file.absolutePath
        player.playFile(file)
    }

    // ================= Use a track =================

    fun useTrack(track: Track) {
        stopPlayback()
        workJob?.cancel()
        screen = Screen.Working(track.title, "Finding the audio stream…", null)
        workJob = viewModelScope.launch {
            try {
                val src = YouTube.resolve(track.url)
                val dir = File(getApplication<Application>().cacheDir, "src").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "source.${src.extension}")
                screen = Screen.Working(track.title, "Downloading", 0f)
                YouTube.download(src, file) { p -> screen = Screen.Working(track.title, "Downloading", p) }
                screen = Screen.Working(track.title, "Reading the waveform", 0f)
                val t0 = System.currentTimeMillis()
                val wave = runInterruptible(Dispatchers.Default) {
                    Waveform.analyze(file.absolutePath) { p -> screen = Screen.Working(track.title, "Reading the waveform", p) }
                }
                android.util.Log.i("Trill", "analyzed ${wave.durationMs} ms of audio in ${System.currentTimeMillis() - t0} ms")
                val start = wave.firstSoundMs()
                editor = EditorState(
                    track = track,
                    source = file,
                    wave = wave,
                    startMs = start,
                    endMs = (start + DEFAULT_LEN_MS).coerceAtMost(wave.durationMs),
                    gameName = query.ifBlank { track.title },
                )
                saved = null
                qualityIndex = defaultQualityIndex
                screen = Screen.Editor
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                screen = Screen.Home
                toast = "Couldn't get that track: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun cancelWork() {
        workJob?.cancel()
        screen = Screen.Home
    }

    // ================= Editor =================

    private fun updateSel(start: Long, end: Long) {
        val e = editor ?: return
        val dur = e.wave.durationMs
        var s = start.coerceIn(0, dur - MIN_LEN_MS)
        var en = end.coerceIn(s + MIN_LEN_MS, dur)
        if (en - s > MAX_LEN_MS) {
            if (s != e.startMs) en = s + MAX_LEN_MS else s = en - MAX_LEN_MS
        }
        editor = e.copy(startMs = s, endMs = en)
    }

    fun nudgeActive(deltaMs: Long) {
        val e = editor ?: return
        if (e.active == Handle.Start) updateSel(e.startMs + deltaMs, e.endMs)
        else updateSel(e.startMs, e.endMs + deltaMs)
    }

    fun setHandle(h: Handle, ms: Long) {
        val e = editor ?: return
        editor = e.copy(active = h)
        if (h == Handle.Start) updateSel(ms, e.endMs) else updateSel(e.startMs, ms)
    }

    fun shiftSelection(deltaMs: Long) {
        val e = editor ?: return
        val len = e.endMs - e.startMs
        val s = (e.startMs + deltaMs).coerceIn(0, e.wave.durationMs - len)
        editor = e.copy(startMs = s, endMs = s + len)
    }

    fun moveSelectionTo(centerMs: Long) {
        val e = editor ?: return
        shiftSelection(centerMs - (e.startMs + e.endMs) / 2)
    }

    fun toggleHandle() {
        val e = editor ?: return
        editor = e.copy(active = if (e.active == Handle.Start) Handle.End else Handle.Start)
    }

    fun setGameName(n: String) { editor = editor?.copy(gameName = n) }

    fun cycleDefaultQuality(d: Int) {
        defaultQualityIndex = (defaultQualityIndex + d).mod(Quality.ALL.size)
        qualityIndex = defaultQualityIndex
        prefs.edit().putInt("defaultQuality", defaultQualityIndex).apply()
    }

    fun cycleQuality(d: Int) {
        qualityIndex = (qualityIndex + d).mod(Quality.ALL.size)
    }

    fun toggleFadeIn() { fadeIn = !fadeIn; prefs.edit().putBoolean("fadeIn", fadeIn).apply() }
    fun toggleFadeOut() { fadeOut = !fadeOut; prefs.edit().putBoolean("fadeOut", fadeOut).apply() }
    fun toggleNormalize() { normalize = !normalize; prefs.edit().putBoolean("normalize", normalize).apply() }

    private fun spec(e: EditorState) = EncodeSpec(
        startMs = e.startMs,
        endMs = e.endMs,
        fadeInMs = if (fadeIn) 60 else 0,
        fadeOutMs = if (fadeOut) ((e.endMs - e.startMs) / 4).toInt().coerceIn(300, 2000) else 0,
        normalize = normalize,
        quality = quality,
    )

    var encoding by mutableStateOf(false)

    /** Previews the *encoded* result, so fades and quality are exactly what you'll get. */
    fun toggleTrimPreview() {
        val e = editor ?: return
        if (playingKey == TRIM_KEY) { stopPlayback(); return }
        stopPlayback()
        playingKey = TRIM_KEY
        loadingKey = TRIM_KEY
        previewJob = viewModelScope.launch {
            try {
                val out = File(getApplication<Application>().cacheDir, "preview.ogg")
                runInterruptible(Dispatchers.Default) { JingleEncoder.encode(e.source.absolutePath, spec(e), out) }
                if (playingKey == TRIM_KEY) player.playFile(out)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                playingKey = null; loadingKey = null
                toast = "Preview failed: ${ex.message}"
            }
        }
    }

    /** Encodes the selection into [outputDir] as "<game name>.ogg" (never overwrites). */
    fun save(ctx: Context) {
        val e = editor ?: return
        refreshPermission()
        if (!hasStorage) {
            toast = "Allow file access so jingles can go to $outputDir"
            openStoragePermission(ctx)
            return
        }
        if (encoding) return
        stopPlayback()
        encoding = true
        viewModelScope.launch {
            try {
                val name = sanitize(e.gameName).ifBlank { "jingle" }
                val dir = File(outputDir)
                var out = File(dir, "$name.ogg")
                var n = 2
                while (out.exists()) out = File(dir, "$name ($n).ogg").also { n++ }
                val te = System.currentTimeMillis()
                val bytes = runInterruptible(Dispatchers.Default) { JingleEncoder.encode(e.source.absolutePath, spec(e), out) }
                android.util.Log.i("Trill", "encoded $bytes bytes in ${System.currentTimeMillis() - te} ms -> $out")
                // Index it so it shows up under Audio/Recent in Cocoon's file picker.
                MediaScannerConnection.scanFile(ctx, arrayOf(out.absolutePath), arrayOf("audio/ogg"), null)
                saved = Saved(out, bytes, (e.endMs - e.startMs) / 1000f)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                toast = "Save failed: ${ex.message ?: ex.javaClass.simpleName}"
            } finally {
                encoding = false
            }
        }
    }

    fun playSaved() {
        saved?.let { toggleFile(it.file) }
    }

    fun closeEditor() {
        stopPlayback()
        saved = null
        screen = Screen.Home
    }

    fun setOutputDirectory(path: String) {
        outputDir = path.trim().ifBlank { Library.defaultOutputDir(getApplication()) }
        prefs.edit().putString("outputDir", outputDir).apply()
    }

    fun setRomsDirectory(path: String) {
        romsDir = path.trim()
        prefs.edit().putString("romsDir", romsDir).apply()
        loadGames(force = true)
    }

    override fun onCleared() {
        player.release()
    }

    companion object {
        const val TRIM_KEY = "trim"
        const val DEFAULT_LEN_MS = 8_000L
        const val MIN_LEN_MS = 1_000L
        const val MAX_LEN_MS = 60_000L

        fun sanitize(s: String) = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "").trim().take(120)
    }
}
