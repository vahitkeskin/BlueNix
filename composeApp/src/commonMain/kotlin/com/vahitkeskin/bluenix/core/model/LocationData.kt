package com.vahitkeskin.bluenix.core.model

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L
)