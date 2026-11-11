package com.lscardinali.trill.ui

import android.view.KeyEvent as AKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lscardinali.trill.Flavor
import com.lscardinali.trill.FolderTarget
import com.lscardinali.trill.MainViewModel
import com.lscardinali.trill.Tab
import com.lscardinali.trill.audio.Quality
import com.lscardinali.trill.data.Track
import com.lscardinali.trill.ui.theme.CC
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(vm: MainViewModel) {
    val searchFocus = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    var focusedTrack by remember { mutableStateOf<Track?>(null) }
    var focusOnUse by remember { mutableStateOf(false) }

    PadShortcuts { key, repeat ->
        when (key) {
            AKeyEvent.KEYCODE_BUTTON_Y, AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (vm.playingKey != null) vm.stopPlayback() else focusedTrack?.let(vm::togglePreview); true
            }
            AKeyEvent.KEYCODE_BUTTON_X -> { focusedTrack?.let(vm::useTrack); true }
            AKeyEvent.KEYCODE_BUTTON_L1 -> { vm.seekPreview(-10_000); true }
            AKeyEvent.KEYCODE_BUTTON_R1 -> { vm.seekPreview(10_000); true }
            AKeyEvent.KEYCODE_BUTTON_L2, AKeyEvent.KEYCODE_BUTTON_R2 -> {
                if (repeat == 0) {
                    val d = if (key == AKeyEvent.KEYCODE_BUTTON_L2) -1 else 1
                    vm.pickFlavor(Flavor.entries[(vm.flavor.ordinal + d).mod(Flavor.entries.size)])
                }
                true
            }
            AKeyEvent.KEYCODE_BUTTON_SELECT -> { if (repeat == 0) searchFocus.requestFocus(); true }
            else -> false
        }
    }

    LaunchedEffect(Unit) { searchFocus.requestSoon() }
    LaunchedEffect(vm.resultsVersion) {
        if (vm.resultsVersion > 0 && vm.results.isNotEmpty()) firstResult.requestSoon()
    }

    Column(Modifier.fillMaxSize()) {
        Header("Trill", onBack = null, showIcon = true) { NowPlayingChip(vm) }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ---------- Left pane ----------
            Column(
                Modifier.width(290.dp).fillMaxHeight().padding(start = 18.dp, end = 10.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditablePill(
                    value = vm.query,
                    placeholder = "Search a game",
                    icon = Icons.Rounded.Search,
                    onCommit = { vm.query = it; vm.search(it) },
                    imeAction = ImeAction.Search,
                    focusRequester = searchFocus,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Tab.entries.forEach { t ->
                    NavRow(
                        icon = t.icon(),
                        title = t.title,
                        subtitle = t.subtitle,
                        selected = vm.tab == t,
                        onClick = { vm.tab = t },
                        onFocus = { if (it) vm.tab = t },
                    )
                }
            }
            // ---------- Right pane ----------
            Box(Modifier.weight(1f).fillMaxHeight().padding(end = 14.dp)) {
                when (vm.tab) {
                    Tab.Results -> ResultsPane(vm, firstResult) { t, onUse -> focusedTrack = t; focusOnUse = onUse }
                    Tab.Library -> LibraryPane(vm)
                    Tab.Jingles -> JinglesPane(vm)
                    Tab.Settings -> SettingsPane(vm)
                }
            }
        }
        val playing = vm.playingKey != null
        HintBar(
            buildList {
                when (vm.tab) {
                    Tab.Results -> {
                        val t = focusedTrack
                        if (t != null) {
                            add("A" to when { focusOnUse -> "Use"; vm.playingKey == t.url -> "Stop"; else -> "Preview" })
                            if (!focusOnUse) add("X" to "Use")
                        }
                        if (playing) {
                            add("Y" to "Stop")
                            add("L1 R1" to "Seek")
                        }
                        add("L2 R2" to "Flavor")
                    }
                    Tab.Library -> {
                        if (vm.games.isNotEmpty()) add("A" to "Find music")
                        add("SEL" to "Search")
                    }
                    Tab.Jingles -> {
                        if (vm.jingles.isNotEmpty()) add("A" to "Play")
                        if (playing) add("Y" to "Stop")
                    }
                    Tab.Settings -> add("A" to "Change")
                }
            },
        )
    }

    vm.folderPicker?.let { target ->
        val out = target == FolderTarget.Output
        FolderPicker(
            title = if (out) "Save jingles to" else "ROMs folder",
            initial = if (out) vm.outputDir else vm.romsDir.ifBlank { null },
            onPick = { path ->
                if (out) vm.setOutputDirectory(path) else vm.setRomsDirectory(path)
                vm.folderPicker = null
            },
            onDismiss = { vm.folderPicker = null },
        )
    }
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Results -> Icons.Rounded.SmartDisplay
    Tab.Library -> Icons.Rounded.SportsEsports
    Tab.Jingles -> Icons.Rounded.LibraryMusic
    Tab.Settings -> Icons.Rounded.Settings
}

