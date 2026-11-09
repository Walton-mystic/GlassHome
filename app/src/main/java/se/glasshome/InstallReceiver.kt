package se.glasshome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { ctx.startActivity(confirm) } catch (_: Exception) {}
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(ctx, "Uppdateringen avbröts", Toast.LENGTH_SHORT).show()
        }
    }
}
