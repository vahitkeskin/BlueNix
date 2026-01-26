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

    // Inject ettiğimiz ChatController (AndroidChatController)
    private val chatController: ChatController by inject()

    // Eğer Client'a da buradan erişebiliyorsan onu da inject et, yoksa Repository üzerinden halledeceğiz.
    // Şimdilik sadece sunucuyu kapatmak bile büyük fark yaratır.

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.w("BlueNixDebug", "SERVICE: onCreate çalıştı.")
        startForegroundServiceNotification()

        Log.w("BlueNixDebug", "SERVICE: ChatController.startHosting() tetikleniyor...")
        chatController.startHosting()
    }

    // --- %100 ÇÖZÜM: ONDESTROY TEMİZLİĞİ ---
    override fun onDestroy() {
        Log.w("BlueNixDebug", "SERVICE: onDestroy çalıştı. Kaynaklar temizleniyor...")

        // Eğer AndroidChatController sınıfına stopHosting eklediysen (Interface'e de eklemen gerekebilir):
        (chatController as? AndroidChatController)?.stopHosting()

        super.onDestroy()
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