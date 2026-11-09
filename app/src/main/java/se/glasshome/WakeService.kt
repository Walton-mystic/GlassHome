package se.glasshome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Keeps a tiny watcher alive so the home screen comes up every time the TV wakes from standby.
 * If another app (for example YouTube started from Google Home) takes over right after waking,
 * the home screen stays out of the way.
 */
class WakeService : Service() {
    private val ui = Handler(Looper.getMainLooper())
    private var screenOnAt = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) return
            if (!Prefs(ctx).homeOnWake) return
            screenOnAt = System.currentTimeMillis()
            ui.removeCallbacksAndMessages(null)
            ui.postDelayed({
                if (!otherAppTookOver(ctx, screenOnAt - 1500)) goHome(ctx)
            }, 3000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "GlassHome", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("GlassHome")
            .setSmallIcon(R.drawable.wx_sun)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, n)
            }
        } catch (_: Exception) {
            stopSelf()
            return
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "glasshome_wake"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, WakeService::class.java))
        }

        fun goHome(ctx: Context) {
            val i = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            try { ctx.startActivity(i) } catch (_: Exception) {}
        }

        /** True if some app other than us or system UI came to the front since [since]. */
        private fun otherAppTookOver(ctx: Context, since: Long): Boolean {
            return try {
                val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return false
                val events = usm.queryEvents(since, System.currentTimeMillis())
                val e = UsageEvents.Event()
                val ignore = setOf(
                    ctx.packageName, "com.android.systemui", "android",
                    "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx"
                )
                while (events.hasNextEvent()) {
                    events.getNextEvent(e)
                    @Suppress("DEPRECATION")
                    if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && e.packageName !in ignore) return true
                }
                false
            } catch (_: Exception) {
                false
            }
        }
    }
}
