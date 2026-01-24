package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vahitkeskin.bluenix.core.Constants
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidChatController(
    private val context: Context,
    private val repository: ChatRepository
) : ChatController {

    private val _isRemoteTyping = MutableStateFlow(false)
    override val isRemoteTyping: StateFlow<Boolean> = _isRemoteTyping.asStateFlow()

    private val _typingDeviceAddress = MutableStateFlow<String?>(null)
    override val typingDeviceAddress: StateFlow<String?> = _typingDeviceAddress.asStateFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    override fun startHosting() {
        if (gattServer != null) {
            Log.d("BlueNixDebug", "⚠️ Server zaten açık.")
            // Yine de reklamı tetikleyelim, belki durmuştur.
            startAdvertising()
            return
        }

        Log.w("BlueNixDebug", ">>> [ALICI] GATT SERVER BAŞLATILIYOR >>>")

        val callback = object : BluetoothGattServerCallback() {
            // ... (Callback içeriği aynı kalsın) ...
        }

        gattServer = bluetoothManager.openGattServer(context, callback)

        // 1. Önce Servisleri Ekle
        val success = setupServices()

        // 2. Servis eklendiyse Yayını Başlat
        if (success) {
            startAdvertising()
        } else {
            Log.e("BlueNixDebug", "❌ Kritik Hata: Servis eklenemediği için yayın başlatılmadı.")
        }
    }

    private fun setupServices(): Boolean {
        val serviceUUID = UUID.fromString(Constants.CHAT_SERVICE_UUID)
        val charUUID = UUID.fromString(Constants.CHAT_CHARACTERISTIC_UUID)

        // Daha önce eklenmiş mi kontrol et
        val existingService = gattServer?.getService(serviceUUID)
        if (existingService != null) {
            Log.d("BlueNixDebug", "♻️ Servis zaten mevcut, tekrar eklenmiyor.")
            return true
        }

        val service = BluetoothGattService(
            serviceUUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val characteristic = BluetoothGattCharacteristic(
            charUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)

        val result = gattServer?.addService(service) ?: false

        if (result) {
            Log.i("BlueNixDebug", "✅ Servis GATT Server'a eklendi: $serviceUUID")
        } else {
            Log.e("BlueNixDebug", "❌ Servis ekleme başarısız oldu!")
        }

        return result
    }


    private fun startAdvertising() {
        if (advertiser == null) {
            Log.e("BlueNixDebug", "❌ HATA: Advertising desteklenmiyor.")
            return
        }

        Log.d("BlueNixDebug", "📡 Yayın başlatma isteği gönderiliyor...")

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // --- %100 ÇÖZÜM BURASI ---
        // setIncludeDeviceName(FALSE) yaptık.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // <--- İsim Kapatıldı (Veri Tasarrufu)
            .setIncludeTxPowerLevel(false) // <--- Güç Seviyesi Kapatıldı (Veri Tasarrufu)
            .addServiceUuid(ParcelUuid(UUID.fromString(Constants.CHAT_SERVICE_UUID)))
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.w("BlueNixDebug", "✅✅✅ YAYIN (ADVERTISING) BAŞLADI!")
            }
            override fun onStartFailure(errorCode: Int) {
                val errorMsg = when(errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Veri çok büyük (Data Too Large)"
                    else -> "Hata Kodu: $errorCode"
                }
                Log.e("BlueNixDebug", "❌❌❌ YAYIN BAŞLATILAMADI: $errorMsg")
            }
        }

        advertiser.startAdvertising(settings, data, callback)
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "bluenix_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Mesajlar", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        val intent = try {
            Intent(context, Class.forName("com.vahitkeskin.bluenix.MainActivity"))
        } catch (e: Exception) { null }
        val pendingIntent = if (intent != null) PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE) else null

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}