package com.lscardinali.trill.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lscardinali.trill.EditorState
import com.lscardinali.trill.Handle
import com.lscardinali.trill.ui.theme.CC
import kotlin.math.abs
import kotlin.math.max

/** Keeps the zoomed window stable while handles move, re-centring only when needed. */
private class ZoomWindow {
    var start = -1L
    var len = 0L
    /**
     * @param frozen true while a finger drag is in progress: moving the window under the finger
     * would remap its position to a new time and make the selection run away down the song.
     */
    fun update(s: Long, e: Long, dur: Long, frozen: Boolean): Pair<Long, Long> {
        if (frozen && start >= 0) return start to len
        val sel = e - s
        val want = max(20_000L, sel * 5 / 2).coerceAtMost(dur)
        val margin = sel / 8 + 500
        if (start < 0 || want > len || want < len * 2 / 5 || s < start + margin || e > start + len - margin) {
            len = want
            start = ((s + e) / 2 - len / 2).coerceIn(0, (dur - len).coerceAtLeast(0))
        }
        return start to len
    }
}

@Composable
fun DetailWave(
    e: EditorState,
    playheadMs: Long?,
    onNudge: (Long) -> Unit,
    onToggle: () -> Unit,
    onSetHandle: (Handle, Long) -> Unit,
    onShift: (Long) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    val zoom = remember { ZoomWindow() }
    var dragging by remember { mutableStateOf(false) }
    val (ws, wl) = zoom.update(e.startMs, e.endMs, e.wave.durationMs, frozen = dragging)
    val state by rememberUpdatedState(e)
    val win by rememberUpdatedState(ws to wl)
    val selBrush = remember { Brush.horizontalGradient(listOf(CC.Mint, CC.Green)) }

    CocoonCard(
        onClick = onToggle,
        modifier = modifier,
        focusRequester = focusRequester,
        onFocus = onFocus,
        color = CC.Card,
        focusedColor = CC.Card,
        onKey = { ev ->
            if (ev.type != KeyEventType.KeyDown) return@CocoonCard false
            val r = ev.nativeKeyEvent.repeatCount
            val step = when { r > 20 -> 1000L; r > 6 -> 250L; else -> 50L }
            when (ev.key) {
                Key.DirectionLeft -> { onNudge(-step); true }
                Key.DirectionRight -> { onNudge(step); true }
                else -> false
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { p ->
                            val (s0, l0) = win
                            val ms = s0 + (p.x / size.width * l0).toLong()
                            val st = state
                            val h = if (abs(ms - st.startMs) <= abs(ms - st.endMs)) Handle.Start else Handle.End
                            onSetHandle(h, ms)
                        }
                    }
                    .pointerInput(Unit) {
                        var mode = 0 // 1 start, 2 end, 3 move
                        var anchor = 0L
                        detectDragGestures(
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                            onDragStart = { p ->
                                dragging = true
                                val (s0, l0) = win
                                val st = state
                                val px = { ms: Long -> (ms - s0).toFloat() / l0 * size.width }
                                val tol = 28.dp.toPx()
                                anchor = s0 + (p.x / size.width * l0).toLong()
                                mode = when {
                                    abs(p.x - px(st.startMs)) < tol -> 1
                                    abs(p.x - px(st.endMs)) < tol -> 2
                                    p.x > px(st.startMs) && p.x < px(st.endMs) -> 3
                                    p.x < px(st.startMs) -> 1
                                    else -> 2
                                }
                            },
                            onDrag = { change, _ ->
                                val (s0, l0) = win
                                // Window is frozen during the drag; keep the finger's time inside it.
                                val x = change.position.x.coerceIn(0f, size.width.toFloat())
                                val ms = s0 + (x / size.width * l0).toLong()
                                when (mode) {
                                    1 -> onSetHandle(Handle.Start, ms)
                                    2 -> onSetHandle(Handle.End, ms)
                                    3 -> { onShift(ms - anchor); anchor = ms }
                                }
                            },
                        )
                    },
            ) {
                val w = size.width
                val h = size.height
                val mid = h / 2
                val x = { ms: Long -> ((ms - ws).toFloat() / wl * w) }
                val sx = x(e.startMs)
                val ex = x(e.endMs)

                drawRoundRect(
                    CC.Accent.copy(alpha = 0.10f), Offset(sx, 0f), Size(ex - sx, h), CornerRadius(8.dp.toPx()),
                )

                val bar = 2.5.dp.toPx()
                val gap = 1.5.dp.toPx()
                val n = (w / (bar + gap)).toInt().coerceAtLeast(1)
                val msPerBar = wl.toDouble() / n
                for (i in 0 until n) {
                    val t0 = ws + i * msPerBar
                    val peak = e.wave.peakBetween(t0, t0 + msPerBar)
                    val bh = max(2.dp.toPx(), peak * (h - 18.dp.toPx()))
                    val bx = i * (bar + gap)
                    val inside = bx + bar / 2 in sx..ex
                    val tl = Offset(bx, mid - bh / 2)
                    val sz = Size(bar, bh)
                    val cr = CornerRadius(bar / 2)
                    if (inside) drawRoundRect(selBrush, tl, sz, cr) else drawRoundRect(CC.Dim.copy(alpha = 0.55f), tl, sz, cr)
                }

                // Handles
                listOf(Handle.Start to sx, Handle.End to ex).forEach { (hd, hx) ->
                    val active = e.active == hd
                    val c = if (active) Color.White else CC.Sub.copy(alpha = 0.7f)
                    drawLine(c, Offset(hx, 0f), Offset(hx, h), strokeWidth = (if (active) 3 else 2).dp.toPx())
                    val r = (if (active) 8 else 6).dp.toPx()
                    drawCircle(c, r, Offset(hx, r))
                    drawCircle(c, r, Offset(hx, h - r))
                    if (active) {
                        drawCircle(CC.Accent, r + 3.dp.toPx(), Offset(hx, r), style = Stroke(2.dp.toPx()))
                        drawCircle(CC.Accent, r + 3.dp.toPx(), Offset(hx, h - r), style = Stroke(2.dp.toPx()))
                    }
                }

                if (playheadMs != null && playheadMs in ws..(ws + wl)) {
                    val px = x(playheadMs)
                    drawLine(Color.White, Offset(px, 0f), Offset(px, h), strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}

/** Whole-track overview. Tap or drag to jump the selection there. */
@Composable
fun OverviewWave(e: EditorState, onMoveTo: (Long) -> Unit, modifier: Modifier = Modifier) {
    val dur = e.wave.durationMs.coerceAtLeast(1)
    val cb by rememberUpdatedState(onMoveTo)
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures { p -> cb((p.x / size.width * dur).toLong()) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> cb((c.position.x / size.width * dur).toLong()) } },
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(CC.Card, cornerRadius = CornerRadius(10.dp.toPx()))
        val bar = 2.dp.toPx()
        val gap = 1.dp.toPx()
        val n = (w / (bar + gap)).toInt().coerceAtLeast(1)
        val msPerBar = dur.toDouble() / n
        val sx = e.startMs.toFloat() / dur * w
        val ex = e.endMs.toFloat() / dur * w
        for (i in 0 until n) {
            val t0 = i * msPerBar
            val peak = e.wave.peakBetween(t0, t0 + msPerBar)
            val bh = max(1.5.dp.toPx(), peak * (h - 8.dp.toPx()))
            val bx = i * (bar + gap)
            val inside = bx + bar >= sx && bx <= ex
            drawRect(if (inside) CC.Accent else CC.Dim.copy(alpha = 0.5f), Offset(bx, h / 2 - bh / 2), Size(bar, bh))
        }
        val selW = max(ex - sx, 3.dp.toPx())
        drawRoundRect(CC.Accent, Offset(sx, 1.dp.toPx()), Size(selW, h - 2.dp.toPx()), CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
    }
}
