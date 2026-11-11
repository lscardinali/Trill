package com.lscardinali.trill.ui

import android.view.KeyEvent as AKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lscardinali.trill.Handle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lscardinali.trill.MainViewModel
import com.lscardinali.trill.Screen
import com.lscardinali.trill.ui.theme.CC

@Composable
fun EditorScreen(vm: MainViewModel) {
    val e = vm.editor ?: return
    val ctx = LocalContext.current
    val waveFocus = remember { FocusRequester() }
    var waveFocused by remember { mutableStateOf(false) }
    val trimPlaying = vm.playingKey == MainViewModel.TRIM_KEY
    val saved = vm.saved

    BackHandler(enabled = saved == null) { vm.closeEditor() }

    PadShortcuts { key, repeat ->
        if (vm.saved != null) {
            when (key) {
                AKeyEvent.KEYCODE_BUTTON_Y -> { if (repeat == 0) vm.playSaved(); true }
                else -> true
            }
        } else {
            val accel = if (repeat > 8) 3 else 1
            when (key) {
                AKeyEvent.KEYCODE_BUTTON_L1 -> { vm.shiftSelection(-500L * accel); true }
                AKeyEvent.KEYCODE_BUTTON_R1 -> { vm.shiftSelection(500L * accel); true }
                AKeyEvent.KEYCODE_BUTTON_L2 -> { vm.shiftSelection(-10_000); true }
                AKeyEvent.KEYCODE_BUTTON_R2 -> { vm.shiftSelection(10_000); true }
                AKeyEvent.KEYCODE_BUTTON_X -> { if (repeat == 0) vm.toggleHandle(); true }
                AKeyEvent.KEYCODE_BUTTON_Y, AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (repeat == 0) vm.toggleTrimPreview(); true }
                AKeyEvent.KEYCODE_BUTTON_START -> { if (repeat == 0) vm.save(ctx); true }
                else -> false
            }
        }
    }

    LaunchedEffect(Unit) { waveFocus.requestSoon() }

    val lenMs = e.endMs - e.startMs
    val estBytes = vm.quality.bitrate / 8 * lenMs / 1000 + 1200

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header("Make a jingle", onBack = { vm.closeEditor() })
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // ---------- Left: source + options ----------
                Column(
                    Modifier.width(290.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 10.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CC.Card).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            e.track.thumbnail, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(84.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(CC.Glyph),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.track.title, color = CC.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                            Text("${e.track.channel} · ${formatMs(e.wave.durationMs)}", color = CC.Sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OptionRow(Icons.Rounded.TrendingUp, "Fade in", "Soft start", onClick = vm::toggleFadeIn) { Toggle(vm.fadeIn) }
                    OptionRow(Icons.Rounded.TrendingDown, "Fade out", "Gentle tail at the end", onClick = vm::toggleFadeOut) { Toggle(vm.fadeOut) }
                    OptionRow(Icons.Rounded.Equalizer, "Normalize", "Even out the volume", onClick = vm::toggleNormalize) { Toggle(vm.normalize) }
                    OptionRow(
                        Icons.Rounded.Tune, "Quality", vm.quality.detail,
                        onClick = { vm.cycleQuality(1) }, onKey = cycleKeys { vm.cycleQuality(it) },
                    ) { Text("◀ ${vm.quality.label} ▶", color = CC.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }

                // ---------- Right: name, waveform, actions ----------
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EditablePill(
                        value = e.gameName,
                        placeholder = "Game name (file name)",
                        icon = Icons.Rounded.Edit,
                        onCommit = vm::setGameName,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OverviewWave(e, onMoveTo = vm::moveSelectionTo, modifier = Modifier.fillMaxWidth().height(34.dp))
                    DetailWave(
                        e = e,
                        playheadMs = if (trimPlaying && vm.loadingKey == null) e.startMs + vm.playPos else null,
                        onNudge = vm::nudgeActive,
                        onToggle = vm::toggleHandle,
                        onSetHandle = vm::setHandle,
                        onShift = vm::shiftSelection,
                        focusRequester = waveFocus,
                        onFocus = { waveFocused = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Stat("Start", formatMs(e.startMs, true), e.active == Handle.Start) { vm.setHandle(Handle.Start, e.startMs) }
                        Stat("End", formatMs(e.endMs, true), e.active == Handle.End) { vm.setHandle(Handle.End, e.endMs) }
                        Stat("Length", "%.1fs".format(lenMs / 1000f), false)
                        Stat("Size", "≈ " + formatBytes(estBytes), false)
                        Spacer(Modifier.weight(1f))
                        PillButton(
                            if (trimPlaying) "Stop" else "Preview", null,
                            onClick = vm::toggleTrimPreview, primary = false,
                            icon = if (trimPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        )
                        Spacer(Modifier.width(14.dp))
                        PillButton("Save", null, onClick = { vm.save(ctx) }, icon = Icons.Rounded.Save)
                    }
                }
            }
            HintBar(
                buildList {
                    if (waveFocused) {
                        add("◀▶" to "Move marker")
                        add("A" to "Switch marker")
                    } else add("A" to "Select")
                    add("L1 R1" to "Slide")
                    add("L2 R2" to "Jump 10s")
                    add("Y" to if (trimPlaying) "Stop" else "Preview")
                    add("☰" to "Save")
                },
            )
        }

        if (vm.encoding) {
            Scrim { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = CC.Accent)
                Spacer(Modifier.height(12.dp))
                Text("Encoding…", color = CC.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            } }
        }
        if (saved != null) SavedDialog(vm)
    }
}

@Composable
private fun Stat(label: String, value: String, active: Boolean, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.padding(end = 14.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(label, color = if (active) CC.Accent else CC.Dim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(value, color = CC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC111111)).clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.focusProperties { onExit = { cancelFocusChange() } }.focusGroup()) { content() }
    }
}

