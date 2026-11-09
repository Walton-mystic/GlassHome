package se.glasshome

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast

data class AppEntry(val label: String, val pkg: String)

object Apps {
    /** Home screen apps, in order: label shown, packages to try, labels to match on the TV. */
    val favorites = listOf(
        Triple("GlassTV", listOf<String>(), listOf("GlassTV", "Glass TV")),
        Triple("YouTube", listOf("com.google.android.youtube.tv", "com.google.android.youtube.googletv"), listOf("YouTube")),
        Triple("Netflix", listOf("com.netflix.ninja"), listOf("Netflix")),
        Triple("YouTube Music", listOf("com.google.android.youtube.tvmusic"), listOf("YouTube Music")),
        Triple("Titta på TV", listOf("org.droidtv.playtv"), listOf("Titta på TV", "Watch TV"))
    )

    fun all(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, AppEntry>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(i, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || found.containsKey(pkg)) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                found[pkg] = AppEntry(label, pkg)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    /** Returns the package for a favorite, or null if it isn't installed. */
    fun resolve(ctx: Context, pkgs: List<String>, labels: List<String>, installed: List<AppEntry>): String? {
        val pm = ctx.packageManager
        for (p in pkgs) {
            try {
                pm.getPackageInfo(p, 0)
                if (launchIntent(ctx, p) != null) return p
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        for (l in labels) {
            installed.firstOrNull { it.label.equals(l, ignoreCase = true) }?.let { return it.pkg }
        }
        return null
    }

    private fun launchIntent(ctx: Context, pkg: String): Intent? {
        val pm = ctx.packageManager
        return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    }

    fun launch(ctx: Context, pkg: String) {
        val i = launchIntent(ctx, pkg)
        if (i == null) {
            Toast.makeText(ctx, "Appen gick inte att öppna", Toast.LENGTH_SHORT).show()
            return
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try { ctx.startActivity(i) } catch (_: Exception) {
            Toast.makeText(ctx, "Appen gick inte att öppna", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPlayStore(ctx: Context) {
        val pkgs = listOf("com.android.vending")
        for (p in pkgs) {
            val pm = ctx.packageManager
            val i = pm.getLeanbackLaunchIntentForPackage(p) ?: pm.getLaunchIntentForPackage(p)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { ctx.startActivity(i); return } catch (_: Exception) {}
            }
        }
        Toast.makeText(ctx, "Play Butik hittades inte", Toast.LENGTH_SHORT).show()
    }

    fun openTvSettings(ctx: Context) {
        val tries = listOf(Intent(Settings.ACTION_SETTINGS))
        for (i in tries) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(i); return } catch (_: ActivityNotFoundException) {}
        }
    }
}
