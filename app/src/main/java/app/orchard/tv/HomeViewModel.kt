package app.orchard.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.orchard.tv.apps.AppCatalog
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.apps.LayoutStore
import app.orchard.tv.system.Brightness
import app.orchard.tv.system.HomeStatus
import app.orchard.tv.system.NetworkStatus
import app.orchard.tv.system.SystemProbe
import app.orchard.tv.system.TvSource
import app.orchard.tv.system.VolumeStatus
import app.orchard.tv.ui.home.ScreensaverSkips
import app.orchard.tv.ui.home.PlaybackShelfOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Overlay {
    data object None : Overlay
    data class ControlCenter(val page: CcPage = CcPage.Main) : Overlay
    data class AppMenu(val key: String, val anchor: Rect) : Overlay
    data object HiddenApps : Overlay
    data object Wallpaper : Overlay
}

enum class CcPage { Main, Inputs, Settings }

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val context get() = getApplication<Application>()
    private val store = LayoutStore(app)
    private var all: List<AppEntry> = emptyList()

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var hiddenApps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set

    var overlay by mutableStateOf<Overlay>(Overlay.None)
    var movingKey by mutableStateOf<String?>(null); private set

    var network by mutableStateOf(NetworkStatus()); private set
    var volume by mutableStateOf(VolumeStatus()); private set
    var sources by mutableStateOf<List<TvSource>>(emptyList()); private set
    var homeStatus by mutableStateOf(HomeStatus.NotDefault); private set

    /** Panel brightness 0..1, or null when Aerial cannot change it on this TV. */
    var brightness by mutableStateOf<Float?>(null); private set

    /**
     * Set when Home is pressed: go back to the top row. Done once the window has focus again,
     * since a focus request made while another app is in front is lost.
     */
    var homeResetPending by mutableStateOf(false)
    var playbackOverlay by mutableStateOf(PlaybackShelfOverlay.None)

    /** Aerial's screensaver is up (see MainActivity's idle timer). */
    var screensaverVisible by mutableStateOf(false)
    val screensaverSkips = ScreensaverSkips()

    private var loadJob: Job? = null

    init { reload() }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            all = AppCatalog.load(context)
            arrange()
            loaded = true
        }
    }

    private fun arrange() {
        val (visible, hidden) = store.arrange(all)
        apps = visible
        hiddenApps = hidden
    }

    fun refreshSystem() {
        viewModelScope.launch {
            val ctx = context
            val (n, v, s) = withContext(Dispatchers.Default) {
                Triple(SystemProbe.network(ctx), SystemProbe.volume(ctx), SystemProbe.sources(ctx))
            }
            network = n; volume = v; sources = s
            homeStatus = SystemProbe.homeStatus(ctx)
            brightness = Brightness.level(ctx)
        }
    }

    fun refreshNetwork() { network = SystemProbe.network(context) }

    fun adjustVolume(direction: Int) {
        SystemProbe.adjustVolume(context, direction)
        volume = SystemProbe.volume(context)
    }

    fun adjustBrightness(direction: Int) {
        brightness = Brightness.adjust(context, direction)
    }

    fun toggleMute() {
        SystemProbe.toggleMute(context)
        volume = SystemProbe.volume(context)
    }

    fun hide(key: String) {
        store.hide(key)
        arrange()
    }

    fun unhide(key: String) {
        store.unhide(key)
        arrange()
    }

    fun startMove(key: String) {
        overlay = Overlay.None
        movingKey = key
    }

    /** Moves the app being moved by [delta] slots in the visible grid. */
    fun moveBy(delta: Int) {
        val key = movingKey ?: return
        val from = apps.indexOfFirst { it.key == key }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, apps.lastIndex)
        if (to == from) return
        store.move(key, apps[to].key)
        arrange()
    }

    fun endMove() { movingKey = null }

    fun onHomePressed() {
        playbackOverlay = PlaybackShelfOverlay.None
        screensaverVisible = false
        overlay = Overlay.None
        movingKey = null
        homeResetPending = true
    }
}
