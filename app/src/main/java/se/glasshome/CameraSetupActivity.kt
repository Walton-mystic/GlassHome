package se.glasshome

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Hidden setup for the baby monitor. Everything typed here stays on the TV. */
class CameraSetupActivity : Activity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        prefs = Prefs(this)
        Ui.showPainting(this, findViewById<ImageView>(R.id.painting))
        findViewById<TextView>(R.id.title).text = "Babyvakt"
        val list = findViewById<LinearLayout>(R.id.list)

        val ip = field(list, "IP-adress (t.ex. 192.168.x.x)", prefs.camIp, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val port = field(list, "Port", prefs.camPort, InputType.TYPE_CLASS_NUMBER)
        val user = field(list, "Användarnamn", prefs.camUser, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val pass = field(list, "Lösenord", prefs.camPass, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val path = field(list, "Ström (stream2 = lättare, stream1 = skarpare)", prefs.camPath, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)

        list.addView(Ui.item(this, "Spara", sizeSp = 19f, widthDp = 580f) {
            prefs.camIp = ip.text.toString().trim()
            prefs.camPort = port.text.toString().trim()
            prefs.camUser = user.text.toString().trim()
            prefs.camPass = pass.text.toString()
            prefs.camPath = path.text.toString().trim().ifEmpty { "stream2" }
            Toast.makeText(this, "Sparat", Toast.LENGTH_SHORT).show()
        })
        list.addView(Ui.item(this, "Testa nu", sizeSp = 19f, widthDp = 580f) {
            val svc = KeyService.instance
            if (svc == null) {
                Toast.makeText(this, "Färgknapparna är inte aktiverade än", Toast.LENGTH_LONG).show()
            } else {
                svc.toggle()
            }
        })
        list.addView(Ui.item(this, "Ta bort babyvakten", sizeSp = 19f, widthDp = 580f) {
            KeyService.instance?.hide()
            prefs.camIp = ""; prefs.camUser = ""; prefs.camPass = ""
            ip.setText(""); user.setText(""); pass.setText("")
            Toast.makeText(this, "Borttagen", Toast.LENGTH_SHORT).show()
        })

        val active = KeyService.instance != null
        info(list, if (active) "Färgknapparna är aktiva." else "Färgknapparna är inte aktiva än. Kör aktivera-babyvakt.bat från datorn en gång.")
        info(list, "Röd: visa/dölj  ·  Grön: storlek  ·  Gul: byt hörn  ·  Blå: ljud på/av")
        if (!active) {
            list.addView(Ui.item(this, "Öppna Tillgänglighet", sizeSp = 15f, widthDp = 580f) {
                try { startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
            })
        }
        ip.requestFocus()
    }

    private fun field(list: LinearLayout, hint: String, value: String, type: Int): EditText {
        val label = TextView(this)
        label.text = hint
        label.setTextColor(0xCCFFFFFF.toInt())
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        label.setPadding(Ui.dp(this, 18f), Ui.dp(this, 6f), 0, 0)
        list.addView(label)
        val e = EditText(this)
        e.setText(value)
        e.inputType = type
        e.isSingleLine = true
        e.setTextColor(0xFFFFFFFF.toInt())
        e.setHintTextColor(0x88FFFFFF.toInt())
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        e.setBackgroundResource(R.drawable.item_bg)
        e.setPadding(Ui.dp(this, 18f), Ui.dp(this, 8f), Ui.dp(this, 18f), Ui.dp(this, 8f))
        e.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 580f), ViewGroup.LayoutParams.WRAP_CONTENT)
        list.addView(e)
        return e
    }

    private fun info(list: LinearLayout, text: String) {
        val t = TextView(this)
        t.text = text
        t.setTextColor(0xB3FFFFFF.toInt())
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        t.setPadding(Ui.dp(this, 18f), Ui.dp(this, 12f), 0, 0)
        list.addView(t)
    }
}
