package com.vahitkeskin.bluenix.core.model

import kotlin.math.pow

data class BluetoothDeviceDomain(
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val isPaired: Boolean = false,
    val timestamp: Long = 0L
) {
    // RSSI'den Sinyal Seviyesini (0 - 4 arası) hesaplar
    // -50 ve üzeri: Mükemmel (4 diş)
    // -70 ve üzeri: İyi (3 diş)
    // -85 ve üzeri: Orta (2 diş)
    // -100 ve üzeri: Zayıf (1 diş)
    // Daha düşük: Yok (0 diş)
    fun getSignalLevel(): Int {
        return when {
            rssi >= -50 -> 4
            rssi >= -70 -> 3
            rssi >= -85 -> 2
            rssi >= -100 -> 1
            else -> 0
        }
    }

    // RSSI'den Tahmini Mesafe Hesaplama (Logaritmik Formül)
    // d = 10 ^ ((TxPower - RSSI) / (10 * n))
    // TxPower: 1 metredeki sinyal gücü (Genelde -59 varsayılır)
    // n: Ortam faktörü (Açık alan: 2, Kapalı alan: 2-4)
    fun getEstimatedDistance(): String {
        if (rssi == 0) return "?"

        val txPower = -59 // Ortalama referans gücü
        val n = 2.0 // Çevresel faktör (2 = Açık alan benzeri)

        val exponent = (txPower - rssi) / (10 * n)
        val distance = 10.0.pow(exponent)

        return if (distance < 1.0) {
            "${(distance * 100).toInt()} cm"
        } else {
            "%.1f m".format(distance)
        }
    }
}