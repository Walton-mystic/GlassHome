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
        get() = p.getString("artCache", "") ?: ""
        set(v) = p.edit().putString("artCache", v).apply()

    var artCacheFor: Int
        get() = p.getInt("artCacheFor", -1)
        set(v) = p.edit().putInt("artCacheFor", v).apply()

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
}
