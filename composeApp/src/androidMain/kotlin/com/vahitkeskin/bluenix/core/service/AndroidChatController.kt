package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.vahitkeskin.bluenix.core.Constants
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import com.vahitkeskin.bluenix.data.repository.AndroidChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidChatController(
    private val context: Context,
    private val repository: ChatRepository // BURASI ARAYÜZ (INTERFACE) OLMALI
) : ChatController {

    // --- ZORUNLU ALAN (Interface Gereği) ---
    // Karşı tarafın yazıyor bilgisini tutan StateFlow
    private val _isRemoteTyping = MutableStateFlow(false)
    override val isRemoteTyping: StateFlow<Boolean> = _isRemoteTyping.asStateFlow()

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null

    // ---------------- SERVER KISMI (Mesaj ve Sinyal Alma) ----------------
    override fun startHosting() {
        if (gattServer != null) return

        val callback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int
            ) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BlueNixChat", "Cihaz bağlandı: ${device.address}")
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
                super.onCharacteristicWriteRequest(
                    device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value
                )

                value?.let {
                    val incomingData = String(it, Charsets.UTF_8)

                    // --- KRİTİK MANTIK ---
                    // Gelen veri bir komut mu (Yazıyor...) yoksa mesaj mı?
                    when (incomingData) {
                        "SIG_TYP_START" -> {
                            _isRemoteTyping.value = true
                        }

                        "SIG_TYP_STOP" -> {
                            _isRemoteTyping.value = false
                        }

                        else -> {
                            // Gerçek Mesaj Geldi -> Repository'ye ver, DB'ye yazsın
                            _isRemoteTyping.value = false // Mesaj geldiyse yazma bitmiştir
                            Log.d("BlueNixChat", "📩 Mesaj alındı ve DB'ye işleniyor: $incomingData")

                            CoroutineScope(Dispatchers.IO).launch {
                                repository.receiveMessage(
                                    address = device.address,
                                    name = device.name ?: "Unknown",
                                    text = incomingData
                                )
                            }
                        }
                    }
                }

                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

        gattServer = bluetoothManager.openGattServer(context, callback)
        setupServices()
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
        Log.i("BlueNixChat", "🚀 Sunucu Aktif (Dinleniyor)")
    }
}