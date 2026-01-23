package com.vahitkeskin.bluenix.core.model

data class BluetoothDeviceDomain(
    val name: String?,
    val address: String,
    val rssi: Int, // Sinyal gücü (Mesafe tahmini için)
    val timestamp: Long = 0L
) {
    // RSSI değerinden tahmini mesafe hesaplama (Basit formül)
    fun getEstimatedDistance(): String {
        val distance = Math.pow(10.0, (-69.0 - rssi) / (10.0 * 2.0))
        return if (distance < 1.0) "Running (<1m)"
        else if (distance < 10.0) "${distance.toString().take(3)}m • Active"
        else "Far • Weak"
    }
}