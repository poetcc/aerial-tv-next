package app.orchard.tv.ui.home

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.FocusStyle
import app.orchard.tv.ui.components.isSelect
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// D-pad remotes give no tilt to follow, so the lift itself has to read from across the room.
private const val FOCUSED_SCALE = 1.22f
private const val PRESSED_SCALE = 1.10f
private val Corner = 12.dp

/**
 * One app on the home screen, drawn the tvOS way: it lifts toward you when focused, a soft light
 * lies across its top edge, its name appears beneath it, and it leans and springs back when you
 * push against the edge of the grid. Adaptive icons keep their two layers, so the logo shifts
 * against its background when the tile leans.
 */
@Composable
fun AppTile(
    app: AppEntry,
    width: Dp,
    height: Dp,
    focusRequester: FocusRequester,
    enabled: Boolean,
    moving: Boolean,
    columns: Int,
    appearDelayMs: Int,
    onFocused: () -> Unit,
    onClick: (Rect?) -> Unit,
    onLongPress: (Rect?) -> Unit,
    onMove: (Int) -> Unit,
    onMoveDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longFired by remember { mutableStateOf(false) }

    // Lean (degrees) and push (px) used by the edge nudge.
    val leanY = remember { Animatable(0f) }
    val leanX = remember { Animatable(0f) }
    val push = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    val scale by animateFloatAsState(
        targetValue = when {
            reduced -> 1f
            pressed -> PRESSED_SCALE
            focused || moving -> if (AerialSettings.focusStyle == FocusStyle.HighContrast) FOCUSED_SCALE else 1.18f
            else -> 1f
        },
        // Press answers immediately; focus settles on a spring so rapid D-pad presses retarget smoothly.
        animationSpec = if (pressed) tween(110, easing = Motion.EaseOut) else spring(dampingRatio = 0.66f, stiffness = 460f),
        label = "tile-scale",
    )
    val lift by animateFloatAsState(
        targetValue = if (focused || moving) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
        label = "tile-lift",
    )
    // tvOS "jiggle" while the app is being moved.
    val wobble = if (moving && !reduced) {
        rememberInfiniteTransition(label = "jiggle").animateFloat(
            initialValue = -1.3f, targetValue = 1.3f,
            animationSpec = infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse),
            label = "jiggle-angle",
        )
    } else null

    // First appearance: a short stagger, so the grid settles in rather than popping on.
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            delay(appearDelayMs.toLong())
            appear.animateTo(1f, tween(420, easing = Motion.EaseOut))
        }
    }

    fun nudge(dx: Float, dy: Float) {
        if (reduced) return
        scope.launch {
            push.animateTo(Offset(dx * 14f, dy * 14f), tween(90, easing = Motion.EaseOut))
            push.animateTo(Offset.Zero, spring(dampingRatio = 0.45f, stiffness = 520f))
        }
        scope.launch {
            leanY.animateTo(dx * 7f, tween(90, easing = Motion.EaseOut))
            leanY.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 420f))
        }
        scope.launch {
            leanX.animateTo(-dy * 7f, tween(90, easing = Motion.EaseOut))
            leanX.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 420f))
        }
    }

    Box(
        modifier
            .zIndex(if (focused || moving) 1f else 0f)
            .size(width, height)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 18.dp.toPx()
            },
    ) {
        Box(
            Modifier
                .size(width, height)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationY = leanY.value
                    rotationX = leanX.value
                    rotationZ = wobble?.value ?: 0f
                    translationX = push.value.x
                    translationY = push.value.y
                    cameraDistance = 14f * density
                    shadowElevation = 32.dp.toPx() * lift
                    shape = RoundedCornerShape(Corner)
                    clip = true
                }
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused() else {
                        pressed = false
                        longPressJob?.cancel()
                    }
                }
                .onKeyEvent { e ->
                    val code = e.nativeKeyEvent.keyCode
                    val down = e.type == KeyEventType.KeyDown
                    if (moving) {
                        if (!down) return@onKeyEvent code != NativeKeyEvent.KEYCODE_HOME
                        when (code) {
                            NativeKeyEvent.KEYCODE_DPAD_LEFT -> onMove(-1)
                            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> onMove(1)
                            NativeKeyEvent.KEYCODE_DPAD_UP -> onMove(-columns)
                            NativeKeyEvent.KEYCODE_DPAD_DOWN -> onMove(columns)
                            NativeKeyEvent.KEYCODE_BACK, NativeKeyEvent.KEYCODE_DPAD_CENTER,
                            NativeKeyEvent.KEYCODE_ENTER, NativeKeyEvent.KEYCODE_NUMPAD_ENTER -> onMoveDone()
                            else -> return@onKeyEvent false
                        }
                        return@onKeyEvent true
                    }
                    val dir = when (code) {
                        NativeKeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Left
                        NativeKeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
                        NativeKeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
                        NativeKeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
                        else -> null
                    }
                    if (dir != null) {
                        if (down && !focusManager.moveFocus(dir)) {
                            when (dir) {
                                FocusDirection.Left -> nudge(-1f, 0f)
                                FocusDirection.Right -> nudge(1f, 0f)
                                FocusDirection.Up -> nudge(0f, -1f)
                                else -> nudge(0f, 1f)
                            }
                        }
                        return@onKeyEvent true
                    }
                    if (!e.isSelect()) return@onKeyEvent false
                    if (down) {
                        if (e.nativeKeyEvent.repeatCount > 0 && pressed && !longFired) {
                            // Remotes that auto-repeat a held key report the long press themselves.
                            longPressJob?.cancel()
                            longFired = true
                            pressed = false
                            onLongPress(bounds)
                        } else if (e.nativeKeyEvent.repeatCount == 0) {
                            pressed = true
                            longFired = false
                            longPressJob?.cancel()
                            longPressJob = scope.launch {
                                delay(550)
                                if (pressed) {
                                    longFired = true
                                    pressed = false
                                    onLongPress(bounds)
                                }
                            }
                        }
                    } else if (e.type == KeyEventType.KeyUp) {
                        longPressJob?.cancel()
                        if (pressed && !longFired) onClick(bounds)
                        pressed = false
                    }
                    true
                }
                .focusable(enabled),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val art = app.art
                drawImage(
                    art.back,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
                art.fore?.let { fore ->
                    val s = size.height * art.foreFraction
                    // Parallax: the logo travels further than the plate when the tile leans.
                    val px = leanY.value / 7f * size.width * 0.035f
                    val py = -leanX.value / 7f * size.height * 0.035f
                    drawImage(
                        fore,
                        dstOffset = IntOffset(((size.width - s) / 2 + px).roundToInt(), ((size.height - s) / 2 + py).roundToInt()),
                        dstSize = IntSize(s.roundToInt(), s.roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                if (lift > 0.01f) {
                    // Light from above: a soft sheen across the top, brighter where the tile leans toward it.
                    val glintX = size.width * (0.3f + leanY.value / 7f * 0.25f)
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.28f * lift),
                            1f to Color.Transparent,
                            center = Offset(glintX, -size.height * 0.25f),
                            radius = size.width * 0.75f,
                        ),
                        blendMode = BlendMode.Screen,
                    )
                }
                // A hairline on the rim, as on tvOS icons, so dark artwork still reads against black.
                drawRoundRect(
                    Color.White.copy(alpha = 0.07f + 0.08f * lift),
                    cornerRadius = CornerRadius(Corner.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        BasicText(
            text = app.label,
            style = Type.Headline.copy(
                textAlign = TextAlign.Center,
                // Names sit on the wallpaper; a soft shadow keeps them readable over bright photos.
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = height + 10.dp)
                .requiredSize(width + 36.dp, 24.dp)
                .graphicsLayer {
                    alpha = lift
                    // Sits just under the lifted tile, and rises into place as it appears.
                    translationY = height.toPx() * (scale - 1f) / 2f - (1f - lift) * 6.dp.toPx()
                },
        )
    }
}
