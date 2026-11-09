package se.glasshome

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File

/** Checks GitHub releases for a newer GlassHome and installs it. */
object Updater {
    private const val REPO = "Walton-mystic/GlassHome"
    private var checking = false

    fun available(ctx: Context): Boolean {
        val p = Prefs(ctx)
        return p.updateVersion > BuildConfig.VERSION_CODE && p.updateUrl.isNotEmpty()
    }

    fun checkInBackground(ctx: Context, force: Boolean = false, done: (Boolean) -> Unit) {
        val p = Prefs(ctx)
        if (checking) return
        if (!force && System.currentTimeMillis() - p.updateCheckedAt < 6 * 3600_000L) return
        checking = true
        Net.io.execute {
            var found = false
            try {
                val o = JSONObject(Net.text("https://api.github.com/repos/$REPO/releases/latest", "application/vnd.github+json"))
                val tag = o.optString("tag_name")
                val ver = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val assets = o.optJSONArray("assets")
                var url = ""
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break }
                }
                p.updateCheckedAt = System.currentTimeMillis()
                p.updateVersion = ver
                p.updateUrl = url
                found = ver > BuildConfig.VERSION_CODE && url.isNotEmpty()
            } catch (_: Exception) {
            }
            checking = false
            Handler(Looper.getMainLooper()).post { done(found) }
        }
    }

    fun install(a: Activity) {
        val pm = a.packageManager
        if (!pm.canRequestPackageInstalls()) {
            Toast.makeText(a, "Tillåt GlassHome att installera appar och försök igen", Toast.LENGTH_LONG).show()
            try {
                a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            } catch (_: Exception) {}
            return
        }
        val url = Prefs(a).updateUrl
        if (url.isEmpty()) return
        Toast.makeText(a, "Laddar ner uppdateringen…", Toast.LENGTH_SHORT).show()
        val ctx = a.applicationContext
        Net.io.execute {
            try {
                val apk = File(ctx.cacheDir, "update.apk")
                Net.download(url, apk)
                val installer = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(ctx.packageName)
                val id = installer.createSession(params)
                installer.openSession(id).use { s ->
                    s.openWrite("glasshome.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        s.fsync(out)
                    }
                    val pi = PendingIntent.getBroadcast(
                        ctx, id, Intent(ctx, InstallReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    s.commit(pi.intentSender)
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(ctx, "Uppdateringen misslyckades", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
