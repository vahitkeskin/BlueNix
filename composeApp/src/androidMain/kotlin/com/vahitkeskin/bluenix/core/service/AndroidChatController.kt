package com.vahitkeskin.bluenix.core.service

// ... (Mevcut importlar aynı)
import android.Manifest
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
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vahitkeskin.bluenix.core.Constants
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class AndroidChatController(
    private val context: Context
) : ChatController, KoinComponent {

    private val repository: ChatRepository by inject()
    private val _remoteTypingState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    override val remoteTypingState: StateFlow<Map<String, Boolean>> =
        _remoteTypingState.asStateFlow()

    private var activeChatAddress: String? = null
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null // Reklamı durdurmak için referans
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val DELIMITER = "|||"
    private val messageBuffers = ConcurrentHashMap<String, StringBuilder>()

    override fun setActiveChat(address: String?) {
        activeChatAddress = address?.uppercase()
    }

    // ...
    // Diğer değişken tanımları (adapter, bluetoothManager vs.) yukarıda aynen kalacak...

    override fun startHosting() {
        // 1. Güvenlik ve Donanım Kontrolü
        if (adapter == null || !adapter.isEnabled) return
        if (!hasBluetoothPermission()) return

        // 2. Önce Temizlik (Zombi Sunucu Önlemi)
        stopHosting()

        // 3. Callback Tanımlama
        val callback = object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                // Servis başarıyla eklendiyse, artık cihazı görünür yap (Reklamı başlat)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    startAdvertising()
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

                // Onay gönder
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }

                val incomingBytes = value ?: return
                val incomingData = String(incomingBytes, Charsets.UTF_8)
                val address = device.address

                // Sinyal Kontrolü
                if (incomingData == "SIG_TYP_START") {
                    _remoteTypingState.update { it + (address to true) }
                    return
                }
                if (incomingData == "SIG_TYP_STOP") {
                    _remoteTypingState.update { it + (address to false) }
                    return
                }

                // Mesaj Birleştirme (Packet Reassembly)
                val buffer = messageBuffers.getOrPut(address) { StringBuilder() }
                buffer.append(incomingData)
                val fullMessage = buffer.toString()

                if (fullMessage.contains(DELIMITER)) {
                    processFullMessage(device, fullMessage)
                    buffer.clear()
                } else {
                    // Güvenlik: Tampon taşmasını önle
                    if (buffer.length > 5000) buffer.clear()
                }
            }
        }

        // 4. Sunucuyu Başlat
        try {
            gattServer = bluetoothManager.openGattServer(context, callback)
            addServicesToGattServer()
        } catch (e: Exception) {
            Log.e("ChatController", "Server başlatılamadı: ${e.message}")
        }
    }

    // --- REKLAM FONKSİYONU (AYRI OLMALI) ---
    private fun startAdvertising() {
        if (!hasBluetoothPermission()) return
        val advertiser = adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(UUID.fromString(Constants.CHAT_SERVICE_UUID)))
            .build()

        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).build()

        // Callback'i değişkene atıyoruz ki stopHosting çağrıldığında durdurabilelim
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i("ChatController", "✅ Reklam (Advertising) başladı.")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("ChatController", "❌ Reklam hatası: $errorCode")
            }
        }

        try {
            advertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
        } catch (e: Exception) {
            Log.e("ChatController", "Reklam başlatılamadı: ${e.message}")
        }
    }

    // --- %100 ÇÖZÜM: TEMİZLİK FONKSİYONU ---
    fun stopHosting() {
        if (!hasBluetoothPermission()) return

        try {
            // 1. Reklamı Durdur
            advertiseCallback?.let {
                adapter.bluetoothLeAdvertiser?.stopAdvertising(it)
                advertiseCallback = null
            }

            // 2. Sunucuyu Kapat
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
            Log.i("ChatController", "🛑 Sunucu ve Reklam durduruldu.")
        } catch (e: Exception) {
            Log.e("ChatController", "Durdurma hatası: ${e.message}")
        }
    }

    private fun processFullMessage(device: BluetoothDevice, message: String) {
        scope.launch {
            var senderName = "Bilinmeyen (${device.address.takeLast(4)})"
            var realContent = message

            if (message.contains(DELIMITER)) {
                val parts = message.split(DELIMITER, limit = 2)
                if (parts.size == 2) {
                    senderName = parts[0]
                    realContent = parts[1]
                }
            }
            repository.receiveMessage(device.address, senderName, realContent)
            if (activeChatAddress != device.address.uppercase()) {
                sendNotification(senderName, realContent, device.address)
            }
        }
    }

    private fun addServicesToGattServer() {
        if (gattServer == null) return
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
    }

    private fun sendNotification(title: String, message: String, address: String) {
        // Android 13+ Bildirim İzni Kontrolü
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("AndroidChatController", "⚠️ Bildirim izni yok.")
                return
            }
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "chat_msg_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "BlueNix Mesajları",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        val intent = try {
            Intent(context, Class.forName("com.vahitkeskin.bluenix.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("targetDeviceAddress", address)
            }
        } catch (e: Exception) {
            null
        }
        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(
                context,
                address.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(address.hashCode(), notification)
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}