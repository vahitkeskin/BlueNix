package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.vahitkeskin.bluenix.core.model.BluetoothDeviceDomain
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidBluetoothService(
    private val context: Context
) : BluetoothService {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    @SuppressLint("MissingPermission")
    override fun scanDevices(): Flow<List<BluetoothDeviceDomain>> = callbackFlow {
        // 1. Donanım Kontrolü
        if (adapter == null || !adapter.isEnabled) {
            Log.e("BlueNixBT", "Bluetooth kapalı veya yok.")
            trySend(emptyList())
            // Kapatmıyoruz, kullanıcı açarsa diye bekliyoruz ama şimdilik boş dönüyoruz.
            return@callbackFlow
        }

        // Taranan cihazları tutacak ortak havuz
        val foundDevices = mutableMapOf<String, BluetoothDeviceDomain>()

        // ----------------------------------------------------------------
        // YÖNTEM A: LE (Low Energy) Taraması (Mevcut kodunuz)
        // ----------------------------------------------------------------
        val leScanner = adapter.bluetoothLeScanner
        val leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                addDeviceToList(device, result.rssi, foundDevices) { sortedList ->
                    trySend(sortedList)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { result ->
                    addDeviceToList(result.device, result.rssi, foundDevices) { sortedList ->
                        trySend(sortedList)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BlueNixBT", "LE Scan Failed: $errorCode")
            }
        }

        // LE Taramasını Başlat
        if (leScanner != null) {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            leScanner.startScan(null, settings, leCallback)
            Log.i("BlueNixBT", "BLE Taraması Başlatıldı.")
        }

        // ----------------------------------------------------------------
        // YÖNTEM B: Klasik Bluetooth Taraması (Discovery)
        // ----------------------------------------------------------------
        val discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        // Yeni bir klasik cihaz bulundu
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                            .toInt()

                        device?.let {
                            addDeviceToList(it, rssi, foundDevices) { sortedList ->
                                trySend(sortedList)
                            }
                        }
                    }
                }
            }
        }

        // Receiver'ı Kaydet
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(discoveryReceiver, filter)

        // Klasik Taramayı Başlat (12 saniye sürer, sonra sistem durdurur, döngü gerekebilir)
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        adapter.startDiscovery()
        Log.i("BlueNixBT", "Klasik Discovery Başlatıldı.")

        // ----------------------------------------------------------------
        // YÖNTEM C: Zaten Eşleşmiş Cihazları Getir (Hız Kazandırır)
        // ----------------------------------------------------------------
        adapter.bondedDevices?.forEach { device ->
            // Eşleşmiş cihazların RSSI değeri olmaz, varsayılan bir değer veriyoruz (-50 gibi güçlü varsayalım)
            addDeviceToList(device, -50, foundDevices) { sortedList ->
                trySend(sortedList)
            }
        }

        // ----------------------------------------------------------------
        // TEMİZLİK (Flow Kapanınca)
        // ----------------------------------------------------------------
        awaitClose {
            Log.i("BlueNixBT", "Tüm taramalar durduruluyor...")
            leScanner?.stopScan(leCallback)

            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (e: IllegalArgumentException) {
                // Zaten unregister edilmişse hata vermesin
            }
        }
    }

    // Listeye ekleme ve sıralama yapan yardımcı fonksiyon
    @SuppressLint("MissingPermission")
    private fun addDeviceToList(
        device: BluetoothDevice,
        rssi: Int,
        map: MutableMap<String, BluetoothDeviceDomain>,
        onUpdate: (List<BluetoothDeviceDomain>) -> Unit
    ) {
        val address = device.address ?: return

        // İsim yoksa bile adresi göster
        val name = device.name ?: "Bilinmeyen Cihaz"
        val displayName = if (name.isBlank()) "No Name [$address]" else name

        // Logcat'te görmek için (İsteğe bağlı açabilirsin)
        // Log.d("BlueNixBT", "Bulundu: $displayName ($rssi)")

        map[address] = BluetoothDeviceDomain(
            name = displayName,
            address = address,
            rssi = rssi,
            timestamp = System.currentTimeMillis()
        )

        // RSSI gücüne göre sıralayıp gönder (En yakın en üstte)
        val sortedList = map.values.toList().sortedByDescending { it.rssi }
        onUpdate(sortedList)
    }

    override fun isBluetoothEnabled(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    trySend(state == BluetoothAdapter.STATE_ON)
                }
            }
        }

        // İlk durumu gönder
        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        trySend(adapter?.isEnabled == true)

        // Değişiklikleri dinle
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
}