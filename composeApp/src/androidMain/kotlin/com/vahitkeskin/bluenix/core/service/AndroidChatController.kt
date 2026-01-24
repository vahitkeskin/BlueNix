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

    // --- State Yönetimi ---
    private val _isRemoteTyping = MutableStateFlow(false)
    override val isRemoteTyping: StateFlow<Boolean> = _isRemoteTyping.asStateFlow()

    private val _typingDeviceAddress = MutableStateFlow<String?>(null)
    override val typingDeviceAddress: StateFlow<String?> = _typingDeviceAddress.asStateFlow()

    // --- Bluetooth Bileşenleri ---
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser

    // --- KRİTİK: Yaşam Döngüsü Bağımsız Scope ---
    // Bu scope, bir hata olsa bile diğer işlemleri durdurmaz (SupervisorJob)
    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startHosting() {
        if (gattServer != null) {
            Log.w("BlueNixChat", "Server zaten açık, tekrar başlatılmıyor.")
            return
        }

        val callback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                super.onConnectionStateChange(device, status, newState)
                val stateStr = if (newState == BluetoothProfile.STATE_CONNECTED) "BAĞLANDI" else "KOPTU"
                Log.d("BlueNixChat", "Server Bağlantı Durumu: $stateStr - ${device.address}")

                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _typingDeviceAddress.value = null
                    _isRemoteTyping.value = false
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

                // 1. Karşı tarafa "Tamam, aldım" sinyali gönder (Gecikirse timeout olur)
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }

                // 2. Gelen veriyi işle
                val incomingBytes = value ?: return
                val incomingData = String(incomingBytes, Charsets.UTF_8)

                // Log ile teyit et
                Log.i("BlueNixChat", "📥 HAM VERİ GELDİ: $incomingData [Cihaz: ${device.address}]")

                when (incomingData) {
                    "SIG_TYP_START" -> {
                        _typingDeviceAddress.value = device.address
                        _isRemoteTyping.value = true
                    }
                    "SIG_TYP_STOP" -> {
                        if (_typingDeviceAddress.value == device.address) {
                            _typingDeviceAddress.value = null
                            _isRemoteTyping.value = false
                        }
                    }
                    else -> {
                        // --- GERÇEK MESAJ ---
                        _typingDeviceAddress.value = null
                        _isRemoteTyping.value = false

                        // Veritabanı işlemini güvenli scope içinde yap
                        controllerScope.launch {
                            try {
                                // Cihaz adı bazen null gelebilir, garantiye al
                                val safeName = if (device.name.isNullOrBlank()) "Cihaz ${device.address.takeLast(4)}" else device.name

                                Log.d("BlueNixChat", "💾 DB'ye Yazılıyor -> Gönderen: $safeName, Mesaj: $incomingData")

                                repository.receiveMessage(
                                    address = device.address,
                                    name = safeName,
                                    text = incomingData
                                )

                                Log.d("BlueNixChat", "✅ DB Kayıt Başarılı!")
                            } catch (e: Exception) {
                                Log.e("BlueNixChat", "❌ DB Kayıt Hatası: ${e.message}", e)
                            }
                        }

                        // Bildirim At
                        val safeName = device.name ?: "Yeni Mesaj"
                        sendNotification(safeName, incomingData)
                    }
                }
            }
        }

        gattServer = bluetoothManager.openGattServer(context, callback)
        setupServices()
        startAdvertising()
    }

    private fun setupServices() {
        val service = BluetoothGattService(
            UUID.fromString(Constants.CHAT_SERVICE_UUID),
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val characteristic = BluetoothGattCharacteristic(
            UUID.fromString(Constants.CHAT_CHARACTERISTIC_UUID),
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)
        Log.i("BlueNixChat", "Hizmetler Kuruldu: ${Constants.CHAT_SERVICE_UUID}")
    }

    private fun startAdvertising() {
        if (advertiser == null) {
            Log.e("BlueNixChat", "Bu cihaz Advertising desteklemiyor!")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // Cihaz adını yayına ekle
            .addServiceUuid(ParcelUuid(UUID.fromString(Constants.CHAT_SERVICE_UUID)))
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i("BlueNixChat", "📡 YAYIN BAŞLADI (Advertising)")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("BlueNixChat", "📡 YAYIN BAŞARISIZ Hata Kodu: $errorCode")
            }
        }

        advertiser.startAdvertising(settings, data, callback)
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "bluenix_chat_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Mesajlar", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = try {
            Intent(context, Class.forName("com.vahitkeskin.bluenix.MainActivity"))
        } catch (e: ClassNotFoundException) { null }

        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else null

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