package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.vahitkeskin.bluenix.core.model.BluetoothDeviceDomain
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidBluetoothService(
    private val context: Context
) : BluetoothService {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    @SuppressLint("MissingPermission") // İzinler MainActivity'de kontrol ediliyor
    override fun scanDevices(): Flow<List<BluetoothDeviceDomain>> = callbackFlow {
        // 1. ADIM: Donanım Kontrolü
        if (adapter == null) {
            Log.e("BlueNixBT", "HATA: Cihazda Bluetooth donanımı bulunamadı.")
            close() // Flow'u kapat
            return@callbackFlow
        }

        if (!adapter.isEnabled) {
            Log.w("BlueNixBT", "UYARI: Bluetooth kapalı. Kullanıcının açması bekleniyor.")
            // Burada kullanıcıya bir uyarı gösterilebilir, şimdilik boş liste dönüyoruz.
            trySend(emptyList())
            // close() yapmıyoruz, belki kullanıcı sonradan açar.
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e("BlueNixBT", "HATA: Bluetooth Scanner başlatılamadı (Bluetooth kapalı olabilir).")
            close()
            return@callbackFlow
        }

        // Taranan cihazları tutacağımız geçici hafıza (Tekrar edenleri engellemek için Map)
        val foundDevices = mutableMapOf<String, BluetoothDeviceDomain>()

        // 2. ADIM: Tarama Ayarları (Düşük Gecikme = Hızlı Tespit)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // 3. ADIM: Callback Tanımı
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)

                val device = result.device
                val address = device.address

                // İsim yoksa "No Name" yaz ama GİZLEME
                val displayName = if (device.name.isNullOrBlank()) {
                    "No Name [$address]"
                } else {
                    device.name
                }

                // Log'a da bakalım
                //Log.d("BlueNixBT", "Eklendi: $displayName - RSSI: ${result.rssi}")

                foundDevices[address] = BluetoothDeviceDomain(
                    name = displayName,
                    address = address,
                    rssi = result.rssi,
                    timestamp = System.currentTimeMillis()
                )

                trySend(foundDevices.values.toList().sortedByDescending { it.rssi })
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                results?.forEach { result ->
                    // Toplu gelen sonuçları işle (Nadiren tetiklenir ama iyidir)
                    val device = result.device
                    foundDevices[device.address] = BluetoothDeviceDomain(
                        name = device.name ?: "Unknown Device",
                        address = device.address,
                        rssi = result.rssi,
                        timestamp = System.currentTimeMillis()
                    )
                }
                trySend(foundDevices.values.toList().sortedByDescending { it.rssi })
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                val errorMessage = when(errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Tarama zaten çalışıyor."
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Uygulama kaydedilemedi (Restart gerekebilir)."
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Bu cihaz BLE desteklemiyor."
                    SCAN_FAILED_INTERNAL_ERROR -> "Dahili Bluetooth hatası."
                    else -> "Bilinmeyen Hata: $errorCode"
                }
                Log.e("BlueNixBT", "Tarama Başarısız: $errorMessage")
            }
        }

        // 4. ADIM: Taramayı Başlat
        try {
            Log.i("BlueNixBT", "Bluetooth taraması başlatılıyor...")
            scanner.startScan(null, settings, callback)
        } catch (e: Exception) {
            Log.e("BlueNixBT", "startScan hatası: ${e.message}")
            close(e) // Hata ile kapat
        }

        // 5. ADIM: Temizlik (Memory Leak Önleme)
        awaitClose {
            Log.i("BlueNixBT", "Bluetooth taraması durduruluyor...")
            // Scanner null olabilir (Bluetooth kapatıldıysa), kontrol et
            try {
                scanner?.stopScan(callback)
            } catch (e: Exception) {
                Log.e("BlueNixBT", "stopScan hatası: ${e.message}")
            }
        }
    }
}