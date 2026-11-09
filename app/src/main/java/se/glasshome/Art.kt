package se.glasshome

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Backgrounds for the home screen:
 *  - public-domain oil paintings from the Art Institute of Chicago open API
 *  - real 4K landscape photos from Wikimedia Commons "Featured pictures" (free licences, credited on screen)
 * Only wide images are used so they fill the TV without odd cropping.
 */
object Art {
    enum class Source { PAINTING, PHOTO }
    data class Collection(val name: String, val source: Source, val query: String)

    val collections = listOf(
        Collection("Oljemålningar · Landskap", Source.PAINTING, "landscape"),
        Collection("Oljemålningar · Impressionister", Source.PAINTING, "impressionism landscape"),
        Collection("Oljemålningar · Claude Monet", Source.PAINTING, "Claude Monet"),
        Collection("Oljemålningar · Hav och sjöar", Source.PAINTING, "seascape"),
        Collection("Oljemålningar · Kvällsljus", Source.PAINTING, "evening landscape"),
        Collection("Foton 4K · Landskap", Source.PHOTO, "Featured_pictures_of_landscapes"),
        Collection("Foton 4K · Berg", Source.PHOTO, "Featured_pictures_of_mountains"),
        Collection("Foton 4K · Kust och hav", Source.PHOTO, "Featured_pictures_of_coasts"),
        Collection("Foton 4K · Sverige", Source.PHOTO, "Featured_pictures_of_Sweden")
    )

    fun current(prefs: Prefs) = collections[prefs.collection.coerceIn(0, collections.size - 1)]

    // ---------- Paintings (Art Institute of Chicago)
    private fun paintings(query: String): JSONArray {
        val enc = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.artic.edu/api/v1/artworks/search?q=$enc" +
            "&query%5Bterm%5D%5Bis_public_domain%5D=true" +
            "&fields=id,title,artist_title,image_id,classification_title,medium_display,thumbnail" +
            "&limit=100"
        val out = JSONArray()
        val data = JSONObject(Net.text(url)).getJSONArray("data")
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
            val artist = o.optString("artist_title", "").let { if (it == "null") "" else it }
            val title = o.optString("title", "")
            out.put(JSONObject().apply {
                put("url", "https://www.artic.edu/iiif/2/$img/full/1686,/0/default.jpg")
                put("caption", if (artist.isNotEmpty()) "$title · $artist" else title)
            })
        }
        return out
    }

    // ---------- Photos (Wikimedia Commons featured pictures)
    private fun stripHtml(s: String) = s.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()

    private fun photos(category: String): JSONArray {
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
            "&generator=categorymembers&gcmtitle=Category:$category&gcmtype=file&gcmlimit=500" +
            "&prop=imageinfo&iiprop=size%7Curl%7Cextmetadata" +
            "&iiextmetadatafilter=Artist%7CLicenseShortName%7CObjectName&iiurlwidth=3840"
        val out = JSONArray()
        val pages = JSONObject(Net.text(url)).optJSONObject("query")?.optJSONObject("pages") ?: return out
        for (key in pages.keys()) {
            val page = pages.getJSONObject(key)
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val w = info.optDouble("width", 0.0)
            val h = info.optDouble("height", 0.0)
            if (w < 3000 || h <= 0) continue
            val ratio = w / h
            if (ratio < 1.3 || ratio > 2.4) continue
            val mime = page.optString("title").lowercase()
            if (!(mime.endsWith(".jpg") || mime.endsWith(".jpeg"))) continue
            val img = info.optString("thumburl").ifEmpty { info.optString("url") }
            if (img.isEmpty()) continue
            val meta = info.optJSONObject("extmetadata")
            val name = stripHtml(meta?.optJSONObject("ObjectName")?.optString("value") ?: "")
                .ifEmpty { page.optString("title").removePrefix("File:").substringBeforeLast('.') }
            val artist = stripHtml(meta?.optJSONObject("Artist")?.optString("value") ?: "")
            val licence = stripHtml(meta?.optJSONObject("LicenseShortName")?.optString("value") ?: "")
            val credit = listOf(artist.take(40), "Wikimedia Commons", licence).filter { it.isNotEmpty() }.joinToString(" · ")
            out.put(JSONObject().apply {
                put("url", img)
                put("caption", "$name · Foto: $credit")
            })
        }
        return out
    }

    /** Returns a JSON array of {url, caption}; cached per collection. */
    private fun list(prefs: Prefs): JSONArray {
        val idx = prefs.collection.coerceIn(0, collections.size - 1)
        if (prefs.artCacheFor == idx && prefs.artCache.isNotEmpty()) {
            val cached = try { JSONArray(prefs.artCache) } catch (_: Exception) { JSONArray() }
            if (cached.length() > 0) return cached
        }
        val c = collections[idx]
        val out = if (c.source == Source.PHOTO) photos(c.query) else paintings(c.query)
        prefs.artCache = out.toString()
        prefs.artCacheFor = idx
        return out
    }

    /** Downloads the next background into filesDir/painting.jpg. Runs on a background thread. */
    fun next(ctx: Context): Boolean {
        val prefs = Prefs(ctx)
        val arr = list(prefs)
        if (arr.length() == 0) return false
        var pick = Random.nextInt(arr.length())
        if (arr.length() > 1 && pick == prefs.lastIndex) pick = (pick + 1) % arr.length()
        val o = arr.getJSONObject(pick)
        Net.download(o.getString("url"), File(ctx.filesDir, "painting.jpg"))
        prefs.lastIndex = pick
        prefs.paintingCaption = o.optString("caption")
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
