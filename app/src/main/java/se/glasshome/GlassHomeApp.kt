package se.glasshome

import android.app.Application

class GlassHomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try { WakeService.start(this) } catch (_: Exception) {}
    }
}
