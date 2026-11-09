package se.glasshome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the wake watcher after boot or an app update, and shows the home screen after boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try { WakeService.start(ctx) } catch (_: Exception) {}
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WakeService.goHome(ctx)
        }
    }
}
