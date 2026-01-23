package com.vahitkeskin.bluenix.core.service

import com.vahitkeskin.bluenix.core.model.LocationData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.*
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.timeIntervalSince1970

class IosLocationService : LocationService {

    private val locationManager = CLLocationManager()

    @OptIn(ExperimentalForeignApi::class)
    override fun getLocationUpdates(): Flow<LocationData> = callbackFlow {
        // Delegate'i flow bloğu içinde tanımlayıp locationManager'a atıyoruz.
        // Bu blok yaşadığı sürece delegate de yaşayacak.
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {

            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val locations = didUpdateLocations as? List<CLLocation>
                val location = locations?.lastOrNull()

                location?.let { loc ->
                    // CValue<CLLocationCoordinate2D> kullanımı için useContents şarttır
                    val lat = loc.coordinate.useContents { latitude }
                    val lon = loc.coordinate.useContents { longitude }

                    trySend(
                        LocationData(
                            latitude = lat,
                            longitude = lon,
                            accuracy = loc.horizontalAccuracy.toFloat(),
                            timestamp = (loc.timestamp.timeIntervalSince1970 * 1000).toLong()
                        )
                    )
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                // İleride loglama eklenebilir
                println("Location Error: ${didFailWithError.localizedDescription}")
            }
        }

        locationManager.allowsBackgroundLocationUpdates = true // KRİTİK NOKTA
        locationManager.pausesLocationUpdatesAutomatically = false // Durmasın diye

        locationManager.delegate = delegate
        // iOS'te pil ömrü için hassasiyeti ayarlayalım
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = 10.0 // 10 metrede bir güncelle (Opsiyonel)

        // İzin durumunu kontrol edip istemek daha sağlıklıdır ama şimdilik direkt istiyoruz
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()

        awaitClose {
            locationManager.stopUpdatingLocation()
            locationManager.delegate = null // Memory leak önleme
        }
    }

    override suspend fun getCurrentLocation(): LocationData? {
        // Tek seferlik konum için Flow'un first() metodunu ViewModel'de kullanabilirsin.
        return null
    }
}