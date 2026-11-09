package se.glasshome

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Public-domain oil paintings from the Art Institute of Chicago open API.
 * Only landscape-shaped paintings are used so they fill the TV like a framed canvas.
 */
object Art {
    data class Collection(val name: String, val query: String)

    val collections = listOf(
        Collection("Landskap", "landscape"),
        Collection("Impressionister", "impressionism landscape"),
        Collection("Claude Monet", "Claude Monet"),
        Collection("Hav och sjöar", "seascape"),
        Collection("Kvällsljus", "evening landscape")
    )

    private fun searchUrl(q: String): String {
        val enc = URLEncoder.encode(q, "UTF-8")
        return "https://api.artic.edu/api/v1/artworks/search?q=$enc" +
            "&query%5Bterm%5D%5Bis_public_domain%5D=true" +
            "&fields=id,title,artist_title,image_id,classification_title,medium_display,thumbnail" +
            "&limit=100"
    }

    /** Returns a JSON array of {image_id,title,artist}; cached per collection. */
    private fun list(ctx: Context, prefs: Prefs): JSONArray {
        val idx = prefs.collection.coerceIn(0, collections.size - 1)
        if (prefs.artCacheFor == idx && prefs.artCache.isNotEmpty()) {
            val cached = JSONArray(prefs.artCache)
            if (cached.length() > 0) return cached
        }
        val out = JSONArray()
        val data = JSONObject(Net.text(searchUrl(collections[idx].query))).getJSONArray("data")
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val img = o.optString("image_id", "")
            if (img.isEmpty() || img == "null") continue
            val cls = o.optString("classification_title", "").lowercase()
            val medium = o.optString("medium_display", "").lowercase()
            if (!cls.contains("painting") && !medium.contains("oil")) continue
            val th = o.optJSONObject("thumbnail")
            if (th != null) {
                val w = th.optDouble("width", 0.0)
                val h = th.optDouble("height", 0.0)
                if (w <= 0 || h <= 0 || w / h < 1.2) continue
            }
            out.put(JSONObject().apply {
                put("image_id", img)
                put("title", o.optString("title", ""))
                put("artist", o.optString("artist_title", "").let { if (it == "null") "" else it })
            })
        }
        prefs.artCache = out.toString()
        prefs.artCacheFor = idx
        return out
    }

    /** Downloads the next painting into filesDir/painting.jpg. Runs on a background thread. */
    fun next(ctx: Context): Boolean {
        val prefs = Prefs(ctx)
        val arr = list(ctx, prefs)
        if (arr.length() == 0) return false
        var pick = Random.nextInt(arr.length())
        if (arr.length() > 1 && pick == prefs.lastIndex) pick = (pick + 1) % arr.length()
        val o = arr.getJSONObject(pick)
        val url = "https://www.artic.edu/iiif/2/${o.getString("image_id")}/full/1686,/0/default.jpg"
        val target = File(ctx.filesDir, "painting.jpg")
        Net.download(url, target)
        prefs.lastIndex = pick
        val title = o.optString("title")
        val artist = o.optString("artist")
        prefs.paintingCaption = if (artist.isNotEmpty()) "$title · $artist" else title
        prefs.paintingChangedAt = System.currentTimeMillis()
        return true
    }

    fun isDue(ctx: Context): Boolean {
        val p = Prefs(ctx)
        val f = File(ctx.filesDir, "painting.jpg")
        if (!f.exists()) return true
        return System.currentTimeMillis() - p.paintingChangedAt > p.rotateMinutes * 60_000L
    }
}
