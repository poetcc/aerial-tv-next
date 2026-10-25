package app.orchard.tv.guard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.getSystemService
import app.orchard.tv.MainActivity

/**
 * Keeps Aerial as the home screen on firmwares that will not let a third-party app own HOME
 * (Xiaomi PatchWall, some TCL and Hisense builds), without disabling any system app:
 *
 * 1. The Home key is taken before the system handles it and answered with Aerial.
 * 2. If the stock launcher's home activity still comes to the front (an app exits to it, the TV
 *    boots into it), Aerial is brought back on top.
 *
 * Both only act while Aerial is not the resolved HOME app; once it is, the system does the work.
 * It also lets Control Center put the TV to sleep, which no ordinary app permission allows.
 * The service reads window-change events only; it never reads screen content.
 */
class HomeGuardService : AccessibilityService() {

    /** HOME activities that are not ours: package to class names. Refreshed on every connect. */
    private var stockHomes: Map<String, Set<String>> = emptyMap()

    /** Set when the user opens the stock launcher on purpose from Aerial's grid. */
    private var allowStockUntil = 0L

    /** The last window that came to the front, as package to class; null until one does. */
    private var lastWindow: Pair<String, String>? = null

    private val handler = Handler(Looper.getMainLooper())
    private var lastHomeLaunch = Long.MIN_VALUE / 2
    private var transientPackages: Set<String> = setOf("android", "com.android.systemui")
    private val wakeCheck = Runnable { takeOverIfStockHomeInFront("wake recovery") }

    private fun scheduleWakeCheck() {
        handler.removeCallbacks(wakeCheck)
        // A transient system window can arrive after the stock launcher during wake.
        listOf(700L, 2200L, 6000L).forEach { handler.postDelayed(wakeCheck, it) }
    }

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Firmwares put their own launcher back in front while the TV is in standby, and no
            // window change follows on wake. Look once things have settled after the screen lights.
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                GuardSetup.recordScreenOff(this@HomeGuardService)
                return
            }
            GuardSetup.recover(this@HomeGuardService)
            scheduleWakeCheck()
        }
    }

    private val adbObserver by lazy {
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = GuardSetup.ensureAdb(this@HomeGuardService)
        }
    }

    override fun onServiceConnected() {
        instance = this
        refreshStockHomes()
        transientPackages = transientPackages + getSystemService<InputMethodManager>()?.inputMethodList.orEmpty().map { it.packageName }
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) })
        contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, adbObserver)
        GuardSetup.ensureAdb(this)
        // Bound a little after boot, or brought back after the firmware killed Aerial: either way
        // the stock launcher is what's in front, and the window change that showed it is long gone.
        // Not after an update, though: installing over Aerial also kills it, and whoever is watching
        // an input or another app at that moment must not be pulled back to Home.
        val revived = SystemClock.elapsedRealtime() - GuardSetup.lastRevivedAt < 60_000 && !justUpdated()
        if ((SystemClock.elapsedRealtime() < BOOT_WINDOW_MS || revived) && !isDefaultHome() &&
            getSystemService<PowerManager>()?.isInteractive == true
        ) {
            Log.i(TAG, if (revived) "re-enabled after being switched off; taking over Home" else "bound shortly after boot; taking over Home")
            goHome()
        } else if (!justUpdated()) {
            scheduleWakeCheck()
        }
    }

    private fun justUpdated(): Boolean = runCatching {
        val updatedAt = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        System.currentTimeMillis() - updatedAt < 2 * 60_000 && !GuardSetup.screenOffAfterUpdate(this, updatedAt)
    }.getOrDefault(false)

    private fun takeOverIfStockHomeInFront(reason: String) {
        if (isDefaultHome() || getSystemService<PowerManager>()?.isInteractive != true) return
        val last = lastWindow
        // Unknown means we were only just bound, which happens after a standby kill.
        val stockInFront = stockHomeInFront(last, stockHomes)
        if (!stockInFront || SystemClock.uptimeMillis() < allowStockUntil) return
        Log.i(TAG, "$reason with the stock launcher in front; bringing Aerial back")
        goHome()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOn) }
        runCatching { contentResolver.unregisterContentObserver(adbObserver) }
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (isDefaultHome()) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            Log.i(TAG, "Home key taken before the system saw it")
            goHome()
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        // Ignore system/IME popups: they do not replace the foreground activity underneath.
        if (pkg in transientPackages) return
        lastWindow = rememberGuardWindow(lastWindow, pkg, cls, event.isFullScreen, stockHomes, transientPackages, packageName)
        // Only the stock launcher's home activity itself, not its dialogs or other screens.
        if (cls !in (stockHomes[pkg] ?: return)) return
        if (SystemClock.uptimeMillis() < allowStockUntil) return
        if (isDefaultHome() || getSystemService<PowerManager>()?.isInteractive != true) return
        Log.i(TAG, "stock home $pkg came to the front; bringing Aerial back")
        goHome()
    }

    private fun isDefaultHome(): Boolean =
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName == packageName

    private fun refreshStockHomes() {
        stockHomes = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
            // Negative priority entries are fallbacks shown only while nothing else can be Home.
            .filter { it.activityInfo.packageName != packageName && it.priority >= 0 }
            .groupBy({ it.activityInfo.packageName }, { it.activityInfo.name })
            .mapValues { it.value.toSet() }
    }

    private fun goHome() {
        val now = SystemClock.uptimeMillis()
        if (now - lastHomeLaunch < 650) return
        lastHomeLaunch = now
        runCatching { startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        ) }.onFailure { Log.w(TAG, "could not return to Aerial", it) }
    }

    companion object {
        private const val TAG = "AerialGuard"
        private const val BOOT_WINDOW_MS = 3 * 60 * 1000L

        @Volatile
        private var instance: HomeGuardService? = null

        val isRunning: Boolean get() = instance != null
        fun checkAfterWake() { instance?.scheduleWakeCheck() }

        /** Lets the user reach the stock launcher from the grid without being bounced back. */
        fun allowStockLauncher(packageName: String) {
            val s = instance ?: return
            if (packageName in s.stockHomes) s.allowStockUntil = SystemClock.uptimeMillis() + 60 * 60 * 1000L
        }

        /** Called when Aerial comes back to the front: the stock launcher is fair game again. */
        fun onAerialResumed() {
            instance?.allowStockUntil = 0L
        }

        /** The lock-screen action that puts the TV to sleep arrived in Android 9. */
        val canSleep: Boolean get() = Build.VERSION.SDK_INT >= 28

        /** Puts the TV into standby, the same way the power button's short press does. */
        fun sleep(): Boolean = canSleep && instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}
