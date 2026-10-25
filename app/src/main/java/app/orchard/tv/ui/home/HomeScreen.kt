package app.orchard.tv.ui.home

import android.content.Intent
import android.os.Build
import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orchard.tv.CcPage
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.R
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.media.RecentItem
import app.orchard.tv.ui.control.ControlCenter
import app.orchard.tv.ui.wallpaper.WallpaperSheet
import app.orchard.tv.wallpaper.Wallpapers
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import kotlin.math.roundToInt

private const val COLUMNS = 5

/** Grid geometry for the screen at hand. On a 1080p TV this is a 960×540 dp canvas. */
private class GridMetrics(screenW: Dp, screenH: Dp) {
    val margin = 56.dp
    val gap = 28.dp
    val tileW = (screenW - margin * 2 - gap * (COLUMNS - 1)) / COLUMNS
    val tileH = tileW * 9f / 16f
    val pitchX = tileW + gap
    val pitchY = tileH + 50.dp

    /** The top row sits a little past the middle, under the shelf, as on tvOS. */
    val topRowY = screenH * 0.555f

    /** Where a row rests once you go down into the grid: the row above stays in view. */
    val restY = screenH * 0.37f
    val screenH = screenH

    fun slot(index: Int) = Pair(margin + pitchX * (index % COLUMNS), topRowY + pitchY * (index / COLUMNS))

