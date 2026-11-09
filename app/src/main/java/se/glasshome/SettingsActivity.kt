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

    private fun focusedIndex() = (0 until list.childCount).firstOrNull { list.getChildAt(it).hasFocus() } ?: 0

    private fun row(label: String, detail: String? = null, action: () -> Unit) {
        list.addView(Ui.item(this, label, sizeSp = 19f, widthDp = 420f, detail = detail) {
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
        row("Tavlor", Art.collections[prefs.collection.coerceIn(0, Art.collections.size - 1)].name) {
            prefs.collection = (prefs.collection + 1) % Art.collections.size
            nextPainting()
        }
        row("Byt tavla nu") { nextPainting() }
        val rot = rotations.firstOrNull { it.first == prefs.rotateMinutes } ?: rotations[1]
        row("Byt tavla", rot.second) {
            val i = rotations.indexOf(rot)
            prefs.rotateMinutes = rotations[(i + 1) % rotations.size].first
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
        list.addView(Ui.item(this, "GlassHome ${BuildConfig.VERSION_NAME}", sizeSp = 13f, widthDp = 420f) {}.apply { alpha = 0.6f })
        list.getChildAt(focus.coerceIn(0, list.childCount - 1))?.requestFocus()
    }

    private fun nextPainting() {
        Toast.makeText(this, "Hämtar en ny tavla…", Toast.LENGTH_SHORT).show()
        Net.io.execute {
            val ok = try { Art.next(applicationContext) } catch (_: Exception) { false }
            runOnUiThread {
                if (ok) Ui.showPainting(this, painting)
                else Toast.makeText(this, "Kunde inte hämta tavlan just nu", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
