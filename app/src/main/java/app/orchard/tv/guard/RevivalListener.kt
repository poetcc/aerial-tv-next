package app.orchard.tv.guard

import android.service.notification.NotificationListenerService
import android.util.Log
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.media.session.MediaSession
import android.service.notification.StatusBarNotification
import java.lang.ref.WeakReference
import app.orchard.tv.media.RecentPlayback

/**
 * Brings Home Guard back after the firmware kills Aerial.
 *
 * Xiaomi force-stops every third-party app when the TV goes to standby, and Android drops a
 * force-stopped app's accessibility services from the enabled list for good. Notification
 * listeners are treated differently: the system keeps the approval and binds the listener again
 * shortly after its process dies. That rebind is the one thing that reliably restarts Aerial
 * after a standby. No notification bodies are read. When the user enables Recent Playback,
 * this authorization also permits observing standard media sessions and their notification entry.
 */
class RevivalListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var watching = false
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                GuardSetup.recordScreenOff(this@RevivalListener)
                return
            }
            GuardSetup.recover(this@RevivalListener)
            HomeGuardService.checkAfterWake()
        }
    }
    private val adbObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = GuardSetup.ensureAdb(this@RevivalListener)
    }
    private fun watchRecoverySignals() {
        if (watching) return
        ContextCompat.registerReceiver(this, wakeReceiver, IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, adbObserver)
        watching = true
    }
    override fun onListenerConnected() {
        connected = WeakReference(this)
        Log.i("AerialGuard", "revived by the notification listener binding")
        watchRecoverySignals()
        GuardSetup.recover(this)
        RecentPlayback.refresh(this)
    }

    override fun onListenerDisconnected() {
        connected = null
        RecentPlayback.disconnect()
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= 24) runCatching { requestRebind(ComponentName(this, RevivalListener::class.java)) }
    }

    override fun onDestroy() {
        if (watching) {
            runCatching { unregisterReceiver(wakeReceiver) }
            runCatching { contentResolver.unregisterContentObserver(adbObserver) }
            watching = false
        }
        handler.removeCallbacksAndMessages(null)
        connected = null
        RecentPlayback.disconnect()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        captureMediaEntry(sbn)
    }

    @Suppress("DEPRECATION")
    private fun captureMediaEntry(sbn: StatusBarNotification) {
        if (!RecentPlayback.enabled || !RecentPlayback.accessGranted) return
        // Only accept an entry tied to a verified, active MediaSession. Never parse normal messages.
        val token = runCatching {
            sbn.notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        }.getOrNull() ?: return
        val entry = sbn.notification.contentIntent ?: return
        RecentPlayback.acceptMediaEntry(sbn.packageName, token, entry)
    }

    override fun dump(fd: java.io.FileDescriptor?, writer: java.io.PrintWriter, args: Array<out String>?) {
        writer.println(RecentPlayback.diagnostics())
        writer.println("guardRunning=${HomeGuardService.isRunning} recoveryWatching=$watching keepAdb=${GuardSetup.keepAdb(this)}")
    }

    companion object {
        private var connected: WeakReference<RevivalListener>? = null
        val isConnected: Boolean get() = connected?.get() != null

        fun refreshMediaEntries() {
            if (!RecentPlayback.enabled || !RecentPlayback.accessGranted) return
            val service = connected?.get() ?: return
            runCatching { service.activeNotifications?.forEach(service::captureMediaEntry) }
        }
    }
}
