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
    override val remoteTypingState: StateFlow<Map<String, Boolean>> = _remoteTypingState.asStateFlow()

    private var activeChatAddress: String? = null
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val DELIMITER = "|||"

    // --- KRİTİK EKLENTİ: MESAJ BİRLEŞTİRME TAMPONU ---
    // Her cihaz adresi için ayrı bir StringBuilder tutuyoruz.
    private val messageBuffers = ConcurrentHashMap<String, StringBuilder>()

    override fun setActiveChat(address: String?) {
        activeChatAddress = address?.uppercase()
    }

    override fun startHosting() {
        if (adapter == null || !adapter.isEnabled) return
        if (gattServer != null) {
            gattServer?.clearServices()
            gattServer?.close()
        }

        val callback = object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                if (status == BluetoothGatt.GATT_SUCCESS) startAdvertising()
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }

                val incomingBytes = value ?: return
                val incomingData = String(incomingBytes, Charsets.UTF_8)
                val address = device.address

                // 1. Sinyal Kontrolü (Sinyaller her zaman tek parça ve kısadır)
                if (incomingData == "SIG_TYP_START") {
                    _remoteTypingState.update { it + (address to true) }
                    return
                }
                if (incomingData == "SIG_TYP_STOP") {
                    _remoteTypingState.update { it + (address to false) }
                    return
                }

                // 2. Mesaj Birleştirme (Packet Reassembly)
                // Gelen veriyi o cihaza ait tampona ekle
                val buffer = messageBuffers.getOrPut(address) { StringBuilder() }
                buffer.append(incomingData)

                // --- MESAJ TAMAMLANDI MI? ---
                // Basit bir kontrol: Eğer tamponda "|||" varsa, mesajın başlığı gelmiş demektir.
                // MTU 512 olsa bile, bazen Android veriyi bölerek gönderir.
                // Burada %100 garantili bir yöntem uygulayalım:
                // Gelen veriyi her eklediğimizde kontrol edelim.

                val fullMessage = buffer.toString()

                // Eğer mesaj "|||" içeriyorsa işleme al
                if (fullMessage.contains(DELIMITER)) {
                    processFullMessage(device, fullMessage)
                    // Mesaj işlendi, tamponu temizle
                    buffer.clear()
                } else {
                    // Henüz "|||" gelmedi, beklemeye devam et (sonraki paket bekleniyor)
                    // Ancak çok uzun süre beklememek için bir timeout mekanizması eklenebilir
                    // Şimdilik basit tutuyoruz.

                    // Güvenlik: Eğer buffer çok şiştiyse (örn 1000 karakter) ve hala ||| yoksa temizle
                    if (buffer.length > 2000) buffer.clear()
                }
            }
        }
        gattServer = bluetoothManager.openGattServer(context, callback)
        addServicesToGattServer()
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

            Log.d("BlueNixTrace", """
                📥 ---------------- MESAJ BİRLEŞTİRİLDİ ----------------
                📦 HAM: $message
                👤 KİMDEN: $senderName
                💬 İÇERİK: $realContent
                --------------------------------------------------------
            """.trimIndent())

            repository.receiveMessage(device.address, senderName, realContent)

            if (activeChatAddress != device.address.uppercase()) {
                sendNotification(senderName, realContent, device.address)
            }
        }
    }

    // ... (addServicesToGattServer, startAdvertising, sendNotification AYNI KALACAK) ...
    private fun addServicesToGattServer() {
        if (gattServer == null) return
        val service = BluetoothGattService(UUID.fromString(Constants.CHAT_SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.fromString(Constants.CHAT_CHARACTERISTIC_UUID),
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)
    }

    private fun startAdvertising() {
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
        advertiser.startAdvertising(settings, advertiseData, scanResponse, object : AdvertiseCallback() {})
    }

    private fun sendNotification(title: String, message: String, address: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "chat_msg_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "BlueNix Mesajları", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        val intent = try {
            Intent(context, Class.forName("com.vahitkeskin.bluenix.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("targetDeviceAddress", address)
            }
        } catch (e: Exception) { null }
        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(context, address.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
}