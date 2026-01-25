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

@SuppressLint("MissingPermission")
class AndroidChatController(
    private val context: Context
) : ChatController, KoinComponent {

    private val repository: ChatRepository by inject()

    private val _remoteTypingState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    override val remoteTypingState: StateFlow<Map<String, Boolean>> = _remoteTypingState.asStateFlow()

    // Şu an açık olan sohbetin adresi (Bildirimleri engellemek için)
    private var activeChatAddress: String? = null

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // UI Tarafından çağrılır: Hangi sohbette olduğumuzu set eder
    override fun setActiveChat(address: String?) {
        activeChatAddress = address?.uppercase() // Garanti olsun diye büyük harfe çevir
    }

    override fun startHosting() {
        if (adapter == null || !adapter.isEnabled) return
        if (gattServer != null) gattServer?.close()

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

                when (incomingData) {
                    "SIG_TYP_START" -> _remoteTypingState.update { it + (address to true) }
                    "SIG_TYP_STOP" -> _remoteTypingState.update { it + (address to false) }
                    else -> {
                        _remoteTypingState.update { it + (address to false) }
                        scope.launch {
                            // --- İSİM DÜZELTME HAMLESİ ---
                            // Gelen mesajın kimden geldiğini bulurken "Unknown" yerine gerçek ismi zorla.
                            val safeName = getBestDeviceName(device)

                            // 1. Veritabanına kaydet (Listede isim düzelsin diye safeName gönderiyoruz)
                            repository.receiveMessage(address, safeName, incomingData)

                            // 2. Bildirim Kontrolü
                            // Eğer şu an bu kişiyle konuşmuyorsak bildirim gönder
                            if (activeChatAddress != address.uppercase()) {
                                sendNotification(safeName, incomingData, address)
                            }
                        }
                    }
                }
            }
        }
        gattServer = bluetoothManager.openGattServer(context, callback)
        addServicesToGattServer()
    }

    // Cihaz ismini bulmak için en iyi yöntem
    private fun getBestDeviceName(device: BluetoothDevice): String {
        // 1. Cihazın kendi ismi var mı?
        if (!device.name.isNullOrBlank()) return device.name

        // 2. Yoksa, Eşleşmiş (Bonded) cihazlar listesine bak (Orada isim kesin vardır)
        val bondedMatch = adapter.bondedDevices.find { it.address == device.address }
        if (bondedMatch?.name != null) return bondedMatch.name

        // 3. Hiçbiri yoksa Adresi döndür
        return "Cihaz ${device.address.takeLast(5)}"
    }

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

        // İSİM GÖSTERİMİ İÇİN:
        // UUID'yi AdvertiseData'ya, İsmi ScanResponse'a koyuyoruz.
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(UUID.fromString(Constants.CHAT_SERVICE_UUID)))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // İsim burada gidiyor
            .build()

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
            .setContentTitle(title) // Artık burada Gerçek İsim yazacak
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(address.hashCode(), notification)
    }
}