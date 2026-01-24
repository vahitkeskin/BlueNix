package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.vahitkeskin.bluenix.core.Constants
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidChatClient(private val context: Context) {

    private val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // İSİM DEĞİŞİKLİĞİ: sendMessage -> sendRawData
    // Bu sayede hem sohbet metnini hem de "Yazıyor..." sinyalini taşıyabilir.
    fun sendRawData(targetAddress: String, data: String) {
        try {
            val device = bluetoothAdapter.getRemoteDevice(targetAddress)

            // Bağlantı ve yazma işlemleri
            device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i("BlueNixChat", "Bağlandı, servisler aranıyor...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt.close()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt.getService(UUID.fromString(Constants.CHAT_SERVICE_UUID))
                        val characteristic = service?.getCharacteristic(UUID.fromString(Constants.CHAT_CHARACTERISTIC_UUID))

                        if (characteristic != null) {
                            characteristic.value = data.toByteArray(Charsets.UTF_8)
                            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                            val success = gatt.writeCharacteristic(characteristic)
                            Log.d("BlueNixChat", "📤 Veri gönderildi ($success): $data")

                            // Mesaj gittikten sonra bağlantıyı kapatmak pil tasarrufu sağlar
                            // Ancak "Yazıyor" gibi sık giden sinyallerde açık tutmak daha iyidir.
                            // gatt.disconnect()
                        } else {
                            Log.e("BlueNixChat", "HATA: Karşı tarafta BlueNix Chat servisi bulunamadı.")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("BlueNixChat", "Gönderim Hatası: ${e.message}")
        }
    }
}