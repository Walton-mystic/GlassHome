package se.glasshome

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        Ui.showPainting(this, findViewById<ImageView>(R.id.painting))
        findViewById<TextView>(R.id.title).text = "Alla appar"
        findViewById<TextView>(R.id.clock).text = SimpleDateFormat("HH:mm", Locale("sv", "SE")).format(Date())
        val list = findViewById<LinearLayout>(R.id.list)
        for (app in Apps.all(this)) {
            list.addView(Ui.item(this, app.label, sizeSp = 19f, widthDp = 400f) {
                Apps.launch(this, app.pkg)
                finish()
            })
        }
        if (list.childCount > 0) list.getChildAt(0).requestFocus()
    }
}