    fun scrollFor(row: Int, rows: Int): Dp {
        if (row <= 0) return 0.dp
        val wanted = topRowY + pitchY * row - restY
        val bottom = topRowY + pitchY * rows + 36.dp - screenH
        return minOf(wanted, maxOf(bottom, topRowY + pitchY - restY))
    }
}

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onOpen: (Intent) -> Unit,
    onRecent: (RecentItem) -> Unit,
    onMediaPermission: () -> Unit,
    onAppInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onStartScreensaver: () -> Unit,
) {
    val apps = vm.apps
    val overlay = vm.overlay
    val moving = vm.movingKey
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val now by rememberNow()

    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    val pillRequester = remember { FocusRequester() }

    var focusedKey by remember { mutableStateOf<String?>(null) }
    var pillFocused by remember { mutableStateOf(false) }
    var recentFocused by remember { mutableStateOf(false) }
    var lastFocusedIndex by remember { mutableStateOf(0) }
    val focusedIndex = apps.indexOfFirst { it.key == focusedKey }
    val focusedRow = if (pillFocused || recentFocused || focusedIndex < 0) 0 else focusedIndex / COLUMNS
    val playbackOverlay = vm.playbackOverlay
    // A separate dialog owns focus; no control in the underlying Home may become selected.
    val gridEnabled = overlay == Overlay.None && playbackOverlay == PlaybackShelfOverlay.None && !vm.screensaverVisible

    fun focusApp(index: Int) {
        if (!gridEnabled) return
        val app = apps.getOrNull(index.coerceIn(0, (apps.size - 1).coerceAtLeast(0))) ?: return
        runCatching { requester(app.key).requestFocus() }
    }

    // First focus once the apps are in; afterwards, put focus back wherever it was when an overlay closes.
    LaunchedEffect(vm.loaded, overlay) {
        if (!vm.loaded || overlay != Overlay.None) return@LaunchedEffect
        withFrameNanos { }
        val index = apps.indexOfFirst { it.key == focusedKey }
        if (pillFocused && focusedKey != null) runCatching { pillRequester.requestFocus() } else focusApp(if (index >= 0) index else 0)
    }
    // An app vanished from under the focus (uninstalled or hidden): take its neighbour.
    LaunchedEffect(apps) {
        if (focusedKey != null && apps.none { it.key == focusedKey } && overlay == Overlay.None) {
            withFrameNanos { }
            focusApp(lastFocusedIndex)
        }
        // While moving, focus follows the app to its new slot.
        moving?.let { withFrameNanos { }; runCatching { requester(it).requestFocus() } }
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused, vm.homeResetPending, vm.loaded, gridEnabled) {
        if (windowFocused && vm.homeResetPending && vm.loaded && gridEnabled) {
            withFrameNanos { }
            focusApp(0)
            vm.homeResetPending = false
        }
    }

    LaunchedEffect(vm.screensaverVisible) {
        if (vm.screensaverVisible) {
            playbackOverlay.let { if (it != PlaybackShelfOverlay.None) vm.playbackOverlay = PlaybackShelfOverlay.None }
        } else if (vm.loaded && !vm.homeResetPending && playbackOverlay == PlaybackShelfOverlay.None) {
            withFrameNanos { }
            focusApp(lastFocusedIndex)
        }
    }

    BackHandler(enabled = gridEnabled && moving == null) {
        // tvOS: Back from inside the grid returns to the top row; Back on the top row starts the
        // screensaver.
        if (recentFocused) focusApp(0)
        else if (focusedRow > 0) focusApp(focusedIndex % COLUMNS) else onStartScreensaver()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (!gridEnabled || e.type != KeyEventType.KeyDown || moving != null) return@onPreviewKeyEvent false
                when (e.nativeKeyEvent.keyCode) {
                    NativeKeyEvent.KEYCODE_MENU -> {
                        vm.overlay = if (overlay is Overlay.ControlCenter) Overlay.None else Overlay.ControlCenter()
                        true
                    }
                    NativeKeyEvent.KEYCODE_TV_INPUT -> {
                        vm.overlay = Overlay.ControlCenter(CcPage.Inputs)
                        true
                    }
                    else -> false
                }
            },
    ) {
        val metrics = remember(maxWidth, maxHeight) { GridMetrics(maxWidth, maxHeight) }
        val recentShelfWidth = (maxWidth * 0.52f - metrics.margin - 104.dp) * 0.75f
        val recentShelfX = maxWidth - metrics.margin - recentShelfWidth
        val rows = (apps.size + COLUMNS - 1) / COLUMNS
        val collapsePx = with(density) { (metrics.topRowY - metrics.restY).toPx() }

        val scroll = remember { Animatable(0f) }
        val target = with(density) { metrics.scrollFor(focusedRow, rows).toPx() }
        LaunchedEffect(target) {
            if (reduced) scroll.snapTo(target)
            // A spring keeps its velocity when a quick second press retargets it mid-flight.
            else scroll.animateTo(target, spring(dampingRatio = 1f, stiffness = 170f))
        }

        val backdropBlur by animateDpAsState(
            if (overlay is Overlay.ControlCenter || overlay is Overlay.HiddenApps) 30.dp else 0.dp,
            tween(320, easing = Motion.EaseOut), label = "backdrop-blur",
        )

        val ambientApp = apps.getOrNull(focusedIndex) ?: apps.firstOrNull()

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val r = backdropBlur.toPx()
                    renderEffect = if (r > 0.5f && Build.VERSION.SDK_INT >= 31) BlurEffect(r, r, TileMode.Clamp) else null
                },
        ) {
            AmbientBackground(
                // The chosen wallpaper; in "Follow App" mode, light from the focused app.
                backdrop = Wallpapers.backdrop ?: ambientApp?.art?.backdrop,
                dim = { (scroll.value / collapsePx).coerceIn(0f, 1f) * 0.5f },
            )

            Box(Modifier.fillMaxSize().offset { IntOffset(0, -scroll.value.roundToInt()) }) {
                ShelfClock(
                    now = now,
                    modifier = Modifier
                        .padding(start = metrics.margin, top = metrics.topRowY - 196.dp)
                        .graphicsLayer { alpha = 1f - (scroll.value / collapsePx * 1.6f).coerceIn(0f, 1f) },
                )

                RecentPlaybackShelf(
                    enabled = gridEnabled && moving == null && focusedRow == 0,
                    overlay = playbackOverlay,
                    onOverlayChange = { vm.playbackOverlay = it },
                    onOpen = onRecent,
                    onPermission = onMediaPermission,
                    onFocused = { pillFocused = false; recentFocused = true },
                    onDown = { focusApp(3) },
                    modifier = Modifier
                        .offset(x = recentShelfX, y = metrics.topRowY - 190.dp)
                        .width(recentShelfWidth)
                        .graphicsLayer { alpha = 1f - (scroll.value / collapsePx * 1.6f).coerceIn(0f, 1f) },
                )

                apps.forEachIndexed { index, app ->
                    key(app.key) {
                        val (x, y) = metrics.slot(index)
                        val slotPx = with(density) { IntOffset(x.roundToPx(), y.roundToPx()) }
                        val slot = animateIntOffsetAsState(
                            slotPx,
                            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                            label = "slot",
                        )
                        AppTile(
                            app = app,
                            width = metrics.tileW,
                            height = metrics.tileH,
                            focusRequester = requester(app.key),
                            enabled = gridEnabled,
                            moving = moving == app.key,
                            columns = COLUMNS,
                            appearDelayMs = (index * 28).coerceAtMost(320),
                            onFocused = {
                                focusedKey = app.key
                                lastFocusedIndex = index
                                pillFocused = false
                                recentFocused = false
                            },
                            onClick = { bounds -> onLaunch(app, bounds) },
                            onLongPress = { bounds ->
                                vm.overlay = Overlay.AppMenu(app.key, bounds ?: Rect.Zero)
                            },
                            onMove = vm::moveBy,
                            onMoveDone = vm::endMove,
                            modifier = Modifier.offset { slot.value },
                        )
                    }
                }
            }
        }

        // Rows that scroll up under the status pill fade out instead of colliding with it.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .graphicsLayer { alpha = (scroll.value / collapsePx).coerceIn(0f, 1f) },
        ) {
            drawRect(Brush.verticalGradient(0f to Color.Black, 0.5f to Color.Black.copy(alpha = 0.82f), 1f to Color.Transparent))
        }

        StatusPill(
            now = now,
            network = vm.network,
            showTime = focusedRow > 0,
            enabled = gridEnabled && moving == null,
            focusRequester = pillRequester,
            onFocused = { gained ->
                // The window hands its first focus to the top-most control; the top row is where Home starts.
                if (gained) {
                    recentFocused = false
                    if (focusedKey == null) {
                        if (apps.isNotEmpty()) focusApp(0)
                    } else {
                        pillFocused = true
                    }
                }
            },
            onClick = { vm.overlay = Overlay.ControlCenter() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = metrics.margin - 14.dp)
                .graphicsLayer { alpha = if (overlay is Overlay.ControlCenter || vm.screensaverVisible) 0f else 1f },
        )

        AnimatedVisibility(
            visible = moving != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260, easing = Motion.EaseOut)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150, easing = Motion.EaseOut)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
        ) {
            BasicText(
                stringResource(R.string.move_hint),
                style = Type.Body,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Palette.Glass)
                    .height(40.dp)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        ControlCenter(
            visible = overlay is Overlay.ControlCenter,
            page = (overlay as? Overlay.ControlCenter)?.page ?: CcPage.Main,
            vm = vm,
            now = now,
            onOpen = { vm.overlay = Overlay.None; onOpen(it) },
            onClose = { vm.overlay = Overlay.None },
        )

        val menu = overlay as? Overlay.AppMenu
        AppMenu(
            app = menu?.let { m -> apps.firstOrNull { it.key == m.key } },
            anchor = menu?.anchor,
            onMove = { vm.startMove(it) },
            onHide = { key ->
                vm.overlay = Overlay.None
                vm.hide(key)
            },
            onInfo = { vm.overlay = Overlay.None; onAppInfo(it) },
            onUninstall = { vm.overlay = Overlay.None; onUninstall(it) },
            onDismiss = { vm.overlay = Overlay.None },
        )

        HiddenAppsSheet(
            visible = overlay is Overlay.HiddenApps,
            apps = vm.hiddenApps,
            onUnhide = { key ->
                vm.unhide(key)
                if (vm.hiddenApps.isEmpty()) vm.overlay = Overlay.None
            },
            onDismiss = { vm.overlay = Overlay.ControlCenter() },
        )

        WallpaperSheet(
            visible = overlay is Overlay.Wallpaper,
            onDismiss = { vm.overlay = Overlay.ControlCenter() },
            onStartScreensaver = {
                vm.overlay = Overlay.None
                onStartScreensaver()
            },
        )

        Screensaver(visible = vm.screensaverVisible, apps = apps, now = now, skips = vm.screensaverSkips)
    }
}
