package se.glasshome

import android.accessibilityservice.AccessibilityService
import android.graphics.Outline
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource

/**
 * Hidden baby monitor: the colour buttons on the remote control a small video window
 * that floats over whatever is on the TV.
 *  red = show/hide, green = small/large, yellow = move to next corner, blue = sound on/off
 */
class KeyService : AccessibilityService() {
    private val ui = Handler(Looper.getMainLooper())
    private var card: FrameLayout? = null
    private var status: TextView? = null
    private var player: ExoPlayer? = null
    private var muted = true

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        hide()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val prefs = Prefs(this)
        if (!prefs.camConfigured) return false
        val down = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        when (event.keyCode) {
            KeyEvent.KEYCODE_PROG_RED -> {
                if (down) toggle()
                return true
            }
            KeyEvent.KEYCODE_PROG_GREEN -> {
                if (card == null) return false
                if (down) { prefs.camLarge = !prefs.camLarge; relayout() }
                return true
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                if (card == null) return false
                if (down) { prefs.camCorner = (prefs.camCorner + 1) % 4; relayout() }
                return true
            }
            KeyEvent.KEYCODE_PROG_BLUE -> {
                if (card == null) return false
                if (down) {
                    muted = !muted
                    player?.volume = if (muted) 0f else 1f
                    flash(if (muted) "Ljud av" else "Ljud på")
                }
                return true
            }
        }
        return false
    }

    fun toggle() { if (card == null) show() else hide() }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun params(): WindowManager.LayoutParams {
        val prefs = Prefs(this)
        val w = dp(if (prefs.camLarge) 520f else 300f)
        val h = w * 9 / 16
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = when (prefs.camCorner) {
            1 -> Gravity.TOP or Gravity.END
            2 -> Gravity.BOTTOM or Gravity.END
            3 -> Gravity.BOTTOM or Gravity.START
            else -> Gravity.TOP or Gravity.START
        }
        lp.x = dp(28f)
        lp.y = dp(24f)
        return lp
    }

    private fun show() {
        val wm = getSystemService(WindowManager::class.java)
        val radius = dp(14f).toFloat()
        val frame = FrameLayout(this)
        frame.setBackgroundColor(0xFF000000.toInt())
        frame.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        frame.clipToOutline = true
        frame.elevation = dp(8f).toFloat()

        val texture = TextureView(this)
        frame.addView(texture, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val label = TextView(this)
        label.setTextColor(0xFFFFFFFF.toInt())
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        label.setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
        label.text = "Ansluter…"
        frame.addView(label, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        try { wm.addView(frame, params()) } catch (_: Exception) { return }
        card = frame
        status = label
        startPlayer(texture)
    }

    private fun startPlayer(texture: TextureView) {
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 2000, 250, 500)
            .build()
        // Use the TV's software decoders for the small window, so the hardware decoder
        // stays free for whatever is playing full screen (GlassTV, Netflix, live TV …).
        val softwareFirst = MediaCodecSelector { mime, secure, tunneling ->
            val all = MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
            val sw = all.filter {
                it.softwareOnly || it.name.startsWith("c2.android.") || it.name.startsWith("OMX.google.")
            }
            sw + all.filter { it !in sw }
        }
        val renderers = DefaultRenderersFactory(this)
            .setMediaCodecSelector(softwareFirst)
            .setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(this, renderers).setLoadControl(load).build()
        p.setVideoTextureView(texture)
        p.volume = if (muted) 0f else 1f
        p.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                status?.visibility = View.GONE
            }
            override fun onPlayerError(error: PlaybackException) {
                status?.visibility = View.VISIBLE
                status?.text = "Babyvakten svarar inte (${error.errorCodeName}) – försöker igen…"
                ui.postDelayed({ if (player === p) prepare(p) }, 4000)
            }
        })
        player = p
        prepare(p)
    }

    private fun prepare(p: ExoPlayer) {
        val source = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(MediaItem.fromUri(Uri.parse(Prefs(this).camUrl())))
        p.setMediaSource(source)
        p.playWhenReady = true
        p.prepare()
    }

    private fun relayout() {
        val c = card ?: return
        try { getSystemService(WindowManager::class.java).updateViewLayout(c, params()) } catch (_: Exception) {}
    }

    private fun flash(text: String) {
        val s = status ?: return
        s.text = text
        s.visibility = View.VISIBLE
        ui.postDelayed({ if (player?.isPlaying == true) s.visibility = View.GONE }, 1500)
    }

    fun hide() {
        ui.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        card?.let { try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: Exception) {} }
        card = null
        status = null
    }

    companion object {
        var instance: KeyService? = null
    }
}
