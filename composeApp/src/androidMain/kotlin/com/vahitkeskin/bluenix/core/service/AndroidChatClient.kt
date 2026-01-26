package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vahitkeskin.bluenix.core.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
class AndroidChatClient(
    private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter

    private var activeGatt: BluetoothGatt? = null
    private var isConnected = false

    private val writeMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var writeCallback: (() -> Unit)? = null

    private val DELIMITER = "|||"

    fun forceResetConnection(address: String) {
        Log.w("BlueNixClient", "⚠️ Bağlantı zorla sıfırlanıyor: $address")
        disconnect()
        Handler(Looper.getMainLooper()).postDelayed({
            connect(address)
        }, 1000)
    }

    fun connect(address: String) {
        if (adapter == null || !adapter.isEnabled) return

        // --- DEĞİŞİKLİK BURADA: ESKİ BAĞLANTIYI KESİN OLARAK ÖLDÜR ---
        // "Zaten bağlıyım" kontrolünü KALDIRDIK. Her connect çağrısında
        // bağlantıyı tazelemeye zorluyoruz.

        Log.w("BlueNixClient", "♻️ Bağlantı tazeleniyor: $address")

        // Varsa eskiyi kapat
        disconnect()

        // Kısa bir bekleme (Bluetooth stack'inin nefes alması için)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val device = adapter.getRemoteDevice(address)
                Log.i("BlueNixClient", "🔌 Yeni bağlantı başlatılıyor...")

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    activeGatt = device.connectGatt(context, false, gattCallback)
                }
            } catch (e: Exception) {
                Log.e("BlueNixClient", "Bağlantı hatası: ${e.message}")
            }
        }, 150) // 150ms gecikme
    }

    fun sendRawData(address: String, data: String) {
        scope.launch {
            if (activeGatt == null || !isConnected) {
                withContext(Dispatchers.Main) { connect(address) }
                delay(2000)
            }

            val payload = if (data.startsWith("SIG_")) {
                data
            } else {
                val myName = adapter.name ?: "Bilinmeyen"
                "$myName$DELIMITER$data"
            }

            Log.d("BlueNixTrace", """
                🚀 ---------------- GİDEN PAKET ----------------
                📦 ORİJİNAL: $data
                📦 GÖNDERİLEN (Payload): $payload
                📏 UZUNLUK: ${payload.toByteArray().size} Byte
                ------------------------------------------------
            """.trimIndent())

            writeMutex.withLock {
                try {
                    val success = internalSendSuspend(payload)
                    if (!success) {
                        withContext(Dispatchers.Main) { forceResetConnection(address) }
                    }
                } catch (e: Exception) {
                    Log.e("BlueNixClient", "Hata: ${e.message}")
                }
            }
        }
    }

    private suspend fun internalSendSuspend(data: String): Boolean = suspendCancellableCoroutine { continuation ->
        val gatt = activeGatt
        if (gatt == null) {
            if (continuation.isActive) continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        val service = gatt.getService(UUID.fromString(Constants.CHAT_SERVICE_UUID))
        val characteristic = service?.getCharacteristic(UUID.fromString(Constants.CHAT_CHARACTERISTIC_UUID))

        if (characteristic == null) {
            Log.e("BlueNixClient", "Servis bulunamadı (MTU sorunu olabilir).")
            if (continuation.isActive) continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        characteristic.setValue(data)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        writeCallback = {
            if (continuation.isActive) continuation.resume(true)
        }

        val success = gatt.writeCharacteristic(characteristic)
        if (!success) {
            if (continuation.isActive) continuation.resume(false)
        }
    }

    private fun disconnect() {
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
        isConnected = false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                Log.i("BlueNixClient", "✅ Bağlandı. MTU Artırılıyor...")

                // --- KRİTİK DEĞİŞİKLİK: ÖNCE MTU İSTE ---
                // Varsayılan 20 byte yetmez, 517 byte istiyoruz.
                gatt.requestMtu(517)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("BlueNixClient", "❌ Koptu.")
                isConnected = false
                activeGatt = null
                gatt.close()
            }
        }

        // --- YENİ: MTU DEĞİŞİNCE SERVİSLERİ ARA ---
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.i("BlueNixClient", "✅ MTU Onaylandı: $mtu Byte. Şimdi servisler aranıyor.")
            // MTU büyüdükten sonra servisleri keşfetmek daha güvenlidir
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BlueNixClient", "✅ Servisler Hazır.")
            } else {
                disconnect()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BlueNixClient", "🚀 Veri gitti.")
            }
            writeCallback?.invoke()
            writeCallback = null
        }
    }
}