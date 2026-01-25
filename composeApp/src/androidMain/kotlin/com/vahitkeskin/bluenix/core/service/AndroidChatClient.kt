package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vahitkeskin.bluenix.core.Constants
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidChatClient(
    private val context: Context
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private val messageQueue: Queue<ByteArray> = LinkedList()

    private var isWriting = false
    private var isServiceReady = false
    private var currentTargetAddress: String? = null

    fun sendRawData(address: String, message: String) {
        val data = message.toByteArray(Charsets.UTF_8)
        Log.d("BlueNixDebug", "➕ Kuyruğa Eklendi: $message")

        if (currentTargetAddress != null && currentTargetAddress != address) {
            closeConnection()
        }
        currentTargetAddress = address
        messageQueue.add(data)

        if (bluetoothGatt != null && isServiceReady && !isWriting) {
            processQueue()
        } else if (bluetoothGatt == null) {
            connectAndSend(address)
        } else {
            Log.d("BlueNixDebug", "⏳ Servislerin hazır olması bekleniyor...")
        }
    }

    private fun connectAndSend(address: String) {
        if (adapter == null || !adapter.isEnabled) return

        val device = adapter.getRemoteDevice(address)
        Log.i("BlueNixDebug", "🔌 Bağlanılıyor: $address")

        val transport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BluetoothDevice.TRANSPORT_LE
        } else {
            BluetoothDevice.TRANSPORT_AUTO
        }

        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("BlueNixDebug", "✅ Bağlandı. Cache temizleniyor...")
                    refreshDeviceCache(gatt)

                    Handler(Looper.getMainLooper()).postDelayed({
                        val success = gatt.discoverServices()
                        if (!success) Log.e("BlueNixDebug", "❌ Servis taraması başlatılamadı!")
                    }, 1500) // 1.5 sn gecikme verelim, sunucu anca hazır olur

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BlueNixDebug", "❌ Bağlantı Koptu. (Status: $status)")
                    closeConnection()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(UUID.fromString(Constants.CHAT_SERVICE_UUID))

                    if (service != null) {
                        Log.i("BlueNixDebug", "✅ Chat Servisi Doğrulandı! Gönderim başlıyor...")
                        isServiceReady = true
                        processQueue()
                    } else {
                        Log.e("BlueNixDebug", "❌ HATA: Hedef Servis Bulunamadı!")
                        Log.e("BlueNixDebug", "--- BULUNAN SERVİSLER ---")
                        gatt.services.forEach {
                            Log.e("BlueNixDebug", "   UUID: ${it.uuid}")
                        }
                        Log.e("BlueNixDebug", "-------------------------")
                    }
                } else {
                    Log.e("BlueNixDebug", "❌ Servis Keşfi Hatası: $status")
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                isWriting = false
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i("BlueNixDebug", "✅ Mesaj İletildi.")
                } else {
                    Log.e("BlueNixDebug", "❌ İletim Hatası: $status")
                }
                processQueue()
            }
        }, transport)
    }

    private fun processQueue() {
        if (messageQueue.isEmpty()) {
            Log.d("BlueNixDebug", "🏁 Kuyruk boşaldı.")
            return
        }

        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(UUID.fromString(Constants.CHAT_SERVICE_UUID)) ?: return
        val characteristic = service.getCharacteristic(UUID.fromString(Constants.CHAT_CHARACTERISTIC_UUID)) ?: return

        val data = messageQueue.poll()
        if (data != null) {
            isWriting = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                characteristic.value = data
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(characteristic)
            }

            Log.d("BlueNixDebug", "🚀 Hızlı Gönderiliyor... (${data.size} byte)")
        }
    }

    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
        try {
            val localMethod = gatt.javaClass.getMethod("refresh")
            if (localMethod != null) {
                return localMethod.invoke(gatt) as Boolean
            }
        } catch (e: Exception) {}
        return false
    }

    fun closeConnection() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        isServiceReady = false
        isWriting = false
        messageQueue.clear()
        currentTargetAddress = null
    }
}