package com.vahitkeskin.bluenix.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vahitkeskin.bluenix.R
import org.koin.android.ext.android.inject

class BlueNixBackgroundService : Service() {

    // Koin ile Controller'ı alıyoruz
    private val chatController: ChatController by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()

        // Servis başladığında Bluetooth Server'ı ayağa kaldır
        chatController.startHosting()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "bluenix_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "BlueNix Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BlueNix Aktif")
            .setContentText("Mesajlar dinleniyor...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Varsayılan ikon
            .build()

        startForeground(1, notification)
    }
}