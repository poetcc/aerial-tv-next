package app.orchard.tv.ui.home

import android.graphics.BitmapFactory
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orchard.tv.R
import app.orchard.tv.media.RecentItem
import app.orchard.tv.media.RecentPlayback
import app.orchard.tv.media.historyKey
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** All material layers share one shape and bounds, including padded buttons and panels. */
private fun Modifier.playbackGlass(focused: Boolean, panel: Boolean = false, light: Boolean = false, round: Boolean = false): Modifier {
    val shape = if (round) RoundedCornerShape(50) else RoundedCornerShape(if (panel) 22.dp else 9.dp)
    val tint = when {
        panel -> Color(0xFFEAF3F7).copy(alpha = 0.336f)
        light -> Color.White.copy(alpha = if (focused) 0.22f else 0.075f)
        else -> Color(0xFF0C1720).copy(alpha = 0.22f)
    }
    val sheen = Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (focused) 0.12f else 0.065f),
        Color.Transparent, Color(0xFF91C9E6).copy(alpha = 0.025f),
    ))
    val rim = Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (panel) 0.52f else 0.44f),
        Color(0xFFC9EBFF).copy(alpha = 0.14f), Color.Transparent,
        Color.White.copy(alpha = 0.20f),
    ))
    return clip(shape).background(tint, shape).background(sheen, shape).border(0.7.dp, rim, shape)
}

enum class PlaybackShelfOverlay { None, History, Search }

/** Three compact rows on Home; the complete programme history is a separate focusable dialog. */
@Composable
fun RecentPlaybackShelf(
    enabled: Boolean,
    overlay: PlaybackShelfOverlay,
    onOverlayChange: (PlaybackShelfOverlay) -> Unit,
    onOpen: (RecentItem) -> Unit,
    onPermission: () -> Unit,
    onFocused: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val records = RecentPlayback.items.take(3)
    val keys = records.map(::historyKey)
    val requesters = remember(keys) { keys.associateWith { FocusRequester() } }
    val historyRequester = remember { FocusRequester() }
    val searchRequester = remember { FocusRequester() }
    val searchVisible = overlay == PlaybackShelfOverlay.Search
    val historyVisible = overlay == PlaybackShelfOverlay.History
    var restoreFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(overlay, windowFocused) {
        if (overlay == PlaybackShelfOverlay.None && windowFocused) {
            restoreFocus?.let { requester ->
                // Wait until Home's controls are enabled again after the dialog window closes.
                withFrameNanos { }
                runCatching { requester.requestFocus() }
                restoreFocus = null
            }
        }
    }
    var historyBackdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    var preparingHistory by remember { mutableStateOf(false) }
    val homeView = LocalView.current
    val scope = rememberCoroutineScope()
    fun controlModifier() = Modifier.onFocusChanged { if (it.hasFocus) onFocused() }
    fun focusRecord(index: Int) {
        records.getOrNull(index)?.let { runCatching { requesters[historyKey(it)]?.requestFocus() } }
    }
    val headerKey: (KeyEvent) -> Boolean = { event ->
        if (event.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.type == KeyEventType.KeyDown) { if (records.isEmpty()) onDown() else focusRecord(0) }
            true
        } else false
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            val searchLabel = stringResource(R.string.search_title)
            Pressable({ onOverlayChange(PlaybackShelfOverlay.Search) }, controlModifier().size(24.dp).semantics { contentDescription = searchLabel },
                enabled && !historyVisible && !searchVisible && !preparingHistory, searchRequester, focusedScale = 1.12f, onKey = headerKey) { state ->
                Box(Modifier.fillMaxSize().playbackGlass(state.focused, round = true),
                    contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Search, Palette.Label, 16.dp)
                }
            }
            ShelfAction(stringResource(R.string.recent_history), enabled && !historyVisible && !searchVisible,
                controlModifier(), headerKey, historyRequester) {
                if (!preparingHistory) scope.launch {
                    preparingHistory = true
                    try {
                        historyBackdrop = withTimeoutOrNull(2000) { captureFrostedHome(homeView) }
                        onOverlayChange(PlaybackShelfOverlay.History)
                    } finally { preparingHistory = false }
                }
            }
        }
        if (records.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(116.dp).playbackGlass(false).padding(16.dp)) {
                BasicText(stringResource(when {
                    !RecentPlayback.enabled -> R.string.recent_disabled_hint
                    !RecentPlayback.accessGranted -> R.string.recent_permission_hint
                    RecentPlayback.sessionAccessFailed -> R.string.recent_access_failed
                    else -> R.string.recent_empty_hint
                }), style = Type.Callout, modifier = Modifier.align(Alignment.CenterStart))
            }
        } else {
            Column(Modifier.fillMaxWidth().height(116.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                records.forEachIndexed { index, item ->
                    key(historyKey(item)) {
                        PlaybackRow(item, enabled && !historyVisible && !searchVisible, controlModifier().fillMaxWidth().height(36.dp),
                            requesters[historyKey(item)], onKey = { event ->
                                when (event.nativeKeyEvent.keyCode) {
                                    NativeKeyEvent.KEYCODE_DPAD_UP -> {
                                        if (event.type == KeyEventType.KeyDown) {
                                            if (index == 0) runCatching { historyRequester.requestFocus() } else focusRecord(index - 1)
                                        }; true
                                    }
                                    NativeKeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (event.type == KeyEventType.KeyDown) { if (index == records.lastIndex) onDown() else focusRecord(index + 1) }; true
                                    }
                                    else -> false
                                }
                            }) { onOpen(item) }
                    }
                }
            }
        }
    }
    if (searchVisible) {
        CatalogSearchDialog(onDismiss = {
            restoreFocus = searchRequester
            onOverlayChange(PlaybackShelfOverlay.None)
        })
    }
    if (historyVisible) {
        PlaybackHistoryDialog(historyBackdrop, onOpen, onPermission, onDismiss = {
            restoreFocus = historyRequester
            onOverlayChange(PlaybackShelfOverlay.None)
        })
    }
}

