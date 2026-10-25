package app.orchard.tv.dream

import android.service.dreams.DreamService
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.orchard.tv.ui.home.Screensaver
import app.orchard.tv.ui.home.ScreensaverSkips
import app.orchard.tv.ui.home.rememberNow
import app.orchard.tv.wallpaper.Wallpapers

/**
 * Aerial's screensaver as the TV's system screensaver, so it starts from any app, as on Apple TV,
 * rather than only on Home. TVs that use Android's standard screensaver (TCL among them) get it
 * set by tools/install.sh in place of the maker's own, which on TCL advertises a paid membership.
 * Xiaomi runs its own screensaver outside this mechanism; there Aerial's Home timer covers it.
 */
class AerialDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val skips = ScreensaverSkips()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Interactive so left / right can step through the films; every other key ends the dream
        // in dispatchKeyEvent, as a non-interactive dream would.
        isInteractive = true
        isFullscreen = true
        Wallpapers.init(this)
        val metrics = resources.displayMetrics
        val canvas = minOf(metrics.widthPixels, metrics.heightPixels * 16 / 9) / 960f
        setContentView(
            ComposeView(this).apply {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(canvas, LocalDensity.current.fontScale)) {
                        val now by rememberNow()
                        Screensaver(visible = true, apps = emptyList(), now = now, skips = skips)
                    }
                }
            },
        )
        // Compose finds its lifecycle on the window's root view.
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (skips.onKey(event)) return true
        if (event.action == KeyEvent.ACTION_DOWN) finish()
        return true
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        registry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
