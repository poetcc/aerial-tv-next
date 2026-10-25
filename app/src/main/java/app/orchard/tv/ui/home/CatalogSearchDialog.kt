package app.orchard.tv.ui.home

import android.view.KeyEvent as NativeKeyEvent
import android.util.TypedValue
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.orchard.tv.R
import app.orchard.tv.search.*
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun CatalogSearchDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var query by remember { mutableStateOf("") }
    var requestedPage by remember { mutableStateOf(1) }
    var retry by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<List<CatalogHit>>(emptyList()) }
    var lastPage by remember { mutableStateOf(0) }
    var pages by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf<CatalogPlatform?>(null) }
    var imeMode by remember { mutableStateOf(false) }
    var inputView by remember { mutableStateOf<SearchTextInput?>(null) }
    var activeNode by remember { mutableStateOf("key:0") }
    var lastKeyboard by remember { mutableStateOf(0) }
    var rootHasFocus by remember { mutableStateOf(false) }
    var pendingResults by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var focusJob by remember { mutableStateOf<Job?>(null) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(node: String) = requesters.getOrPut(node) { FocusRequester() }
    val letters = remember { ('A'..'Z').map(Char::toString) + ('0'..'9').map(Char::toString) }
    val ranked = remember(rows, query) { fairCatalogResults(rows, query) }
    val visible = ranked.filter { selectedPlatform == null || it.route.platform == selectedPlatform }
    val hasMore = query.isNotBlank() && lastPage > 0 && lastPage < pages && rows.size < 400
    val retryVisible = failed || loading && activeNode == "retry"
    val filterIndex = selectedPlatform?.ordinal?.plus(1) ?: 0
    val graph = SearchFocusGraph(visible.map { "hit:${it.key}" }, hasMore, retryVisible, lastKeyboard, filterIndex)
    fun navigate(node: String) {
        val target = node.takeIf { it in graph.nodes } ?: "key:0"
        focusJob?.cancel()
        focusJob = scope.launch {
            val index = graph.results.indexOf(target)
            if (index >= 0) gridState.scrollToItem(index)
            withFrameNanos { }
            val focused = runCatching { requester(target).requestFocus() }.getOrDefault(false)
            if (!focused) runCatching { requester("key:0").requestFocus() }
        }
    }
    fun onFocus(node: String) {
        if (activeNode != node) Log.d("AerialSearch", "Focus=$node")
        activeNode = node
        if (node.startsWith("key:")) lastKeyboard = node.substringAfter(':').toIntOrNull() ?: 0
    }
    fun keyHandler(node: String): (KeyEvent) -> Boolean = { event ->
        val direction = when (event.nativeKeyEvent.keyCode) {
            NativeKeyEvent.KEYCODE_DPAD_LEFT -> SearchDirection.Left
            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> SearchDirection.Right
            NativeKeyEvent.KEYCODE_DPAD_UP -> SearchDirection.Up
            NativeKeyEvent.KEYCODE_DPAD_DOWN -> SearchDirection.Down
            else -> null
        }
        if (direction == null) false else {
            if (event.type == KeyEventType.KeyDown) navigate(graph.move(node, direction))
            true
        }
    }
    fun change(value: String) {
        query = value.filterNot { it.isISOControl() }.take(40)
        requestedPage = 1
    }
    fun finishInput(results: Boolean) {
        Log.d("AerialSearch", "Input closed; selectResults=$results")
        inputView?.hideKeyboard()
        imeMode = false
        pendingResults = results
        if (!results) navigate("input")
    }
    fun submit() {
        if (query.isNotBlank()) { pendingResults = true; requestedPage = 1; retry++ }
        else navigate("input")
    }
    LaunchedEffect(query, requestedPage, retry) {
        failed = false
        if (requestedPage == 1) { rows = emptyList(); lastPage = 0; pages = 1 }
        if (query.isBlank()) { loading = false; return@LaunchedEffect }
        loading = true
        try {
            if (requestedPage == 1) delay(300)
            val response = CatalogService.search(query.trim(), requestedPage)
            rows = (if (requestedPage == 1) response.hits else rows + response.hits).distinctBy { it.key }.take(400)
            lastPage = response.page; pages = response.pages
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    Dialog(onDismissRequest = { inputView?.hideKeyboard(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
            onDispose { inputView?.hideKeyboard() }
        }
        LaunchedEffect(graph.nodes, rootHasFocus, windowFocused, imeMode, loading, pendingResults) {
            if (windowFocused && !imeMode) {
                if (pendingResults && !loading) {
                    pendingResults = false
                    navigate(graph.firstResult)
                } else if ((!rootHasFocus || activeNode !in graph.nodes) && focusJob?.isActive != true) {
                    navigate(activeNode.takeIf { it in graph.nodes } ?: "key:0")
                }
            }
        }
        Column(Modifier.fillMaxSize().background(Color(0xFF101720)).padding(horizontal = 32.dp, vertical = 24.dp)
            .onFocusChanged { rootHasFocus = it.hasFocus }.focusGroup()
            .onPreviewKeyEvent { event ->
                context.searchActivity()?.onUserInteraction()
                if (imeMode || event.type != KeyEventType.KeyDown || event.nativeKeyEvent.repeatCount > 0) false
                else when (val code = event.nativeKeyEvent.keyCode) {
                    in NativeKeyEvent.KEYCODE_A..NativeKeyEvent.KEYCODE_Z -> { change(query + ('A' + code - NativeKeyEvent.KEYCODE_A)); true }
                    in NativeKeyEvent.KEYCODE_0..NativeKeyEvent.KEYCODE_9 -> { change(query + ('0' + code - NativeKeyEvent.KEYCODE_0)); true }
                    NativeKeyEvent.KEYCODE_DEL -> { change(query.dropLast(1)); true }
                    else -> false
                }
            }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(stringResource(R.string.search_title), style = Type.Title)
                BasicText(stringResource(R.string.search_back_hint), style = Type.Caption)
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.width(250.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (imeMode) {
                        val hint = stringResource(R.string.search_input_placeholder)
                        AndroidView(factory = { nativeContext -> SearchTextInput(nativeContext).apply {
                            setText(query); setSelection(text.length)
                            setTextColor(android.graphics.Color.WHITE)
                            setHintTextColor(0x99FFFFFF.toInt()); this.hint = hint
                            setTextSize(TypedValue.COMPLEX_UNIT_PX, with(density) { Type.Body.fontSize.toPx() })
                            val padding = with(density) { 12.dp.roundToPx() }; setPadding(padding, 0, padding, 0)
                            inputView = this
                            post { showKeyboard() }
                        } }, modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.20f)), update = { input ->
                            input.onQuery = { change(it) }
                            input.onFinish = { finishInput(it) }
                            if (input.text.toString() != query) { input.setText(query); input.setSelection(input.text.length) }
                        })
                    } else {
                        Pressable({ imeMode = true }, Modifier.fillMaxWidth().height(44.dp), focusRequester = requester("input"),
                            onKey = keyHandler("input")) { state ->
                            if (state.focused) SideEffect { onFocus("input") }
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = if (state.focused) 0.24f else 0.08f)).padding(12.dp),
                                contentAlignment = Alignment.CenterStart) {
                                BasicText(query.ifEmpty { stringResource(R.string.search_input_placeholder) },
                                    style = Type.Body.copy(color = if (query.isBlank()) Palette.SecondaryLabel else Palette.Label), maxLines = 1)
                            }
                        }
                    }
                    BasicText(stringResource(if (imeMode) R.string.search_ime_hint else R.string.search_initials_hint), style = Type.Caption)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(6) { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(6) { col ->
                                val index = row * 6 + col; val node = "key:$index"
                                SearchControl(letters[index], Modifier.size(34.dp, 32.dp), requester(node), onFocused = { onFocus(node) },
                                    onKey = keyHandler(node)) { change(query + letters[index]) }
                            }
                        } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("delete" to R.string.search_delete, "clear" to R.string.search_clear, "submit" to R.string.search_submit).forEach { (node, label) ->
                            SearchControl(stringResource(label), Modifier.width(76.dp).height(32.dp), requester(node), onFocused = { onFocus(node) },
                                onKey = keyHandler(node)) {
                                when (node) { "delete" -> change(query.dropLast(1)); "clear" -> change(""); else -> submit() }
                            }
                        }
                    }
                    SearchControl(stringResource(R.string.search_chinese_input), Modifier.fillMaxWidth().height(32.dp), requester("chinese"),
                        onFocused = { onFocus("chinese") }, onKey = keyHandler("chinese")) { imeMode = true }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf<CatalogPlatform?>(null) + CatalogPlatform.entries).forEachIndexed { index, platform ->
                            val node = "filter:$index"
                            SearchControl(stringResource(platform?.searchLabel() ?: R.string.search_all), Modifier.width(if (index == 0) 52.dp else 80.dp).height(28.dp),
                                requester(node), selected = selectedPlatform == platform, onFocused = { onFocus(node) }, onKey = keyHandler(node)) { selectedPlatform = platform }
                        }
                    }
                    BasicText(stringResource(when {
                        query.isBlank() -> R.string.search_start_hint
                        loading -> R.string.search_loading
                        failed -> R.string.search_failed
                        else -> R.string.search_count
                    }, visible.size), style = Type.Callout)
                    if (query.isNotBlank() && !loading && !failed && visible.isEmpty()) BasicText(stringResource(R.string.search_empty), style = Type.Body)
                    LazyVerticalGrid(columns = GridCells.Fixed(3), state = gridState, modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(visible, key = { it.key }) { hit ->
                            val node = "hit:${hit.key}"
                            SearchResultCard(hit, requester(node), onFocused = { onFocus(node) }, onKey = keyHandler(node)) {
                                CatalogService.open(context, hit)
                            }
                        }
                    }
                    if (retryVisible) SearchControl(stringResource(if (loading) R.string.search_loading else R.string.search_retry), Modifier.width(120.dp).height(30.dp),
                        requester("retry"), onFocused = { onFocus("retry") }, onKey = keyHandler("retry")) { if (!loading) submit() }
                    if (hasMore) SearchControl(stringResource(if (loading) R.string.search_loading else R.string.search_more), Modifier.width(120.dp).height(30.dp),
                        requester("more"), onFocused = { onFocus("more") }, onKey = keyHandler("more")) { if (!loading) requestedPage = lastPage + 1 }
                }
            }
        }
    }
}

