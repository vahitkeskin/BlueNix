package com.vahitkeskin.bluenix.core.service

import com.vahitkeskin.bluenix.core.model.LocationData
import kotlinx.coroutines.flow.Flow

interface LocationService {
    // Konum güncellemelerini sürekli akış olarak verir
    fun getLocationUpdates(): Flow<LocationData>

    // Tek seferlik konum (gerekirse)
    suspend fun getCurrentLocation(): LocationData?
}