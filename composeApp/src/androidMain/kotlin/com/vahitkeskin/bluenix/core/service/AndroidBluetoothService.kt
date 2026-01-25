package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.provider.Settings
import com.vahitkeskin.bluenix.core.model.BluetoothDeviceDomain
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

@SuppressLint("MissingPermission")
class AndroidBluetoothService(private val context: Context) : BluetoothService {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    override fun getPairedDevices(): List<BluetoothDeviceDomain> {
        if (adapter == null || !adapter.isEnabled) return emptyList()
        return adapter.bondedDevices.map { device ->
            BluetoothDeviceDomain(
                name = device.name ?: "Bilinmeyen Cihaz",
                address = device.address,
                isPaired = true,
                rssi = -50 // Eşleşmiş cihazlar için varsayılan iyi sinyal
            )
        }
    }

    override fun getMyDeviceName(): String {
        return try { adapter?.name ?: "BlueNix Agent" } catch (e: Exception) { "Unknown" }
    }

    override fun getMyDeviceAddress(): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (androidId != null) androidId.chunked(2).joinToString(":").uppercase(Locale.getDefault()) else "ID:UN:AV:AI:LA:BL"
        } catch (e: Exception) { "ERR:ID" }
    }

    override fun isBluetoothEnabled(): Flow<Boolean> = callbackFlow {
        trySend(adapter?.isEnabled == true)
        awaitClose {}
    }

    override fun scanDevices(): Flow<List<BluetoothDeviceDomain>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || adapter?.isEnabled == false) {
            close()
            return@callbackFlow
        }

        val foundDevices = mutableListOf<BluetoothDeviceDomain>()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)

                val device = result.device

                // --- İSİM ALMA STRATEJİSİ ---
                // 1. Önce yayın paketindeki (scanRecord) isme bak (En güncel ve doğru olan budur)
                // 2. Yoksa sistem önbelleğindeki isme bak (device.name)
                // 3. O da yoksa "Bilinmeyen" yaz.
                val realName = result.scanRecord?.deviceName
                    ?: device.name
                    ?: "Bilinmeyen Cihaz"

                // İsimsiz cihazları listeye alma (Görüntü kirliliğini önle)
                if (realName.isBlank() || realName == "Bilinmeyen Cihaz") return

                val rssi = result.rssi

                val domainDevice = BluetoothDeviceDomain(
                    name = realName,
                    address = device.address,
                    isPaired = device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED,
                    rssi = rssi
                )

                // Listeyi güncelle veya ekle
                val index = foundDevices.indexOfFirst { it.address == device.address }
                if (index != -1) {
                    // Mevcutsa güncelle (Sinyal gücü değişmiş olabilir)
                    foundDevices[index] = domainDevice
                } else {
                    foundDevices.add(domainDevice)
                }

                // Listeyi Sinyal Gücüne (RSSI) Göre Sırala (En güçlü en üstte)
                val sortedList = foundDevices.sortedByDescending { it.rssi }
                trySend(sortedList)
            }

            override fun onScanFailed(errorCode: Int) {
                // Log.e("BlueNix", "Scan Failed: $errorCode")
            }
        }

        scanner.startScan(null, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }
}