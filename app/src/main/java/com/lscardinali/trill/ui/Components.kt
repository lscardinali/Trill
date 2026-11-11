package com.lscardinali.trill.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lscardinali.trill.ui.theme.CC
import com.lscardinali.trill.ui.theme.Poppins
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

/**
 * Cocoon's selection cursor: a gradient ring floating just outside the element, with a soft glow.
 */
fun Modifier.focusRing(alpha: Float, pulse: Float, shape: Shape, gap: Dp = 5.dp): Modifier =
    if (alpha <= 0f) this else drawWithContent {
        drawContent()
        val g = gap.toPx()
        val stroke = 3.5.dp.toPx()
        // glow
        for (i in 4 downTo 1) {
            val inflate = g + stroke / 2 + i * 2.5.dp.toPx()
            val o = shape.createOutline(Size(size.width + inflate * 2, size.height + inflate * 2), layoutDirection, this)
            translate(-inflate, -inflate) {
                drawOutline(o, CC.AccentBrush, alpha = alpha * pulse * 0.10f * (5 - i) / 4f, style = Stroke(3.dp.toPx()))
            }
        }
        val inflate = g + stroke / 2
        val o = shape.createOutline(Size(size.width + inflate * 2, size.height + inflate * 2), layoutDirection, this)
        translate(-inflate, -inflate) { drawOutline(o, CC.AccentBrush, alpha = alpha, style = Stroke(stroke)) }
    }

@Composable
fun rememberPulse(): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    return v
}

/** A focusable, clickable surface that looks and behaves like Cocoon's rows/cards. */
@Composable
fun CocoonCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = CC.Card,
    focusedColor: Color = CC.CardHi,
    focusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val a by animateFloatAsState(if (focused) 1f else 0f, tween(140), label = "ring")
    val pulse = rememberPulse()
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused; onFocus?.invoke(it.isFocused) }
            .then(if (onKey != null) Modifier.onKeyEvent(onKey) else Modifier)
            .focusRing(a, pulse, shape)
            .clip(shape)
            .background(if (focused) focusedColor else color)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) { content(focused) }
}

/** Round button glyph like Cocoon's "B" badge. */
@Composable
fun Glyph(rawLabel: String, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val label = ButtonLabels.display(rawLabel)
    Box(
        modifier
            .height(size)
            .then(if (label.length > 1) Modifier.padding(horizontal = 0.dp) else Modifier.width(size))
            .clip(RoundedCornerShape(50))
            .background(CC.Glyph)
            .padding(horizontal = if (label.length > 1) 7.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        val fs = (size.value * 0.5f).sp
        Text(
            label,
            color = Color(0xFFD6D6D6),
            maxLines = 1,
            style = TextStyle(
                fontFamily = Poppins,
                fontSize = fs,
                fontWeight = FontWeight.Bold,
                lineHeight = fs,
                // Poppins' tall ascent/descent pushes caps low; trim it so the letter sits centered.
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
    }
}

/** The Trill pod icon (adaptive layers drawn as a rounded square, like Cocoon's pods). */
@Composable
fun AppIcon(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(24))) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.lscardinali.trill.R.drawable.ic_launcher_background), null,
            Modifier.matchParentSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
        )
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.lscardinali.trill.R.drawable.ic_launcher_foreground), null,
            Modifier.matchParentSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
        )
    }
}

@Composable
fun Header(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showIcon: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showIcon) {
            AppIcon(Modifier.size(38.dp))
            Spacer(Modifier.width(12.dp))
        }
        if (onBack != null) {
            Row(
                Modifier
                    .focusProperties { canFocus = false }
                    .clip(RoundedCornerShape(50))
                    .background(CC.Card)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("B")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = CC.Text, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(title, color = CC.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Left-pane navigation row (icon + accent title + subtitle), as in Cocoon's settings. */
@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    CocoonCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = if (selected) CC.Card else Color.Transparent,
        focusRequester = focusRequester,
        onFocus = onFocus,
    ) { focused ->
        val hl = focused || selected
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (hl) CC.Accent else CC.Text, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = if (hl) CC.Accent else CC.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (hl) Text(subtitle, color = CC.Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Settings-style row with icon, title/subtitle and a trailing slot. */
@Composable
fun OptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onKey: ((KeyEvent) -> Boolean)? = null,
    focusRequester: FocusRequester? = null,
    trailing: @Composable () -> Unit = {},
) {
    CocoonCard(onClick = onClick, modifier = modifier.fillMaxWidth(), onKey = onKey, focusRequester = focusRequester) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = CC.Text, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = CC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (subtitle != null) Text(subtitle, color = CC.Sub, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun Toggle(on: Boolean) {
    val x by animateFloatAsState(if (on) 1f else 0f, tween(160), label = "t")
    Box(
        Modifier
            .size(46.dp, 26.dp)
            .clip(RoundedCornerShape(50))
            .then(if (on) Modifier.background(CC.Accent) else Modifier.background(CC.Glyph)),
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .padding(start = (20 * x).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (on) CC.OnAccent else CC.Sub),
        )
    }
}

/** Gradient pill button (primary action). */
@Composable
fun PillButton(
    text: String,
    glyph: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    CocoonCard(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = if (primary) Color.Transparent else CC.Card,
        focusedColor = if (primary) Color.Transparent else CC.CardHi,
        focusRequester = focusRequester,
        onFocus = onFocus,
    ) {
        Row(
            Modifier
                .then(if (primary) Modifier.background(CC.Accent) else Modifier)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = if (primary) CC.OnAccent else CC.Text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, color = if (primary) CC.OnAccent else CC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (glyph != null) {
                Spacer(Modifier.width(8.dp))
                Glyph(glyph, size = 20.dp)
            }
        }
    }
}

/** Bottom hint strip showing controller shortcuts. */
@Composable
fun HintBar(hints: List<Pair<String, String>>, modifier: Modifier = Modifier, leading: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { leading() }
        hints.forEach { (g, label) ->
            Spacer(Modifier.width(18.dp))
            // "L1 R1" → two separate badges with a small gap.
            g.split(' ').forEachIndexed { i, part ->
                if (i > 0) Spacer(Modifier.width(4.dp))
                Glyph(part, size = 20.dp)
            }
            Spacer(Modifier.width(6.dp))
            Text(label, color = CC.Sub, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

fun formatMs(ms: Long, tenths: Boolean = false): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return if (tenths) "%d:%02d.%d".format(m, s, (ms % 1000) / 100) else "%d:%02d".format(m, s)
}

fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024f)
    else -> "%.2f MB".format(b / 1024f / 1024f)
}