@Composable
private fun NowPlayingChip(vm: MainViewModel) {
    if (vm.playingKey == null) return
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(CC.Card).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.GraphicEq, null, tint = CC.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("${formatMs(vm.playPos)} / ${formatMs(vm.playDur)}", color = CC.Sub, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Glyph("Y", size = 18.dp)
        Spacer(Modifier.width(4.dp))
        Text("Stop", color = CC.Sub, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String, content: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = CC.Accent, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = CC.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = CC.Sub, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

// =====================================================================================

@Composable
private fun ResultsPane(vm: MainViewModel, firstResult: FocusRequester, onFocusTrack: (Track?, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Flavor.entries) { f ->
                val sel = vm.flavor == f
                CocoonCard(
                    onClick = { vm.pickFlavor(f) },
                    shape = RoundedCornerShape(50),
                    color = if (sel) Color.Transparent else CC.Card,
                    focusedColor = if (sel) Color.Transparent else CC.CardHi,
                ) {
                    Text(
                        f.label,
                        color = if (sel) CC.OnAccent else CC.Text,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .then(if (sel) Modifier.background(CC.Accent) else Modifier)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        when {
            vm.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CC.Accent)
            }
            vm.searchError != null -> EmptyState(Icons.Rounded.MusicNote, "Hmm.", vm.searchError!!)
            vm.results.isEmpty() -> EmptyState(
                Icons.Rounded.VideogameAsset,
                "Find a jingle",
                "Search a game name, or pick one from My games.\nPreview tracks with ${ButtonLabels.display("A")}, then press ${ButtonLabels.display("X")} to use one.",
            )
            else -> {
                val state = rememberLazyListState()
                LaunchedEffect(vm.resultsVersion) { state.scrollToItem(0) }
                LazyColumn(
                    state = state,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(vm.results, key = { _, t -> t.url }) { i, t ->
                        ResultRow(vm, t, if (i == 0) firstResult else null, onFocusTrack)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(vm: MainViewModel, t: Track, fr: FocusRequester?, onFocusTrack: (Track?, Boolean) -> Unit) {
    val playing = vm.playingKey == t.url
    val loading = vm.loadingKey == t.url
    Row(verticalAlignment = Alignment.CenterVertically) {
        CocoonCard(
            onClick = { vm.togglePreview(t) },
            modifier = Modifier.weight(1f),
            focusRequester = fr,
            onFocus = { onFocusTrack(if (it) t else null, false) },
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(CC.Glyph)) {
                    AsyncImage(model = t.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC000000)).padding(horizontal = 5.dp, vertical = 1.dp),
                    ) { Text(formatMs(t.durationSec * 1000), color = Color.White, fontSize = 10.sp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = CC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                    Text(t.channel, color = CC.Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (playing && !loading && vm.playDur > 0) {
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { (vm.playPos.toFloat() / vm.playDur).coerceIn(0f, 1f) },
                            color = CC.Accent, trackColor = CC.Glyph, drawStopIndicator = {},
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(color = CC.Accent, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                        playing -> Icon(Icons.Rounded.Stop, null, tint = CC.Accent, modifier = Modifier.size(28.dp))
                        else -> Icon(Icons.Rounded.PlayArrow, null, tint = CC.Sub, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        PillButton("Use", "X", onClick = { vm.useTrack(t) }, onFocus = { onFocusTrack(if (it) t else null, it) })
    }
}

// =====================================================================================

@Composable
private fun LibraryPane(vm: MainViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(vm.hasStorage) { if (vm.hasStorage) vm.loadGames() }
    if (!vm.hasStorage) {
        EmptyState(Icons.Rounded.Lock, "File access needed", "Trill reads your ROM folders to list games\nand saves jingles to ${vm.outputDir}.") {
            PillButton("Allow access", "A", onClick = { vm.openStoragePermission(ctx) })
        }
        return
    }
    if (!vm.gamesLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CC.Accent) }
        return
    }
    if (vm.games.isEmpty()) {
        EmptyState(Icons.Rounded.SportsEsports, "No ROMs found", "Set your ROMs folder in Settings.")
        return
    }
    val platforms = remember(vm.games) { vm.games.map { it.platform }.distinct() }
    val shown = remember(vm.games, vm.platformFilter) { vm.games.filter { vm.platformFilter == null || it.platform == vm.platformFilter } }
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(listOf<String?>(null) + platforms) { p ->
                val sel = vm.platformFilter == p
                CocoonCard(
                    onClick = { vm.platformFilter = p },
                    shape = RoundedCornerShape(50),
                    color = if (sel) Color.Transparent else CC.Card,
                    focusedColor = if (sel) Color.Transparent else CC.CardHi,
                ) {
                    Text(
                        (p ?: "All").uppercase(),
                        color = if (sel) CC.OnAccent else CC.Text,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.then(if (sel) Modifier.background(CC.Accent) else Modifier)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shown, key = { it.platform + "/" + it.fileName }) { g ->
                OptionRow(Icons.Rounded.VideogameAsset, g.name, g.platform.uppercase(), onClick = { vm.pickGame(g) }) {
                    Icon(Icons.Rounded.Search, null, tint = CC.Sub, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// =====================================================================================

@Composable
private fun JinglesPane(vm: MainViewModel) {
    LaunchedEffect(vm.outputDir) { vm.loadJingles() }
    if (vm.jingles.isEmpty()) {
        EmptyState(Icons.Rounded.LibraryMusic, "No jingles yet", "Everything you save lands in\n${vm.outputDir}")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(vm.outputDir, color = CC.Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp)) }
        items(vm.jingles, key = { it.absolutePath }) { f: File ->
            val playing = vm.playingKey == f.absolutePath
            OptionRow(
                if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                f.nameWithoutExtension,
                "${formatBytes(f.length())} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.lastModified()))}",
                onClick = { vm.toggleFile(f) },
            ) {
                Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = if (playing) CC.Accent else CC.Sub, modifier = Modifier.size(26.dp))
            }
        }
    }
}

// =====================================================================================

@Composable
private fun SettingsPane(vm: MainViewModel) {
    val ctx = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionLabel("Folders") }
        item {
            FolderRow("Save jingles to", vm.outputDir, "Choose a folder") { vm.folderPicker = FolderTarget.Output }
        }
        item {
            Text(
                "In Cocoon: edit a game → Jingle → pick the file from this folder.",
                color = CC.Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp),
            )
        }
        item {
            FolderRow("ROMs folder", vm.romsDir, "Auto-detect any “Roms” folder") { vm.folderPicker = FolderTarget.Roms }
        }
        if (vm.romsDir.isNotBlank()) item {
            OptionRow(Icons.Rounded.SportsEsports, "Use auto-detect for ROMs", "Forget the custom ROMs folder", onClick = { vm.setRomsDirectory("") })
        }
        item { SectionLabel("Defaults") }
        item {
            val q = Quality.ALL[vm.defaultQualityIndex]
            OptionRow(
                Icons.Rounded.Tune, "Default quality · ${q.label}", "${q.detail} Ogg Opus · ~${q.bitrate * 10 / 8 / 1000} KB per 10 s",
                onClick = { vm.cycleDefaultQuality(1) },
                onKey = cycleKeys { vm.cycleDefaultQuality(it) },
            ) { Text("◀  ${q.label}  ▶", color = CC.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        item {
            OptionRow(
                Icons.Rounded.SportsEsports,
                "Switch-style button labels",
                "Swap A/B and X/Y in the legend to match Nintendo-style buttons",
                onClick = vm::toggleSwitchStyle,
            ) { Toggle(vm.switchStyle) }
        }
        item {
            OptionRow(
                Icons.Rounded.Lock,
                "File access",
                if (vm.hasStorage) "Granted — jingles can be written anywhere" else "Needed to save into shared folders",
                onClick = { vm.openStoragePermission(ctx) },
            ) { Toggle(vm.hasStorage) }
        }
        item {
            Text(
                "Quality presets: " + Quality.ALL.joinToString("  ·  ") { "${it.label} ${it.bitrate / 1000}k" },
                color = CC.Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = CC.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
}

/** Left/right on a focused row cycles a value instead of moving focus. */
fun cycleKeys(onCycle: (Int) -> Unit): (androidx.compose.ui.input.key.KeyEvent) -> Boolean = {
    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { onCycle(-1); true }
    else if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) { onCycle(1); true }
    else false
}
