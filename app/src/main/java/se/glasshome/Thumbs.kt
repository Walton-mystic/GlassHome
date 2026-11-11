package se.glasshome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Small preview pictures for the picture browser: kept in memory and on disk, so scrolling back is instant. */
object Thumbs {
    private val pool = Executors.newFixedThreadPool(4)
    private val mem = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun file(ctx: Context, url: String): File {
        val dir = File(ctx.cacheDir, "thumbs").apply { mkdirs() }
        val h = MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.jpg")
    }

    fun load(iv: ImageView, url: String) {
        iv.tag = url
        mem.get(url)?.let { iv.setImageBitmap(it); return }
        iv.setImageDrawable(null)
        val ctx = iv.context.applicationContext
        pool.execute {
            val bmp = try {
                val f = file(ctx, url)
                if (!f.exists() || f.length() == 0L) Net.download(url, f)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 360) sample *= 2
                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (_: Throwable) { null }
            if (bmp != null) {
                mem.put(url, bmp)
                iv.post { if (iv.tag == url) iv.setImageBitmap(bmp) }
            }
        }
    }
}