@Composable
private fun SavedDialog(vm: MainViewModel) {
    val s = vm.saved ?: return
    val first = remember { FocusRequester() }
    val playing = vm.playingKey == s.file.absolutePath
    BackHandler { vm.saved = null }
    LaunchedEffect(Unit) { first.requestSoon() }
    Scrim {
        Column(
            Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(28.dp)).background(CC.Card).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = CC.Accent, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(8.dp))
            Text("Jingle saved", color = CC.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                s.file.name,
                color = CC.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatBytes(s.bytes)} · %.1f s · Ogg Opus\n${s.file.parent}".format(s.seconds),
                color = CC.Sub, fontSize = 12.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "In Cocoon: edit the game → Jingle → pick this file.",
                color = CC.Dim, fontSize = 11.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(
                    if (playing) "Stop" else "Play", "Y", onClick = vm::playSaved, primary = false,
                    icon = if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, focusRequester = first,
                )
                PillButton("Tweak", null, onClick = { vm.stopPlayback(); vm.saved = null }, primary = false, icon = Icons.Rounded.Replay)
                PillButton("New jingle", null, onClick = { vm.closeEditor() }, icon = Icons.Rounded.Search)
            }
        }
    }
}

@Composable
fun WorkingScreen(vm: MainViewModel, w: Screen.Working) {
    BackHandler { vm.cancelWork() }
    Column(Modifier.fillMaxSize()) {
        Header("Getting the track", onBack = { vm.cancelWork() })
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(28.dp)).background(CC.Card).padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = CC.Accent, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text(w.title, color = CC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                val p = w.progress
                if (p == null) {
                    LinearProgressIndicator(color = CC.Accent, trackColor = CC.Glyph, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)))
                } else {
                    LinearProgressIndicator(
                        progress = { p }, color = CC.Accent, trackColor = CC.Glyph, drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    w.stage + (p?.let { "  ${(it * 100).toInt()}%" } ?: ""),
                    color = CC.Sub, fontSize = 13.sp,
                )
            }
        }
        HintBar(listOf("B" to "Cancel"))
    }
}
