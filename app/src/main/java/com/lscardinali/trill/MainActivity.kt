package com.lscardinali.trill

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lscardinali.trill.ui.EditorScreen
import com.lscardinali.trill.ui.HomeScreen
import com.lscardinali.trill.ui.Pad
import com.lscardinali.trill.ui.WorkingScreen
import com.lscardinali.trill.ui.theme.CC
import com.lscardinali.trill.ui.theme.TrillTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    /** Whether any Compose element currently holds focus. */
    private var composeHasFocus = false
    private var focusManager: FocusManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrillTheme {
                val fm = LocalFocusManager.current
                SideEffect { focusManager = fm }
                Box(
                    Modifier
                        .fillMaxSize()
                        .onFocusChanged { composeHasFocus = it.hasFocus }
                        .background(Brush.verticalGradient(listOf(CC.BgTop, CC.Bg, CC.Bg)))
                        .safeDrawingPadding(),
                ) {
                    AnimatedContent(
                        targetState = vm.screen,
                        contentKey = { it::class },
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { s ->
                        when (s) {
                            Screen.Home -> HomeScreen(vm)
                            is Screen.Working -> WorkingScreen(vm, vm.screen as? Screen.Working ?: s)
                            Screen.Editor -> EditorScreen(vm)
                        }
                    }

                    val toast = vm.toast
                    LaunchedEffect(toast) { if (toast != null) { delay(3500); vm.toast = null } }
                    AnimatedVisibility(
                        visible = toast != null,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                    ) {
                        Text(
                            toast.orEmpty(),
                            color = CC.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(CC.CardHi).padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermission()
        // Keep the Compose host focused in touch mode, otherwise the first controller press after
        // touching the screen resets Compose focus to the first item.
        findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)?.apply {
            isFocusableInTouchMode = true
            if (!hasFocus()) requestFocus()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vm.refreshPermission()
    }

    override fun onStop() {
        super.onStop()
        vm.stopPlayback()
    }

    /**
     * Controller plumbing: A acts as "select", B as "back", everything else on the pad is a
     * screen shortcut. D-pad / left stick keep driving normal focus navigation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        // Nothing focused yet (fresh launch, or after touching the screen): the first
        // D-pad / A press just lands focus on the first item instead of being lost.
        if (!composeHasFocus && code in navKeys) {
            if (event.action == KeyEvent.ACTION_DOWN && focusManager?.moveFocus(FocusDirection.Next) == true) return true
            if (event.action == KeyEvent.ACTION_UP) return true
        }
        if (code in Pad.shortcutKeys) {
            if (event.action == KeyEvent.ACTION_DOWN) Pad.handler?.invoke(code, event.repeatCount)
            return true
        }
        val mapped = when (code) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> null
        }
        if (mapped != null) {
            return super.dispatchKeyEvent(
                KeyEvent(
                    event.downTime, event.eventTime, event.action, mapped, event.repeatCount,
                    event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
                ),
            )
        }
        return super.dispatchKeyEvent(event)
    }

    private val navKeys = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A,
    )
}
