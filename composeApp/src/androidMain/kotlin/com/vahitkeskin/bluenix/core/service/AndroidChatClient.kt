package com.vahitkeskin.bluenix.core.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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

    private val _isConnected = AtomicBoolean(false)
    val isConnected: Boolean get() = _isConnected.get()

    private val writeMutex = Mutex()
    private val connectionMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var writeCallback: (() -> Unit)? = null

    private var connectionDeferred: CompletableDeferred<Boolean>? = null
    private val DELIMITER = "|||"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect(address: String) {
        scope.launch {
            // Önce mevcut bağlantı varsa kopar
            if (isConnected && activeGatt?.device?.address == address) return@launch

            // --- 1. ADIM: Cihazı Havada Yakala (Scan) ---
            Log.d("BlueNixClient", "🔍 Bağlantı öncesi cihaz aranıyor: $address")
            val isDeviceNearby = waitForDeviceDiscovery(address)

            if (!isDeviceNearby) {
                Log.e("BlueNixClient", "❌ Cihaz taramada bulunamadı! Yine de şansımızı deniyoruz...")
            } else {
                Log.i("BlueNixClient", "✅ Cihaz taramada bulundu! Şimdi bağlanılıyor.")
            }

            // --- 2. ADIM: Bağlan ---
            val success = connectSuspend(address)

            if (!success) {
                Log.w("BlueNixClient", "🔄 İlk bağlantı başarısız. Cache temizleyip tekrar deneniyor...")
                cleanUp()
                delay(1000)
                connectSuspend(address, isRetry = true)
            }
        }
    }

    // --- YENİ EKLENEN FONKSİYON: Cihazı Bulma ---
    private suspend fun waitForDeviceDiscovery(targetAddress: String): Boolean = suspendCancellableCoroutine { cont ->
        if (adapter == null || !adapter.isEnabled || !hasConnectPermission()) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        // Sadece hedef MAC adresini arayan filtre
        val filters = listOf(
            ScanFilter.Builder().setDeviceAddress(targetAddress).build()
        )

        // Düşük gecikmeli (hızlı) tarama ayarı
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result?.device?.address == targetAddress) {
                    // Cihazı bulduk! Taramayı durdur ve devam et
                    Log.i("BlueNixClient", "🎯 Hedef cihaz sahada görüldü: $targetAddress")
                    scanner.stopScan(this)
                    if (cont.isActive) cont.resume(true)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BlueNixClient", "Scan failed: $errorCode")
                if (cont.isActive) cont.resume(false)
            }
        }

        // Taramayı başlat
        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            Log.e("BlueNixClient", "Tarama başlatılamadı: ${e.message}")
            if (cont.isActive) cont.resume(false)
            return@suspendCancellableCoroutine
        }

        // 3 Saniye zaman aşımı koy (Bulamazsa pes etme, connect'e geç)
        mainHandler.postDelayed({
            if (cont.isActive) {
                Log.w("BlueNixClient", "⚠️ Tarama zaman aşımı (Cihaz görünmedi).")
                try { scanner.stopScan(scanCallback) } catch (e: Exception) {}
                cont.resume(false)
            }
        }, 3000)
    }

    suspend fun connectSuspend(address: String, isRetry: Boolean = false): Boolean {
        if (adapter == null || !adapter.isEnabled || !hasConnectPermission()) return false

        return connectionMutex.withLock {
            if (isConnected && activeGatt?.device?.address == address) return@withLock true

            Log.w("BlueNixClient", "🔌 Bağlantı başlatılıyor ($address)")
            cleanUp()
            connectionDeferred = CompletableDeferred()

            mainHandler.post {
                try {
                    val device = adapter.getRemoteDevice(address)
                    val appContext = context.applicationContext

                    // Android 8.0+ için PHY LE 1M zorlaması (Daha stabil)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activeGatt = device.connectGatt(
                            appContext,
                            false, // autoConnect false daha hızlıdır
                            gattCallback,
                            BluetoothDevice.TRANSPORT_LE,
                            BluetoothDevice.PHY_LE_1M_MASK
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        activeGatt = device.connectGatt(
                            appContext,
                            false,
                            gattCallback,
                            BluetoothDevice.TRANSPORT_LE
                        )
                    } else {
                        activeGatt = device.connectGatt(appContext, false, gattCallback)
                    }

                    if (isRetry) refreshDeviceCache(activeGatt)

                } catch (e: Exception) {
                    Log.e("BlueNixClient", "Connect hatası: ${e.message}")
                    try { connectionDeferred?.complete(false) } catch(_:Exception){}
                }
            }

            try {
                withTimeout(10000) { connectionDeferred?.await() ?: false }
            } catch (e: TimeoutCancellationException) {
                Log.e("BlueNixClient", "❌ Bağlantı zaman aşımı!")
                cleanUp()
                false
            }
        }
    }

    private fun refreshDeviceCache(gatt: BluetoothGatt?): Boolean {
        try {
            val localBluetoothGatt = gatt ?: return false
            val localMethod = localBluetoothGatt.javaClass.getMethod("refresh")
            return localMethod.invoke(localBluetoothGatt) as Boolean
        } catch (localException: Exception) { }
        return false
    }

    fun sendRawData(address: String, data: String) {
        scope.launch { sendRawDataSuspend(address, data) }
    }

    suspend fun sendRawDataSuspend(address: String, data: String): Boolean {
        if (!isConnected || activeGatt == null) {
            Log.w("BlueNixClient", "⚠️ Bağlantı yok, süreç başlatılıyor...")
            // Scan ve Connect sürecini başlat
            connect(address)
            // Bağlantının kurulması için biraz bekle
            delay(4000)
            if (!isConnected) return false
        }

        val payload = if (data.startsWith("SIG_")) data else "${adapter.name ?: "Bilinmeyen"}$DELIMITER$data"

        return writeMutex.withLock {
            try {
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
            Log.e("BlueNixClient", "⚠️ Servis bulunamadı.")
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
                Log.e("BlueNixClient", "❌ Yazma başarısız.")
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
                activeGatt?.close()
            }
        } catch (e: Exception) {}
        finally {
            activeGatt = null
            _isConnected.set(false)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BlueNixClient", "❌ Bağlantı Hatası (Status: $status).")
                cleanUp()
                try { connectionDeferred?.complete(false) } catch(_:Exception){}
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BlueNixClient", "✅ GATT Bağlandı. Bekleniyor...")
                mainHandler.postDelayed({
                    if (hasConnectPermission()) {
                        try { gatt.requestMtu(517) }
                        catch (e: Exception) { gatt.discoverServices() }
                    }
                }, 600)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("BlueNixClient", "❌ Bağlantı Koptu.")
                cleanUp()
                try { connectionDeferred?.complete(false) } catch(_:Exception){}
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i("BlueNixClient", "✅ MTU OK. Servisler aranıyor...")
            if (hasConnectPermission()) {
                mainHandler.postDelayed({ gatt.discoverServices() }, 300)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BlueNixClient", "✅✅ Servisler Hazır!")
                _isConnected.set(true)
                try { connectionDeferred?.complete(true) } catch(_:Exception){}
            } else {
                Log.e("BlueNixClient", "❌ Servis hatası: $status")
                cleanUp()
                try { connectionDeferred?.complete(false) } catch(_:Exception){}
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
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