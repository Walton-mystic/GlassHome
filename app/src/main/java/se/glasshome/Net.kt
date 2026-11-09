package se.glasshome

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Net {
    val io = Executors.newFixedThreadPool(3)

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "GlassHome/${BuildConfig.VERSION_NAME} (Android TV)")
        c.setRequestProperty("AIC-User-Agent", "GlassHome (personal TV home screen)")
        return c
    }

    fun text(url: String, accept: String = "application/json"): String {
        val c = open(url)
        c.setRequestProperty("Accept", accept)
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun download(url: String, target: File) {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            val tmp = File(target.parentFile, target.name + ".part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            c.disconnect()
        }
    }
}
