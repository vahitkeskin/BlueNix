package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vahitkeskin.bluenix.core.Constants
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidChatClient(
    private val context: Context
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter

    // Mesajı Bluetooth üzerinden gönderir
    fun sendRawData(address: String, message: String) {
        if (adapter == null || !adapter.isEnabled) {
            Log.e("BlueNixClient", "Bluetooth kapalı, mesaj gönderilemedi.")
            return
        }

        val device = adapter.getRemoteDevice(address)
        if (device == null) {
            Log.e("BlueNixClient", "Cihaz bulunamadı: $address")
            return
        }

        Log.i("BlueNixClient", "Bağlantı başlatılıyor: $address")

        // GATT Bağlantısını Başlat
        device.connectGatt(context, false, object : BluetoothGattCallback() {

            // 1. Bağlantı Durumu Değiştiğinde
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("BlueNixClient", "Bağlandı. Servisler keşfediliyor...")
                    gatt.discoverServices() // Servisleri (Chat Service) ara
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("BlueNixClient", "Bağlantı koptu.")
                    gatt.close()
                }
            }

            // 2. Servisler Bulunduğunda
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(UUID.fromString(Constants.CHAT_SERVICE_UUID))
                    if (service == null) {
                        Log.e("BlueNixClient", "Chat Servisi hedef cihazda bulunamadı!")
                        gatt.disconnect()
                        return
                    }

                    val characteristic = service.getCharacteristic(UUID.fromString(Constants.CHAT_CHARACTERISTIC_UUID))
                    if (characteristic == null) {
                        Log.e("BlueNixClient", "Karakteristik bulunamadı!")
                        gatt.disconnect()
                        return
                    }

                    // 3. Veriyi Yaz (Mesajı Gönder)
                    characteristic.value = message.toByteArray(Charsets.UTF_8)
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // Karşı tarafın onaylamasını bekle

                    val success = gatt.writeCharacteristic(characteristic)
                    Log.i("BlueNixClient", "Mesaj yazma isteği gönderildi: $success")
                }
            }

            // 4. Yazma İşlemi Tamamlandığında
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i("BlueNixClient", "✅ Mesaj başarıyla iletildi!")
                } else {
                    Log.e("BlueNixClient", "❌ Mesaj iletilemedi. Status: $status")
                }
                // İşimiz bitti, bağlantıyı nazikçe kapat
                gatt.disconnect()
            }
        })
    }
}