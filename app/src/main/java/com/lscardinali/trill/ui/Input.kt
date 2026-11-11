package com.lscardinali.trill.ui

import android.view.KeyEvent as AKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lscardinali.trill.ui.theme.CC
import com.lscardinali.trill.ui.theme.Poppins

/**
 * Global gamepad shortcuts (X/Y/L1/R1/L2/R2/Start/Select), routed from the Activity so they
 * work regardless of which element has focus.
 */
/**
 * Legend label style. "Switch style" swaps A↔B and X↔Y so badges match Nintendo-style printed
 * buttons (e.g. the Retroid face buttons). Only the labels change, never the controls.
 */
object ButtonLabels {
    var switchStyle by mutableStateOf(false)

    fun display(label: String): String = if (!switchStyle) label else when (label) {
        "A" -> "B"; "B" -> "A"; "X" -> "Y"; "Y" -> "X"; else -> label
    }
}

object Pad {
    /** Topmost screen/overlay wins; popping restores the one underneath. */
    val stack = ArrayList<(Int, Int) -> Boolean>()
    val handler: ((keyCode: Int, repeat: Int) -> Boolean)? get() = stack.lastOrNull()

    val shortcutKeys = setOf(
        AKeyEvent.KEYCODE_BUTTON_X, AKeyEvent.KEYCODE_BUTTON_Y,
        AKeyEvent.KEYCODE_BUTTON_L1, AKeyEvent.KEYCODE_BUTTON_R1,
        AKeyEvent.KEYCODE_BUTTON_L2, AKeyEvent.KEYCODE_BUTTON_R2,
        AKeyEvent.KEYCODE_BUTTON_START, AKeyEvent.KEYCODE_BUTTON_SELECT,
        AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    )
}

@Composable
fun PadShortcuts(handler: (keyCode: Int, repeat: Int) -> Boolean) {
    val current by rememberUpdatedState(handler)
    DisposableEffect(Unit) {
        val f: (Int, Int) -> Boolean = { k, r -> current(k, r) }
        Pad.stack.add(f)
        onDispose { Pad.stack.remove(f) }
    }
}

suspend fun FocusRequester.requestSoon() {
    withFrameNanos { }
    runCatching { requestFocus() }
}

/**
 * A pill that looks like Cocoon's search box. With a controller it is just a focusable button;
 * pressing A turns it into a text field and opens the keyboard. Down/Up/B leave edit mode.
 */
@Composable
fun EditablePill(
    value: String,
    placeholder: String,
    icon: ImageVector,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    imeAction: ImeAction = ImeAction.Done,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    var editing by remember { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var hadFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    fun finish(submitted: Boolean = false) {
        if (!editing) return
        editing = false
        hadFocus = false
        keyboard?.hide()
        returnFocus = true
        if (submitted || text.text != value) onCommit(text.text)
    }

    if (!editing) {
        CocoonCard(
            onClick = { text = TextFieldValue(value, TextRange(value.length)); editing = true },
            modifier = modifier,
            shape = shape,
            focusRequester = focusRequester,
            onFocus = onFocus,
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value.ifEmpty { placeholder },
                    color = if (value.isEmpty()) CC.Sub else CC.Text,
                    fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(icon, null, tint = CC.Text, modifier = Modifier.size(22.dp))
            }
        }
    } else {
        Box(
            modifier
                .focusRing(1f, 1f, shape)
                .clip(shape)
                .background(CC.CardHi),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = CC.Text, fontSize = 15.sp, fontFamily = Poppins, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(CC.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { finish(true) }, onSearch = { finish(true) }, onGo = { finish(true) }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(fieldFocus)
                        .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) finish() }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown &&
                                (it.key == Key.Back || it.key == Key.DirectionDown || it.key == Key.DirectionUp || it.key == Key.Escape)
                            ) { finish(); true } else false
                        },
                )
                Spacer(Modifier.width(8.dp))
                Icon(icon, null, tint = CC.Accent, modifier = Modifier.size(22.dp))
            }
        }
        LaunchedEffect(Unit) {
            fieldFocus.requestSoon()
            keyboard?.show()
        }
    }

    LaunchedEffect(editing, returnFocus) {
        if (!editing && returnFocus) {
            returnFocus = false
            focusRequester.requestSoon()
        }
    }
}
