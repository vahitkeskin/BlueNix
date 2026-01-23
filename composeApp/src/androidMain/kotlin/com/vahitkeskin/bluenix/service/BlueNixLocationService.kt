package com.vahitkeskin.bluenix.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vahitkeskin.bluenix.R
import com.vahitkeskin.bluenix.core.service.LocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class BlueNixLocationService : Service() {

    private val locationService: LocationService by inject() // KMP servisini çağırıyoruz
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        startTracking()
    }

    private fun startTracking() {
        serviceScope.launch {
            // BURASI UI YOKKEN ÇALIŞAN YER
            locationService.getLocationUpdates().collect { location ->
                // 1. Konumu aldın.
                // 2. BluetoothManager üzerinden diğer cihaza gönder!
                println("Arka Plan Konumu: ${location.latitude}, ${location.longitude}")

                // Örn: bluetoothService.sendData(location.toJson())
            }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "bluenix_location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "BlueNix Konum Servisi",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BlueNix Çalışıyor")
            .setContentText("Konum ve Bluetooth servisleri aktif.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        // 🛡️ GÜVENLİK GÜNCELLEMESİ: Try-Catch Bloğu
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 14+ burada izin kontrolü yapar, yoksa Exception fırlatır
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            // İzin yoksa çökme, servisi durdur
            println("⚠️ Servis başlatılamadı (İzin hatası): ${e.localizedMessage}")
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}