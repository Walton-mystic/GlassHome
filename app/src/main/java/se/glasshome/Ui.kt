package se.glasshome

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

object Ui {
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun dp(a: Activity, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.resources.displayMetrics).toInt()

    /** A text-only row that shows a faint glass pane only while focused. */
    fun item(
        a: Activity,
        label: String,
        sizeSp: Float = 21f,
        widthDp: Float = 260f,
        icon: Drawable? = null,
        detail: String? = null,
        onClick: () -> Unit
    ): TextView {
        val t = TextView(a)
        t.text = if (detail.isNullOrEmpty()) label else "$label   ·   $detail"
        t.setTextColor(0xFFFFFFFF.toInt())
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        t.typeface = light
        t.alpha = 0.88f
        t.setShadowLayer(8f, 0f, 1f, 0x73000000)
        t.isSingleLine = true
        t.setBackgroundResource(R.drawable.item_bg)
        t.setPadding(dp(a, 18f), dp(a, 9f), dp(a, 18f), dp(a, 9f))
        t.isFocusable = true
        t.isFocusableInTouchMode = true
        t.isClickable = true
        if (icon != null) {
            val s = dp(a, sizeSp * 0.85f)
            icon.setBounds(0, 0, s, s)
            t.setCompoundDrawables(icon, null, null, null)
            t.compoundDrawablePadding = dp(a, 10f)
        }
        val lp = LinearLayout.LayoutParams(dp(a, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(a, 3f)
        t.layoutParams = lp
        t.setOnFocusChangeListener { v, has ->
            val tv = v as TextView
            tv.typeface = if (has) medium else light
            tv.animate().alpha(if (has) 1f else 0.88f)
                .scaleX(if (has) 1.03f else 1f).scaleY(if (has) 1.03f else 1f)
                .setDuration(160).start()
        }
        t.pivotX = 0f
        t.setOnClickListener { onClick() }
        return t
    }

    fun divider(a: Activity): View {
        val v = View(a)
        v.setBackgroundResource(R.drawable.divider)
        val lp = LinearLayout.LayoutParams(dp(a, 60f), dp(a, 1f))
        lp.setMargins(dp(a, 18f), dp(a, 10f), 0, dp(a, 10f))
        v.layoutParams = lp
        return v
    }

    /** Show the cached painting (if any) in an ImageView. */
    fun showPainting(a: Activity, iv: ImageView) {
        val f = File(a.filesDir, "painting.jpg")
        if (f.exists() && f.length() > 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            val screenW = maxOf(a.resources.displayMetrics.widthPixels, 1280)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= screenW) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                inSampleSize = sample
            }
            val bmp = try { BitmapFactory.decodeFile(f.absolutePath, opts) } catch (_: Throwable) { null }
            if (bmp != null) {
                iv.setImageBitmap(bmp)
                return
            }
        }
        iv.setImageResource(R.drawable.fallback_painting)
    }
}
