package se.glasshome

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val sv = Locale("sv", "SE")
    private val timeFmt = SimpleDateFormat("HH:mm", sv)
    private val dateFmt = SimpleDateFormat("EEEE d MMMM", sv)
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var painting: ImageView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var menu: LinearLayout
    private lateinit var caption: TextView
    private lateinit var weather: LinearLayout
    private lateinit var weatherNow: TextView
    private lateinit var weatherDays: LinearLayout
    private lateinit var prefs: Prefs
    private var loadingPainting = false
    private var hidden = false
    private val fadeViews by lazy {
        listOf<View>(menu, findViewById(R.id.clockBox), weather, caption)
    }
    private val hideRunnable = Runnable { fadeOut() }

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            ui.postDelayed(this, 1000L * (60 - (System.currentTimeMillis() / 1000) % 60))
        }
    }
    private val slow = object : Runnable {
        override fun run() {
            maybeNextPainting(false)
            refreshWeather(false)
            Updater.checkInBackground(this@MainActivity) { buildMenu(keepFocus = true) }
            ui.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        painting = findViewById(R.id.painting)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        menu = findViewById(R.id.menu)
        caption = findViewById(R.id.caption)
        weather = findViewById(R.id.weather)
        weatherNow = findViewById(R.id.weatherNow)
        weatherDays = findViewById(R.id.weatherDays)
        Ui.showPainting(this, painting)
        caption.text = prefs.paintingCaption
        WakeService.start(this)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        buildMenu(keepFocus = false)
        Ui.showPainting(this, painting)
        caption.text = prefs.paintingCaption
        renderWeatherFromCache()
        ui.removeCallbacks(tick); ui.post(tick)
        ui.removeCallbacks(slow); ui.post(slow)
        fadeIn(immediate = true)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
        ui.removeCallbacks(slow)
        ui.removeCallbacks(hideRunnable)
    }

    /** Restart the countdown that fades everything away when the remote is left alone. */
    private fun scheduleHide() {
        ui.removeCallbacks(hideRunnable)
        val secs = prefs.idleSeconds
        if (secs > 0) ui.postDelayed(hideRunnable, secs * 1000L)
    }

    private fun fadeOut() {
        hidden = true
        for (v in fadeViews) v.animate().alpha(0f).setDuration(1200).start()
    }

    private fun fadeIn(immediate: Boolean = false) {
        hidden = false
        for (v in fadeViews) {
            v.animate().cancel()
            if (immediate) v.alpha = 1f else v.animate().alpha(1f).setDuration(350).start()
        }
        scheduleHide()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home pressed while already home: jump back to the top of the list.
        if (menu.childCount > 0) menu.getChildAt(0).requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // This is the home screen; there is nothing to go back to.
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hidden) {
            // First press only wakes the screen; it doesn't move or open anything.
            if (event.action == KeyEvent.ACTION_DOWN) fadeIn()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) scheduleHide()
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.dispatchKeyEvent(event)
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun updateClock() {
        val now = Date()
        clock.text = timeFmt.format(now)
        date.text = dateFmt.format(now)
    }

    private fun buildMenu(keepFocus: Boolean) {
        val focusedIndex = if (keepFocus) (0 until menu.childCount).firstOrNull { menu.getChildAt(it).hasFocus() } ?: 0 else 0
        menu.removeAllViews()
        val installed = Apps.all(this)
        for ((label, pkgs, labels) in Apps.favorites) {
            val pkg = Apps.resolve(this, pkgs, labels, installed) ?: continue
            menu.addView(Ui.item(this, label) { Apps.launch(this, pkg) })
        }
        menu.addView(Ui.divider(this))
        menu.addView(Ui.item(this, "Alla appar", sizeSp = 17f) {
            startActivity(Intent(this, AppsActivity::class.java))
        })
        menu.addView(Ui.item(this, "Play Butik", sizeSp = 17f) { Apps.openPlayStore(this) })
        val upd = if (Updater.available(this)) "uppdatering finns" else null
        menu.addView(Ui.item(this, "Inställningar", sizeSp = 17f, detail = upd) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        val target = menu.getChildAt(focusedIndex.coerceAtMost(menu.childCount - 1))
        val focusTarget = if (target != null && target.isFocusable) target else menu.getChildAt(0)
        focusTarget?.requestFocus()
    }

    private fun maybeNextPainting(force: Boolean) {
        if (loadingPainting) return
        if (!force && !Art.isDue(this)) return
        loadingPainting = true
        Net.io.execute {
            val ok = try { Art.next(applicationContext) } catch (_: Exception) { false }
            ui.post {
                loadingPainting = false
                if (ok && !isFinishing) {
                    painting.animate().alpha(0f).setDuration(600).withEndAction {
                        Ui.showPainting(this, painting)
                        caption.text = prefs.paintingCaption
                        painting.animate().alpha(1f).setDuration(900).start()
                    }.start()
                }
            }
        }
    }

    private fun refreshWeather(force: Boolean) {
        if (!prefs.showWeather) { weather.visibility = View.INVISIBLE; return }
        if (!force && System.currentTimeMillis() - prefs.weatherAt < 30 * 60_000L && prefs.weatherCache.isNotEmpty()) return
        Net.io.execute {
            try {
                val json = Weather.fetch()
                if (Weather.parse(json) != null) {
                    prefs.weatherCache = json
                    prefs.weatherAt = System.currentTimeMillis()
                    ui.post { renderWeatherFromCache() }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun renderWeatherFromCache() {
        if (!prefs.showWeather) { weather.visibility = View.INVISIBLE; return }
        val f = Weather.parse(prefs.weatherCache) ?: return
        weatherNow.text = "${Weather.PLACE} · ${f.nowTemp}° · ${Weather.text(f.nowCode)}"
        weatherDays.removeAllViews()
        val dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEE", sv)
        val today = LocalDate.now()
        for (d in f.days.take(5)) {
            val date = try { LocalDate.parse(d.date) } catch (_: Exception) { null }
            val name = when {
                date == null -> ""
                date == today -> "Idag"
                else -> date.format(dayFmt).trimEnd('.').replaceFirstChar { it.uppercase() }
            }
            weatherDays.addView(dayColumn(name, d, date == today))
        }
        weather.visibility = View.VISIBLE
    }

    private fun dayColumn(name: String, d: Weather.Day, today: Boolean): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 46f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = Ui.dp(this@MainActivity, 7f)
        }
        val n = TextView(this)
        n.text = name
        n.setTextColor(0xFFFFFFFF.toInt())
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        n.typeface = android.graphics.Typeface.create(if (today) "sans-serif-medium" else "sans-serif-light", 0)
        n.alpha = 0.9f
        n.setShadowLayer(8f, 0f, 1f, 0x73000000)
        val ic = ImageView(this)
        ic.setImageResource(Weather.icon(d.code))
        ic.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 20f), Ui.dp(this, 20f)).apply {
            topMargin = Ui.dp(this@MainActivity, 5f); bottomMargin = Ui.dp(this@MainActivity, 5f)
        }
        val t = TextView(this)
        t.text = android.text.Html.fromHtml("${d.max}°<font color='#A6FFFFFF'> ${d.min}°</font>", 0)
        t.setTextColor(0xFFFFFFFF.toInt())
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        t.typeface = android.graphics.Typeface.create("sans-serif-light", 0)
        t.setShadowLayer(8f, 0f, 1f, 0x73000000)
        col.addView(n); col.addView(ic); col.addView(t)
        return col
    }

    fun forceNextPainting() = maybeNextPainting(true)
}
