package se.glasshome

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import kotlin.random.Random
import org.json.JSONObject as J

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
        Collection("Foton 4K · Sverige", Source.PHOTO, "Featured_pictures_of_Sweden"),
        // Several Commons categories, separated by |. Categories that do not exist simply give no pictures.
        Collection("Foton 4K · Kosovo", Source.PHOTO,
            "Quality_images_of_Kosovo|Featured_pictures_of_Kosovo|Quality_images_of_Pristina|Quality_images_of_Prizren|Nature_of_Kosovo|Mountains_of_Kosovo|Landscapes_of_Kosovo|Rugova_Canyon|Gadime_Cave|Mirusha_waterfalls")
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
                put("thumb", "https://www.artic.edu/iiif/2/$img/full/400,/0/default.jpg")
                put("caption", if (artist.isNotEmpty()) "$title · $artist" else title)
            })
        }
        return out
    }

    // ---------- Photos (Wikimedia Commons featured pictures)
    private fun stripHtml(s: String) = s.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()

    private fun photos(categories: String): JSONArray {
        val out = JSONArray()
        val seen = HashSet<String>()
        for (cat in categories.split('|').map { it.trim() }.filter { it.isNotEmpty() }) {
            val part = try { photosIn(cat) } catch (_: Exception) { JSONArray() }
            for (i in 0 until part.length()) {
                val o = part.getJSONObject(i)
                if (seen.add(o.getString("url"))) out.put(o)
            }
        }
        return out
    }

    /** Commons thumbnail in a standard size: .../3840px-Name.jpg -> .../330px-Name.jpg */
    private fun smaller(url: String) = url.replace(Regex("/\\d+px-"), "/330px-")

    private fun photosIn(category: String): JSONArray {
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
                put("thumb", if (img.contains("/thumb/")) smaller(img) else img)
                put("caption", "$name · Foto: $credit")
            })
        }
        return out
    }

    /**
     * All pictures in one collection as a JSON array of {url, thumb, caption}.
     * Cached in a file per collection for a week, so the picture browser opens quickly.
     */
    fun items(ctx: Context, idx: Int): JSONArray {
        val i = idx.coerceIn(0, collections.size - 1)
        val f = File(ctx.filesDir, "art_$i.json")
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 7L * 24 * 3600_000) {
            val cached = try { JSONArray(f.readText()) } catch (_: Exception) { JSONArray() }
            if (cached.length() > 0) return cached
        }
        val c = collections[i]
        val out = if (c.source == Source.PHOTO) photos(c.query) else paintings(c.query)
        if (out.length() > 0) f.writeText(out.toString())
        else if (f.exists()) return try { JSONArray(f.readText()) } catch (_: Exception) { out }
        return out
    }

    fun thumbOf(o: J): String = o.optString("thumb").ifEmpty { o.optString("url") }

    /** Makes one chosen picture the background (from the picture browser). Background thread. */
    fun choose(ctx: Context, idx: Int, o: J) {
        Net.download(o.getString("url"), File(ctx.filesDir, "painting.jpg"))
        val prefs = Prefs(ctx)
        prefs.collection = idx
        prefs.lastIndex = -1
        prefs.paintingCaption = o.optString("caption")
        prefs.paintingChangedAt = System.currentTimeMillis()
    }

    /** Downloads the next background into filesDir/painting.jpg. Runs on a background thread. */
    fun next(ctx: Context): Boolean {
        val prefs = Prefs(ctx)
        val arr = items(ctx, prefs.collection)
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
