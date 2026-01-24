package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.vahitkeskin.bluenix.core.Constants
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidGattServer(private val context: Context) {

    private var gattServer: BluetoothGattServer? = null
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    // Mesaj geldiğinde tetiklenecek fonksiyon
    var onMessageReceived: ((String) -> Unit)? = null

    fun startServer() {
        if (gattServer != null) return // Zaten açıksa tekrar açma

        val callback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BlueNixChat", "📥 Bir cihaz sunucuya bağlandı: ${device.address}")
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

                value?.let {
                    val message = String(it, Charsets.UTF_8)
                    Log.d("BlueNixChat", "📩 YENİ MESAJ: $message")

                    // UI'a haber ver (Main Thread'de değilse crash verebilir, dikkat)
                    onMessageReceived?.invoke(message)
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
        Log.i("BlueNixChat", "🚀 GATT Sunucusu Başlatıldı. Mesaj bekleniyor...")
    }
}