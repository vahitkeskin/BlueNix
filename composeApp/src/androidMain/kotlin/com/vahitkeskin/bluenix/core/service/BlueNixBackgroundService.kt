package com.vahitkeskin.bluenix.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.koin.android.ext.android.inject

class BlueNixBackgroundService : Service() {

    private val chatController: ChatController by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.w("BlueNixDebug", "SERVICE: onCreate çalıştı.")
        startForegroundServiceNotification()

        // SUNUCUYU BAŞLAT
        Log.w("BlueNixDebug", "SERVICE: ChatController.startHosting() tetikleniyor...")
        chatController.startHosting()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "bluenix_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "BlueNix Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BlueNix")
            .setContentText("Mesajlar dinleniyor...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }
}