private fun CatalogPlatform.searchLabel(): Int = when (this) {
    CatalogPlatform.IQIYI -> R.string.search_iqiyi
    CatalogPlatform.YOUKU -> R.string.search_youku
    CatalogPlatform.TENCENT -> R.string.search_tencent
    CatalogPlatform.MANGO -> R.string.search_mango
}

@Composable
private fun SearchControl(label: String, modifier: Modifier, focusRequester: FocusRequester,
    selected: Boolean = false, onFocused: () -> Unit, onKey: (KeyEvent) -> Boolean, onClick: () -> Unit) {
    Pressable(onClick, modifier.onFocusChanged { if (it.isFocused) onFocused() }, focusRequester = focusRequester,
        focusedScale = 1.1f, onKey = onKey) { state ->
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = if (state.focused) 0.30f else if (selected) 0.14f else 0.06f))
            .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            BasicText(label, style = Type.Callout.copy(color = Palette.Label), maxLines = 1)
        }
    }
}

@Composable
private fun SearchResultCard(hit: CatalogHit, requester: FocusRequester, onFocused: () -> Unit, onKey: (KeyEvent) -> Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, hit.poster) { value = CatalogImages.load(context, hit.poster)?.asImageBitmap() }
    Pressable(onOpen, Modifier.fillMaxWidth().height(166.dp).onFocusChanged { if (it.isFocused) onFocused() },
        focusRequester = requester, focusedScale = 1.09f, onKey = onKey) { state ->
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = if (state.focused) 0.22f else 0.055f))) {
            Box(Modifier.fillMaxWidth().height(108.dp).background(Palette.Fill)) {
                image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(hit.title, style = Type.Callout.copy(color = Palette.Label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = listOf(stringResource(hit.route.platform.searchLabel()), hit.year, hit.status, hit.tag).filter { it.isNotBlank() }.joinToString(" · ")
                BasicText(subtitle, style = Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
