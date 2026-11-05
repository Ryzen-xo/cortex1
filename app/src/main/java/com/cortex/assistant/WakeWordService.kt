package com.cortex.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/** A transparent foreground-service shell. It intentionally does not record audio without an installed Vosk model. */
class WakeWordService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Cortex wake word", NotificationManager.IMPORTANCE_LOW))
        val notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        startForeground(401, notification.setContentTitle("Cortex wake word").setContentText("Recognition is off until a Vosk model is installed.").setSmallIcon(android.R.drawable.ic_btn_speak_now).build())
        stopSelf()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { private const val CHANNEL = "cortex_wake_word" }
}
