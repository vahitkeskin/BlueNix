package com.vahitkeskin.bluenix.core.service

import com.vahitkeskin.bluenix.core.model.LocationData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DesktopLocationService : LocationService {
    override fun getLocationUpdates(): Flow<LocationData> = flow {
        // Desktop için simülasyon veya IP location API entegrasyonu buraya gelir.
        // Şimdilik sabit bir konum dönelim (Örn: İstanbul)
        while (true) {
            emit(LocationData(41.0082, 28.9784))
            delay(5000)
        }
    }

    override suspend fun getCurrentLocation(): LocationData? {
        return LocationData(41.0082, 28.9784)
    }
}