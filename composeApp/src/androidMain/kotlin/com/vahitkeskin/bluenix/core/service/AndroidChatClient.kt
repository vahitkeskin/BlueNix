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

    // --- KONTROLLER ---
    private var isWriting = false
    private var isServiceReady = false // Servisler keşfedildi mi?
    private var currentTargetAddress: String? = null

    fun sendRawData(address: String, message: String) {
        val data = message.toByteArray(Charsets.UTF_8)
        Log.d("BlueNixDebug", "➕ Kuyruğa Eklendi: $message")

        // Hedef değiştiyse bağlantıyı sıfırla
        if (currentTargetAddress != null && currentTargetAddress != address) {
            closeConnection()
        }
        currentTargetAddress = address
        messageQueue.add(data)

        // EĞER:
        // 1. GATT nesnesi varsa
        // 2. Servisler keşfedildiyse (READY)
        // 3. Şu an yazma işlemi yoksa
        // -> İşlem yap.
        if (bluetoothGatt != null && isServiceReady && !isWriting) {
            processQueue()
        }
        // EĞER GATT yoksa bağlan
        else if (bluetoothGatt == null) {
            connectAndSend(address)
        }
        // Diğer durumlarda (Gatt var ama Servis hazır değilse) bekle.
        else {
            Log.d("BlueNixDebug", "⏳ Servislerin hazır olması bekleniyor...")
        }
    }

    private fun connectAndSend(address: String) {
        if (adapter == null || !adapter.isEnabled) return

        val device = adapter.getRemoteDevice(address)
        Log.i("BlueNixDebug", "🔌 Bağlanılıyor: $address")

        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("BlueNixDebug", "✅ Bağlandı. Önbellek temizleniyor...")

                    // --- KRİTİK EKLENTİ: ÖNBELLEK TEMİZLİĞİ ---
                    // Cihazın eski servisleri hatırlamasını engeller.
                    val cacheCleared = refreshDeviceCache(gatt)
                    Log.d("BlueNixDebug", "🧹 Cache Temizlendi mi? -> $cacheCleared")

                    // Servis taramasını biraz gecikmeli başlat (Cache silinmesi için zaman tanı)
                    Handler(Looper.getMainLooper()).postDelayed({
                        val success = gatt.discoverServices()
                        if (!success) Log.e("BlueNixDebug", "❌ Servis taraması başlatılamadı!")
                    }, 1000) // 1 saniye bekleme

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BlueNixDebug", "❌ Bağlantı Koptu. (Status: $status)")
                    closeConnection() // Temiz kapat
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BlueNixDebug", "🔍 Servisler Bulundu. Servis kontrol ediliyor...")

                    val service = gatt.getService(UUID.fromString(Constants.CHAT_SERVICE_UUID))
                    if (service != null) {
                        Log.i("BlueNixDebug", "✅ Chat Servisi Doğrulandı! Kuyruk işleniyor...")
                        isServiceReady = true // ARTIK HAZIRIZ
                        processQueue()
                    } else {
                        Log.e("BlueNixDebug", "❌ HATA: Cihazda Chat Servisi (UUID: ${Constants.CHAT_SERVICE_UUID}) YOK.")
                        // Debug: Mevcut servisleri yazdır
                        gatt.services.forEach { s -> Log.v("BlueNixDebug", "   -> Mevcut: ${s.uuid}") }
                    }
                } else {
                    Log.e("BlueNixDebug", "❌ Servis Keşfi Başarısız: $status")
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                isWriting = false
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i("BlueNixDebug", "✅ Paket İletildi.")
                } else {
                    Log.e("BlueNixDebug", "❌ İletim Hatası: $status")
                }

                // Sıradakini gönder
                processQueue()
            }
        })
    }

    private fun processQueue() {
        if (messageQueue.isEmpty()) {
            Log.d("BlueNixDebug", "🏁 Kuyruk boşaldı.")
            return
        }

        val gatt = bluetoothGatt
        if (gatt == null) {
            Log.e("BlueNixDebug", "❌ HATA: GATT null (Koptu)")
            return
        }

        // Servis ve Karakteristik Kontrolü
        val service = gatt.getService(UUID.fromString(Constants.CHAT_SERVICE_UUID))
        if (service == null) {
            Log.e("BlueNixDebug", "❌ Kritik Hata: Servis artık yok.")
            return
        }

        val characteristic = service.getCharacteristic(UUID.fromString(Constants.CHAT_CHARACTERISTIC_UUID))
        if (characteristic == null) {
            Log.e("BlueNixDebug", "❌ HATA: Karakteristik bulunamadı!")
            return
        }

        val data = messageQueue.poll()
        if (data != null) {
            isWriting = true

            // Veri yazma ayarları (Android sürümüne göre)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                characteristic.value = data
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                gatt.writeCharacteristic(characteristic)
            }

            Log.d("BlueNixDebug", "📤 Gönderiliyor... (${data.size} byte)")
        }
    }

    // --- GİZLİ API: Bluetooth Cache Temizleme ---
    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
        try {
            val localMethod = gatt.javaClass.getMethod("refresh")
            if (localMethod != null) {
                return localMethod.invoke(gatt) as Boolean
            }
        } catch (e: Exception) {
            Log.e("BlueNixDebug", "⚠️ Cache temizlenemedi: ${e.message}")
        }
        return false
    }

    fun closeConnection() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        isServiceReady = false
        isWriting = false
        messageQueue.clear()
        currentTargetAddress = null
        Log.d("BlueNixDebug", "♻️ Bağlantı ve Kuyruk Temizlendi.")
    }
}