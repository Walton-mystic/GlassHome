package se.glasshome

import android.app.Activity
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * Picture browser: the collections on the left, a scrolling list of previews on the right,
 * over a blurred copy of the current background. OK on a preview makes it the background.
 */
class GalleryActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var bg: ImageView
    private lateinit var cols: LinearLayout
    private lateinit var grid: LinearLayout
    private lateinit var gridScroll: ScrollView
    private lateinit var caption: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var shown = -1
    private var token = 0
    private var items = JSONArray()

    private fun dp(v: Float) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val root = FrameLayout(this)
        bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))
        if (Build.VERSION.SDK_INT >= 31) bg.setRenderEffect(RenderEffect.createBlurEffect(dp(18f).toFloat(), dp(18f).toFloat(), Shader.TileMode.CLAMP))
        root.addView(View(this).apply { setBackgroundColor(if (Build.VERSION.SDK_INT >= 31) 0x8C0A0D16.toInt() else 0xCC0A0D16.toInt()) }, FrameLayout.LayoutParams(-1, -1))
        Ui.showPainting(this, bg)

        root.addView(TextView(this).apply {
            text = "Bakgrund"; setTextColor(-1); setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
            setShadowLayer(8f, 0f, 1f, 0x73000000)
        }, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = dp(54f); topMargin = dp(44f) })

        // Left: the collections
        val leftScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(30f)) }
        cols = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6f), 0, dp(6f)) }
        leftScroll.addView(cols)
        root.addView(leftScroll, FrameLayout.LayoutParams(dp(330f), -1).apply { leftMargin = dp(36f); topMargin = dp(110f); bottomMargin = dp(30f) })

        // Right: scrolling list of previews
        gridScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(40f)); clipToPadding = false }
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14f), dp(12f), dp(14f), dp(40f)); clipChildren = false; clipToPadding = false }
        gridScroll.addView(grid)
        root.addView(gridScroll, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = dp(380f); topMargin = dp(100f); rightMargin = dp(30f); bottomMargin = dp(60f) })

        caption = TextView(this).apply {
            setTextColor(0xCCFFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END; setShadowLayer(8f, 0f, 1f, 0x73000000)
        }
        root.addView(caption, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(394f); rightMargin = dp(40f); bottomMargin = dp(24f) })

        setContentView(root)
        buildCols()
        showCollection(prefs.collection.coerceIn(0, Art.collections.size - 1))
        cols.getChildAt(prefs.collection.coerceIn(0, cols.childCount - 1))?.requestFocus()
    }

    private fun buildCols() {
        cols.removeAllViews()
        Art.collections.forEachIndexed { i, c ->
            val item = Ui.item(this, c.name, sizeSp = 17f, widthDp = 320f, detail = if (i == prefs.collection) "vald" else null) {
                showCollection(i)
                ui.postDelayed({ grid.getChildAt(0)?.let { firstThumb(it)?.requestFocus() } }, 150)
            }
            // Moving through the list shows that collection's pictures after a short pause.
            val old = item.onFocusChangeListener
            item.setOnFocusChangeListener { v, has ->
                old?.onFocusChange(v, has)
                if (has) { ui.removeCallbacksAndMessages("col"); ui.postAtTime({ showCollection(i) }, "col", android.os.SystemClock.uptimeMillis() + 300) }
            }
            cols.addView(item)
        }
    }

    private fun firstThumb(row: View): View? = (row as? LinearLayout)?.getChildAt(0)

    private fun message(text: String) {
        grid.removeAllViews()
        grid.addView(TextView(this).apply { this.text = text; setTextColor(0xB3FFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); setPadding(dp(8f), dp(8f), 0, 0) })
        caption.text = ""
    }

    private fun showCollection(i: Int) {
        if (i == shown) return
        shown = i
        val t = ++token
        message("Hämtar bilder …")
        Net.io.execute {
            val arr = try { Art.items(applicationContext, i) } catch (_: Exception) { JSONArray() }
            runOnUiThread {
                if (t != token) return@runOnUiThread
                items = arr
                if (arr.length() == 0) message("Kunde inte hämta bilderna just nu. Kontrollera nätverket och försök igen.")
                else buildGrid(i, arr)
            }
        }
    }

    private fun buildGrid(idx: Int, arr: JSONArray) {
        grid.removeAllViews()
        gridScroll.scrollTo(0, 0)
        val perRow = 3
        val n = minOf(arr.length(), 90)
        val w = dp(160f); val h = dp(90f); val gap = dp(14f)
        var row: LinearLayout? = null
        for (k in 0 until n) {
            if (k % perRow == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
                grid.addView(row, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = gap })
            }
            val o = arr.getJSONObject(k)
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.thumb_bg)
                setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
                isFocusable = true; isFocusableInTouchMode = true; isClickable = true
                contentDescription = o.optString("caption")
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height, dp(10f).toFloat()) }
                }
                clipToOutline = true
                setOnFocusChangeListener { v, has ->
                    v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).translationZ(if (has) 8f else 0f).setDuration(150).start()
                    if (has) caption.text = o.optString("caption")
                }
                setOnClickListener { choose(idx, o) }
            }
            row!!.addView(iv, LinearLayout.LayoutParams(w, h).apply { rightMargin = gap })
            Thumbs.load(iv, Art.thumbOf(o))
        }
        caption.text = "${Art.collections[idx].name} · $n bilder"
    }

    private fun choose(idx: Int, o: JSONObject) {
        Toast.makeText(this, "Byter bakgrund …", Toast.LENGTH_SHORT).show()
        Net.io.execute {
            val ok = try { Art.choose(applicationContext, idx, o); true } catch (_: Exception) { false }
            runOnUiThread {
                if (ok) {
                    Ui.showPainting(this, bg)
                    val f = currentFocus
                    buildCols()
                    f?.requestFocus()
                    Toast.makeText(this, "Bakgrunden är bytt", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "Kunde inte hämta bilden just nu", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
