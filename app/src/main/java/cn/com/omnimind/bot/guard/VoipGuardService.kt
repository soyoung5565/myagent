package cn.com.omnimind.bot.guard

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Hermes VoIP Guard — second-layer watchdog for Tailscale + Linphone.
 * Runs as foreground specialUse service, 30s interval.
 * User clears recents → MIUI kills tasks but this service (and Linphone's) auto-restart via AlarmManager.
 * Even if this guard is killed, Linphone's guard revives Tailscale; even if Linphone is killed, this revives Linphone.
 * Mutual revival = stability == phone service level, without "lock to background".
 */
class VoipGuardService : Service() {
    companion object {
        private const val TAG = "[VoipGuard]"
        private const val NOTIF_ID = 9401
        private const val CHANNEL_ID = "voip_guard"
        private const val INTERVAL_MS = 30 * 1000L
        private const val RESTART_DELAY_MS = 5 * 1000L
        private const val PKG_TAILSCALE = "com.tailscale.ipn"
        private const val PKG_LINPHONE = "org.linphone"

        fun start(context: Context) {
            try {
                val intent = Intent(context, VoipGuardService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.i(TAG, "start() requested")
            } catch (e: Exception) {
                Log.e(TAG, "start failed: $e")
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var fireCount = 0

    private val watchdog = object : Runnable {
        override fun run() {
            fireCount++
            try {
                ensurePackage(PKG_TAILSCALE, "Tailscale")
                ensurePackage(PKG_LINPHONE, "Linphone")
                if (fireCount % 4 == 0) Log.i(TAG, "watchdog #$fireCount ok")
            } catch (e: Exception) {
                Log.e(TAG, "watchdog error: $e")
            }
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    private fun ensurePackage(pkg: String, label: String) {
        // Fast check: is process running? Use ActivityManager if available (deprecated but still works for own visibility check is not needed)
        // Instead: just try to launch; if already running Android no-ops the activity launch to foreground if already top.
        // We need to detect "actually dead" to avoid spamming launches when already running. Use getRunningAppProcesses as heuristic.
        var running = false
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (proc in am.runningAppProcesses ?: emptyList()) {
                if (proc.processName == pkg || proc.processName.startsWith("$pkg:")) {
                    running = true; break
                }
            }
        } catch (_: Exception) {}

        // Also check if the app's service is alive via query: if running==false, we must revive; if running==true we still ensure SIP/Tailscale health via Linphone's own watchdog covers it — so skip launch to avoid UI pop.
        if (running && fireCount > 2) {
            // Already running. NOTE: a service-intent poke does NOT revive a dead tun0 tunnel.
            // Only launching the MainActivity reconnects it — but doing that every 30s would keep
            // popping the app to front, so we only do it every 10th cycle as a tunnel health kick.
            if (pkg == PKG_TAILSCALE && fireCount % 10 == 0) {
                try {
                    val launch = packageManager.getLaunchIntentForPackage(pkg)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launch)
                    }
                } catch (_: Exception) {}
            }
            return
        }
        if (!running) {
            Log.w(TAG, "$label not running (check #$fireCount) -> reviving")
            var launched = false
            var err = ""
            try {
                val launch = packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Do not bring to front aggressively: add EXCLUDE_FROM_RECENTS if possible
                    startActivity(launch)
                    launched = true
                    Log.i(TAG, "$label revived via launchIntent")
                } else err = "launchIntent null"
            } catch (e: Exception) {
                err = "launchIntent: $e"
            }
            if (!launched) {
                try {
                    val alt = Intent(Intent.ACTION_MAIN).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(alt)
                    launched = true
                    Log.i(TAG, "$label revived via ACTION_MAIN")
                } catch (e: Exception) {
                    err += " | ACTION_MAIN: $e"
                }
            }
            if (!launched) Log.e(TAG, "Failed to revive $label: $err")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        Log.i(TAG, "onCreate -> watchdog 30s")
        handler.postDelayed(watchdog, 3000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand startId=$startId")
        // ensure foreground if restarted
        try { startForeground(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, 2000L)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "onTaskRemoved -> schedule restart")
        scheduleRestart("onTaskRemoved")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy -> remove watchdog, schedule restart")
        handler.removeCallbacks(watchdog)
        scheduleRestart("onDestroy")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleRestart(reason: String) {
        try {
            val i = Intent(this, VoipGuardService::class.java)
            val pi = PendingIntent.getService(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + RESTART_DELAY_MS, pi)
            Log.w(TAG, "restart scheduled in ${RESTART_DELAY_MS}ms due to $reason")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleRestart failed: $e")
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, "VoIP Guard", NotificationManager.IMPORTANCE_MIN)
            ch.description = "Keeps Tailscale + Linphone alive after swipe"
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("VoIP Guard running")
            .setContentText("Keeping calls ready (30s check)")
            .setOngoing(true)
        return builder.build()
    }
}
