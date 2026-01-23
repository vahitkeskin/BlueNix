package com.vahitkeskin.bluenix.core.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import com.vahitkeskin.bluenix.core.model.LocationData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidLocationService(
    private val context: Context
) : LocationService {

    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override fun getLocationUpdates(): Flow<LocationData> = callbackFlow {
        // İNTERNETSİZ VE EN KESİN KONUM AYARLARI
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, // GPS Çipini Zorlar (İnternetsiz çalışır)
            1000L // 1 saniyede bir güncelle
        ).apply {
            setMinUpdateDistanceMeters(0f) // En ufak hareketi bile algıla
            setWaitForAccurateLocation(true) // Rastgele veri verme, emin olunca ver
            setMaxUpdateDelayMillis(1000L)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.lastOrNull()?.let { location ->
                    // Veriyi akışa gönder
                    trySend(
                        LocationData(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy, // Hassasiyet (Metre cinsinden)
                            timestamp = location.time
                        )
                    )
                }
            }
        }

        client.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            client.removeLocationUpdates(locationCallback)
        }
    }

    override suspend fun getCurrentLocation(): LocationData? {
        return null // Akış kullandığımız için buna gerek yok
    }
}