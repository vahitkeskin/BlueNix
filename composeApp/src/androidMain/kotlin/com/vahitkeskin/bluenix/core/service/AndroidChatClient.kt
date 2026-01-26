package com.vahitkeskin.bluenix.core.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vahitkeskin.bluenix.core.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
class AndroidChatClient(
    private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter

    private var activeGatt: BluetoothGatt? = null

    // Thread-safe bağlantı durumu
    private val _isConnected = AtomicBoolean(false)
    val isConnected: Boolean get() = _isConnected.get()

    private val writeMutex = Mutex()
    private val connectionMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var writeCallback: (() -> Unit)? = null

    private var connectionDeferred: CompletableDeferred<Boolean>? = null
    private val DELIMITER = "|||"

    fun connect(address: String) {
        scope.launch {
            // İlk deneme
            val success = connectSuspend(address, isRetry = false)
            // Eğer başarısız olursa (133 hatası alırsa), önbelleği temizleyip tekrar dene
            if (!success) {
                Log.w("BlueNixClient", "🔄 İlk bağlantı başarısız (Zombi bağlantı olabilir). Önbellek temizlenip tekrar deneniyor...")
                // Biraz nefes aldır
                delay(1500)
                connectSuspend(address, isRetry = true)
            }
        }
    }

    suspend fun connectSuspend(address: String, isRetry: Boolean = false): Boolean {
        if (adapter == null || !adapter.isEnabled) return false
        if (!hasConnectPermission()) return false

        // Zaten bağlıysak işlem yapma
        if (isConnected && activeGatt?.device?.address == address) return true

        return connectionMutex.withLock {
            if (isConnected && activeGatt?.device?.address == address) return@withLock true

            Log.w("BlueNixClient", "♻️ Bağlantı Operasyonu Başlatılıyor (${if(isRetry) "Retry" else "İlk"}): $address")

            // 1. ADIM: ZOMBİ BAĞLANTIYI TEMİZLE
            cleanUp()

            connectionDeferred = CompletableDeferred()

            withContext(Dispatchers.Main) {
                try {
                    val device = adapter.getRemoteDevice(address)

                    // --- NÜKLEER ÇÖZÜM: RETRY İSE AUTO-CONNECT KULLAN ---
                    // autoConnect = true, 133 hatasına karşı daha dirençlidir ama biraz yavaştır.
                    // İlk denemede false (hızlı), retry'da true (kararlı) deniyoruz.
                    val autoConnect = isRetry

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        activeGatt = device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    } else {
                        activeGatt = device.connectGatt(context, autoConnect, gattCallback)
                    }

                    // Eğer bu bir tekrar denemesi ise, gizli API ile önbelleği temizle
                    if (isRetry) {
                        refreshDeviceCache(activeGatt)
                    }

                } catch (e: Exception) {
                    Log.e("BlueNixClient", "Gatt connect hatası: ${e.message}")
                    connectionDeferred?.complete(false)
                }
            }

            try {
                // Bağlantı için 10 saniye bekle
                withTimeout(10000) {
                    connectionDeferred?.await() ?: false
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("BlueNixClient", "❌ Bağlantı zaman aşımı!")
                cleanUp()
                false
            }
        }
    }

    // --- GİZLİ SİLAH: REFLECTION İLE CACHE TEMİZLEME ---
    // Android'in "Gatt Cache"ini zorla siler. 133 hatasının ilacıdır.
    private fun refreshDeviceCache(gatt: BluetoothGatt?): Boolean {
        try {
            val localBluetoothGatt = gatt ?: return false
            val localMethod = localBluetoothGatt.javaClass.getMethod("refresh")
            if (localMethod != null) {
                val bool = localMethod.invoke(localBluetoothGatt) as Boolean
                Log.w("BlueNixClient", "🧹 Bluetooth GATT Cache Temizlendi: $bool")
                return bool
            }
        } catch (localException: Exception) {
            Log.e("BlueNixClient", "Cache temizlenemedi: " + localException.message)
        }
        return false
    }

    fun sendRawData(address: String, data: String) {
        scope.launch {
            sendRawDataSuspend(address, data)
        }
    }

    suspend fun sendRawDataSuspend(address: String, data: String): Boolean {
        if (!isConnected || activeGatt == null) {
            Log.w("BlueNixClient", "⚠️ Bağlantı yok, bağlanılıyor...")
            val connectedNow = connectSuspend(address)
            if (!connectedNow) {
                Log.e("BlueNixClient", "❌ Mesaj gönderilemedi: Bağlantı kurulamadı.")
                return false
            }
        }

        val payload = if (data.startsWith("SIG_")) data else "${adapter.name ?: "Bilinmeyen"}$DELIMITER$data"

        return writeMutex.withLock {
            try {
                if (!isConnected) {
                    if(!connectSuspend(address)) return@withLock false
                }
                internalSendSuspend(payload)
            } catch (e: Exception) {
                Log.e("BlueNixClient", "Gönderim Hatası: ${e.message}")
                false
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
            // Servis null ise belki henüz keşfedilmemiştir, 133 yüzünden servisler geç gelebilir
            Log.e("BlueNixClient", "⚠️ Servis bulunamadı (Cache sorunu olabilir).")
            if (continuation.isActive) continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        characteristic.setValue(data)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        writeCallback = { if (continuation.isActive) continuation.resume(true) }

        try {
            if (!hasConnectPermission()) {
                if (continuation.isActive) continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            val success = gatt.writeCharacteristic(characteristic)
            if (!success) {
                Log.e("BlueNixClient", "❌ writeCharacteristic başarısız.")
                writeCallback = null
                if (continuation.isActive) continuation.resume(false)
            }
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(false)
        }
    }

    fun cleanUp() {
        try {
            if (hasConnectPermission()) {
                activeGatt?.disconnect()
                // close() çok önemli! Resource sızıntısını bu engeller.
                activeGatt?.close()
            }
        } catch (e: Exception) {
            Log.e("BlueNixClient", "Cleanup hatası: ${e.message}")
        } finally {
            activeGatt = null
            _isConnected.set(false)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

            // Eğer STATUS 133 veya başka bir hata gelirse:
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BlueNixClient", "❌ Bağlantı Hatası (Status: $status). Temizlik yapılıyor.")
                cleanUp() // Zombiyi öldür
                connectionDeferred?.complete(false) // Bekleyen fonksiyona "Başarısız" de
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BlueNixClient", "✅ GATT Bağlandı. MTU isteniyor...")
                // Küçük bir gecikme eklemek bazı cihazlarda (Samsung) stabiliteyi artırır
                Handler(Looper.getMainLooper()).postDelayed({
                    if (hasConnectPermission()) gatt.requestMtu(517)
                }, 300)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("BlueNixClient", "❌ Bağlantı Koptu.")
                cleanUp()
                connectionDeferred?.complete(false)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i("BlueNixClient", "✅ MTU OK ($mtu). Servisler taranıyor...")
            if (hasConnectPermission()) {
                // MTU'dan sonra hemen tarama yapma, 300ms bekle (Samsung Fix)
                Handler(Looper.getMainLooper()).postDelayed({
                    gatt.discoverServices()
                }, 300)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BlueNixClient", "✅✅ Servisler Bulundu! Sohbet Başlayabilir.")
                _isConnected.set(true)
                connectionDeferred?.complete(true)
            } else {
                Log.e("BlueNixClient", "❌ Servis hatası: $status")
                cleanUp()
                connectionDeferred?.complete(false)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) Log.d("BlueNixClient", "✅ Veri iletildi.")
            writeCallback?.invoke()
            writeCallback = null
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    }
}