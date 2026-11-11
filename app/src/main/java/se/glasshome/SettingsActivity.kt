package se.glasshome

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private lateinit var painting: ImageView

    private val rotations = listOf(30 to "Var 30:e minut", 60 to "Varje timme", 180 to "Var 3:e timme", 1440 to "En gång om dagen")

    private val idleOptions = listOf(15 to "Efter 15 sekunder", 30 to "Efter 30 sekunder", 60 to "Efter 1 minut", 300 to "Efter 5 minuter", 0 to "Aldrig")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        prefs = Prefs(this)
        painting = findViewById(R.id.painting)
        Ui.showPainting(this, painting)
        findViewById<TextView>(R.id.title).text = "Inställningar"
        findViewById<TextView>(R.id.clock).text = SimpleDateFormat("HH:mm", Locale("sv", "SE")).format(Date())
        list = findViewById(R.id.list)
        build(0)
        Updater.checkInBackground(this, force = true) { build(focusedIndex()) }
    }

    override fun onResume() {
        super.onResume()
        // Back from the picture browser: show the new background and collection name.
        if (::list.isInitialized) { Ui.showPainting(this, painting); build(focusedIndex()) }
    }

    private fun focusedIndex() = (0 until list.childCount).firstOrNull { list.getChildAt(it).hasFocus() } ?: 0

    private fun row(label: String, detail: String? = null, action: () -> Unit) {
        list.addView(Ui.item(this, label, sizeSp = 19f, widthDp = 580f, detail = detail) {
            val i = focusedIndex()
            action()
            build(i)
        })
    }

    private fun build(focus: Int) {
        list.removeAllViews()
        if (Updater.available(this)) {
            row("Installera uppdatering", "1.0.${prefs.updateVersion}") { Updater.install(this) }
        }
        row("Bakgrund", Art.current(prefs).name) {
            startActivity(android.content.Intent(this, GalleryActivity::class.java))
        }
        row("Byt bild nu") { nextPainting() }
        val rot = rotations.firstOrNull { it.first == prefs.rotateMinutes } ?: rotations[1]
        row("Byt bild", rot.second) {
            val i = rotations.indexOf(rot)
            prefs.rotateMinutes = rotations[(i + 1) % rotations.size].first
        }
        val idle = idleOptions.firstOrNull { it.first == prefs.idleSeconds } ?: idleOptions[1]
        row("Dölj allt när fjärren inte används", idle.second) {
            val i = idleOptions.indexOf(idle)
            prefs.idleSeconds = idleOptions[(i + 1) % idleOptions.size].first
        }
        row("Väder", if (prefs.showWeather) "Visas" else "Dolt") { prefs.showWeather = !prefs.showWeather }
        row("Visa hemskärmen när TV:n startar", if (prefs.homeOnWake) "På" else "Av") { prefs.homeOnWake = !prefs.homeOnWake }
        row("TV-inställningar", "Bild, ljud, nätverk") { Apps.openTvSettings(this) }
        row("Sök efter uppdatering") {
            Toast.makeText(this, "Söker…", Toast.LENGTH_SHORT).show()
            Updater.checkInBackground(this, force = true) { found ->
                if (!found) Toast.makeText(this, "Du har senaste versionen", Toast.LENGTH_SHORT).show()
                build(0)
            }
        }
        list.addView(Ui.item(this, "GlassHome ${BuildConfig.VERSION_NAME}", sizeSp = 13f, widthDp = 580f) {}.apply {
            alpha = 0.6f
            // Hidden: hold OK here to open the baby monitor setup.
            isLongClickable = true
            setOnLongClickListener {
                startActivity(android.content.Intent(this@SettingsActivity, CameraSetupActivity::class.java))
                true
            }
        })
        list.getChildAt(focus.coerceIn(0, list.childCount - 1))?.requestFocus()
    }

    private fun nextPainting() {
        Toast.makeText(this, "Hämtar en ny bild…", Toast.LENGTH_SHORT).show()
        Net.io.execute {
            val ok = try { Art.next(applicationContext) } catch (_: Exception) { false }
            runOnUiThread {
                if (ok) Ui.showPainting(this, painting)
                else Toast.makeText(this, "Kunde inte hämta bilden just nu", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
