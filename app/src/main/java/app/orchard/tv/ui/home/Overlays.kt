package app.orchard.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import kotlin.math.roundToInt

/** Keeps the last non-null value, so an overlay still has its content while it animates out. */
@Composable
fun <T : Any> rememberLatest(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}

/** A row in a glass menu: transparent at rest, the tvOS white plate when focused. */
@Composable
fun MenuRow(
    glyph: Glyph,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    destructive: Boolean = false,
    trailing: String? = null,
) {
    Pressable(onClick = onClick, focusRequester = focusRequester, focusedScale = 1.03f, modifier = modifier.fillMaxWidth()) { state ->
        val bg by animateColorAsState(
            if (state.focused) Palette.Focus else Color.Transparent, tween(140, easing = Motion.EaseOut), label = "row-bg",
        )
        val fg = when {
            state.focused -> if (destructive) Color(0xFFD70015) else Palette.OnFocus
            destructive -> Palette.Red
            else -> Palette.Label
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .height(44.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlyphIcon(glyph, fg, 18.dp)
            BasicText(text, style = Type.Body.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (trailing != null) {
                BasicText(trailing, style = Type.Callout.copy(color = if (state.focused) Palette.OnFocus.copy(alpha = 0.6f) else Palette.SecondaryLabel))
            }
        }
    }
}

/** Long-press menu, opening out of the tile it belongs to. */
@Composable
fun AppMenu(
    app: AppEntry?,
    anchor: Rect?,
    onMove: (String) -> Unit,
    onHide: (String) -> Unit,
    onInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = app != null
    val shownApp = rememberLatest(app) ?: return
    val shownAnchor = rememberLatest(anchor) ?: Rect.Zero
    val first = remember { FocusRequester() }
    val density = LocalDensity.current
    val screenH = LocalWindowInfo.current.containerSize.height.toFloat()
    // Open below the tile when there is room for the whole menu, otherwise above it.
    val below = shownAnchor.bottom + with(density) { 250.dp.toPx() } <= screenH

    BackHandler(enabled = visible, onBack = onDismiss)
    LaunchedEffect(visible) {
        if (visible) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val p = measurable.measure(Constraints())
                val gap = 18.dp.roundToPx()
                val y = if (below) shownAnchor.bottom + gap else shownAnchor.top - gap - p.height
                val x = shownAnchor.left.coerceIn(24.dp.toPx(), constraints.maxWidth - p.width - 24.dp.toPx())
                layout(constraints.maxWidth, constraints.maxHeight) { p.place(x.roundToInt(), y.roundToInt()) }
            },
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160)) + scaleIn(tween(220, easing = Motion.EaseOut), initialScale = 0.92f, transformOrigin = TransformOrigin(0.2f, if (below) 0f else 1f)),
            exit = fadeOut(tween(120)) + scaleOut(tween(120, easing = Motion.EaseOut), targetScale = 0.96f, transformOrigin = TransformOrigin(0.2f, if (below) 0f else 1f)),
        ) {
            Column(
                Modifier
                    .width(250.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.Glass)
                    .border(1.dp, Palette.Hairline, RoundedCornerShape(20.dp))
                    .padding(8.dp),
            ) {
                BasicText(
                    shownApp.label, style = Type.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
                )
                MenuRow(Glyph.Move, stringResource(R.string.menu_move), { onMove(shownApp.key) }, focusRequester = first)
                MenuRow(Glyph.EyeSlash, stringResource(R.string.menu_hide), { onHide(shownApp.key) })
                MenuRow(Glyph.Info, stringResource(R.string.menu_info), { onInfo(shownApp.key) })
                if (shownApp.removable) {
                    MenuRow(Glyph.Trash, stringResource(R.string.menu_uninstall), { onUninstall(shownApp.key) }, destructive = true)
                }
            }
        }
    }
}

/** A small, flat copy of a tile for lists. */
@Composable
fun MiniTile(app: AppEntry, focused: Boolean, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Palette.Focus else Palette.Hairline, RoundedCornerShape(10.dp)),
    ) {
        drawImage(app.art.back, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Medium)
        app.art.fore?.let {
            val s = size.height * app.art.foreFraction
            drawImage(
                it,
                dstOffset = IntOffset(((size.width - s) / 2).roundToInt(), ((size.height - s) / 2).roundToInt()),
                dstSize = IntSize(s.roundToInt(), s.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** Hidden apps, to put back on Home. Reached from Control Center. */
@Composable
fun HiddenAppsSheet(
    visible: Boolean,
    apps: List<AppEntry>,
    onUnhide: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    BackHandler(enabled = visible, onBack = onDismiss)
    LaunchedEffect(visible, apps.size) {
        if (visible) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(tween(300, easing = Motion.Drawer), initialScale = 0.94f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150, easing = Motion.EaseOut), targetScale = 0.97f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(660.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Palette.Glass)
                    .border(1.dp, Palette.Hairline, RoundedCornerShape(28.dp))
                    .padding(28.dp),
            ) {
                BasicText(stringResource(R.string.hidden_title), style = Type.LargeTitle)
                BasicText(
                    stringResource(if (apps.isEmpty()) R.string.hidden_empty else R.string.hidden_hint),
                    style = Type.Callout, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                )
                apps.chunked(4).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = 18.dp)) {
                        row.forEachIndexed { c, app ->
                            Pressable(
                                onClick = { onUnhide(app.key) },
                                focusRequester = if (r == 0 && c == 0) first else null,
                                focusedScale = 1.1f,
                            ) { state ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MiniTile(app, state.focused, Modifier.size(132.dp, 74.dp))
                                    Spacer(Modifier.height(8.dp))
                                    BasicText(
                                        app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = Type.Caption.copy(color = if (state.focused) Palette.Label else Palette.SecondaryLabel),
                                        modifier = Modifier.width(132.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