@Composable
private fun PlaybackRow(
    item: RecentItem, enabled: Boolean, modifier: Modifier,
    focusRequester: FocusRequester? = null, large: Boolean = false,
    onKey: ((KeyEvent) -> Boolean)? = null, onOpen: () -> Unit,
) {
    val image by produceState<ImageBitmap?>(null, item.artwork, RecentPlayback.artworkVersion) {
        value = withContext(Dispatchers.IO) {
            runCatching { RecentPlayback.artworkFile(item)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }.getOrNull()
        }
    }
    Pressable(onOpen, modifier, enabled, focusRequester, focusedScale = if (large) 1.04f else 1.09f, onKey = onKey) { state ->
        Box(Modifier.fillMaxSize().playbackGlass(state.focused, light = large)) {
            Row(Modifier.fillMaxSize().padding(horizontal = if (large) 12.dp else 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(if (large) 76.dp else 48.dp).height(if (large) 44.dp else 28.dp)
                    .clip(RoundedCornerShape(5.dp)).background(Palette.Fill)) {
                    image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Column(Modifier.weight(1f)) {
                    BasicText(item.title, style = (if (large) Type.Headline else Type.Callout).copy(color = if (large) Color(0xFF17232F) else Palette.Label),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(listOf(item.appLabel, item.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                        style = Type.Caption.copy(color = if (large) Color(0xFF435365) else Palette.SecondaryLabel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.duration > 0) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Palette.FillStrong)) {
                    Box(Modifier.fillMaxWidth((item.position.toFloat() / item.duration).coerceIn(0f, 1f)).height(2.dp)
                        .background(Color(0xFFB8DEED)))
                }
            }
        }
    }
}

@Composable
private fun PlaybackHistoryDialog(backdrop: ImageBitmap?, onOpen: (RecentItem) -> Unit, onPermission: () -> Unit, onDismiss: () -> Unit) {
    val firstRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
            onDispose { }
        }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val windowWidth = with(density) { maxWidth.toPx() }
            val windowHeight = with(density) { maxHeight.toPx() }
            val records = RecentPlayback.items
            val panelHeight = (94 + records.size.coerceIn(2, 5) * 68).dp.coerceAtMost(434.dp)
            Box(Modifier.width(536.dp).height(panelHeight).clip(RoundedCornerShape(22.dp))) {
                // Crop the prepared Home snapshot to this panel's exact centered location.
                // Outside this box the real desktop stays sharp and undimmed.
                backdrop?.let { bitmap ->
                    Canvas(Modifier.fillMaxSize()) {
                        val width = (bitmap.width * size.width / windowWidth).roundToInt().coerceIn(1, bitmap.width)
                        val height = (bitmap.height * size.height / windowHeight).roundToInt().coerceIn(1, bitmap.height)
                        drawImage(bitmap,
                            srcOffset = IntOffset((bitmap.width - width) / 2, (bitmap.height - height) / 2),
                            srcSize = IntSize(width, height),
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Medium)
                    }
                }
                val fallback = if (backdrop == null) Modifier.background(Color(0xFFF1F5F8)) else Modifier
                Column(Modifier.fillMaxSize().then(fallback).playbackGlass(false, panel = true).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(stringResource(R.string.recent_history), style = Type.Title.copy(color = Color(0xFF17232F)))
                    if (!RecentPlayback.enabled || !RecentPlayback.accessGranted) {
                        ShelfAction(stringResource(if (!RecentPlayback.enabled) R.string.recent_enable else R.string.recent_permission),
                            true, Modifier, { false }, if (records.isEmpty()) firstRequester else null) {
                            if (!RecentPlayback.enabled) RecentPlayback.setRecordingEnabled(true)
                            if (!RecentPlayback.accessGranted) onPermission()
                        }
                    }
                    if (records.isEmpty()) BasicText(stringResource(R.string.recent_empty_hint), style = Type.Body.copy(color = Color(0xFF435365)))
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(records, key = ::historyKey) { item ->
                            PlaybackRow(item, true, Modifier.fillMaxWidth().height(58.dp),
                                focusRequester = if (historyKey(item) == records.firstOrNull()?.let(::historyKey)) firstRequester else null,
                                large = true) { onOpen(item) }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstRequester.requestFocus() }
        }
    }
}

@Composable
private fun ShelfAction(label: String, enabled: Boolean, modifier: Modifier, onKey: (KeyEvent) -> Boolean,
    focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    Pressable(onClick, modifier, enabled, focusRequester, focusedScale = 1.12f, onKey = onKey) { state ->
        BasicText(label, style = Type.Caption.copy(color = Palette.Label),
            modifier = Modifier.height(24.dp).playbackGlass(state.focused).padding(horizontal = 10.dp).wrapContentHeight(Alignment.CenterVertically))
    }
}
