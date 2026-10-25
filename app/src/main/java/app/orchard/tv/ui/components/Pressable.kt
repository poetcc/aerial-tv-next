package app.orchard.tv.ui.components

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion

fun KeyEvent.isSelect(): Boolean = when (nativeKeyEvent.keyCode) {
    NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_ENTER, NativeKeyEvent.KEYCODE_NUMPAD_ENTER,
    NativeKeyEvent.KEYCODE_BUTTON_A, NativeKeyEvent.KEYCODE_BUTTON_SELECT -> true
    else -> false
}

class PressState {
    var focused by mutableStateOf(false)
    var pressed by mutableStateOf(false)
}

/**
 * A focusable control for the remote. It fires on key up, and only for a press that began on it:
 * when a long press opens a menu, the key-up that ends that press must not also choose the menu's
 * first item.
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    focusedScale: Float = 1.06f,
    onKey: ((KeyEvent) -> Boolean)? = null,
    content: @Composable BoxScope.(PressState) -> Unit,
) {
    val state = remember { PressState() }
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = when {
            reduced -> 1f
            state.pressed -> focusedScale - 0.05f
            state.focused -> focusedScale
            else -> 1f
        },
        animationSpec = if (state.pressed) tween(100, easing = Motion.EaseOut) else spring(dampingRatio = 0.7f, stiffness = 520f),
        label = "pressable-scale",
    )
    Box(
        modifier
            .zIndex(if (state.focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                state.focused = it.isFocused
                if (!it.isFocused) state.pressed = false
            }
            .onKeyEvent { e ->
                if (onKey?.invoke(e) == true) return@onKeyEvent true
                if (!e.isSelect()) return@onKeyEvent false
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (e.nativeKeyEvent.repeatCount == 0) state.pressed = true
                        true
                    }
                    KeyEventType.KeyUp -> {
                        val was = state.pressed
                        state.pressed = false
                        if (was) onClick()
                        true
                    }
                    else -> false
                }
            }
            .focusable(enabled),
    ) { content(state) }
}
