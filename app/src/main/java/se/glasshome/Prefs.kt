package se.glasshome

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("glasshome", Context.MODE_PRIVATE)

    /** Index into Art.collections */
    var collection: Int
        get() = p.getInt("collection", 0)
        set(v) = p.edit().putInt("collection", v).apply()

    /** Minutes between paintings */
    var rotateMinutes: Int
        get() = p.getInt("rotate", 60)
        set(v) = p.edit().putInt("rotate", v).apply()

    var showWeather: Boolean
        get() = p.getBoolean("weather", true)
        set(v) = p.edit().putBoolean("weather", v).apply()

    /** Seconds without remote input before everything fades out; 0 = never */
    var idleSeconds: Int
        get() = p.getInt("idleSeconds", 30)
        set(v) = p.edit().putInt("idleSeconds", v).apply()

    var homeOnWake: Boolean
        get() = p.getBoolean("homeOnWake", true)
        set(v) = p.edit().putBoolean("homeOnWake", v).apply()

    var paintingCaption: String
        get() = p.getString("caption", "") ?: ""
        set(v) = p.edit().putString("caption", v).apply()

    var paintingChangedAt: Long
        get() = p.getLong("paintingAt", 0L)
        set(v) = p.edit().putLong("paintingAt", v).apply()

    var lastIndex: Int
        get() = p.getInt("lastIndex", -1)
        set(v) = p.edit().putInt("lastIndex", v).apply()

    var artCache: String
        get() = p.getString("artCache2", "") ?: ""
        set(v) = p.edit().putString("artCache2", v).apply()

    var artCacheFor: Int
        get() = p.getInt("artCacheFor2", -1)
        set(v) = p.edit().putInt("artCacheFor2", v).apply()

    var weatherCache: String
        get() = p.getString("weatherCache", "") ?: ""
        set(v) = p.edit().putString("weatherCache", v).apply()

    var weatherAt: Long
        get() = p.getLong("weatherAt", 0L)
        set(v) = p.edit().putLong("weatherAt", v).apply()

    var updateCheckedAt: Long
        get() = p.getLong("updAt", 0L)
        set(v) = p.edit().putLong("updAt", v).apply()

    var updateUrl: String
        get() = p.getString("updUrl", "") ?: ""
        set(v) = p.edit().putString("updUrl", v).apply()

    var updateVersion: Int
        get() = p.getInt("updVer", 0)
        set(v) = p.edit().putInt("updVer", v).apply()

    // ---------- Hidden baby monitor (stored only on the TV, never in the code)
    var camIp: String
        get() = p.getString("camIp", "") ?: ""
        set(v) = p.edit().putString("camIp", v).apply()

    var camPort: String
        get() = p.getString("camPort", "554") ?: "554"
        set(v) = p.edit().putString("camPort", v).apply()

    var camUser: String
        get() = p.getString("camUser", "") ?: ""
        set(v) = p.edit().putString("camUser", v).apply()

    var camPass: String
        get() = p.getString("camPass", "") ?: ""
        set(v) = p.edit().putString("camPass", v).apply()

    var camPath: String
        get() = p.getString("camPath", "stream2") ?: "stream2"
        set(v) = p.edit().putString("camPath", v).apply()

    var camCorner: Int
        get() = p.getInt("camCorner", 0)
        set(v) = p.edit().putInt("camCorner", v).apply()

    var camLarge: Boolean
        get() = p.getBoolean("camLarge", false)
        set(v) = p.edit().putBoolean("camLarge", v).apply()

    val camConfigured: Boolean get() = camIp.isNotBlank()

    fun camUrl(): String {
        fun enc(x: String) = java.net.URLEncoder.encode(x, "UTF-8").replace("+", "%20")
        val auth = if (camUser.isNotEmpty()) "${enc(camUser)}:${enc(camPass)}@" else ""
        val port = camPort.ifBlank { "554" }
        return "rtsp://$auth${camIp.trim()}:$port/${camPath.trim().trimStart('/')}"
    }
}
