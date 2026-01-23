package com.vahitkeskin.bluenix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vahitkeskin.bluenix.core.model.LocationData
import com.vahitkeskin.bluenix.core.service.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val locationService: LocationService
) : ViewModel() {

    private val _locationState = MutableStateFlow<LocationData?>(null)
    val locationState: StateFlow<LocationData?> = _locationState.asStateFlow()

    init {
        startLocationTracking()
    }

    private fun startLocationTracking() {
        viewModelScope.launch {
            try {
                locationService.getLocationUpdates().collect { data ->
                    // Filtreleme: Eğer sapma 20 metreden fazlaysa gösterme (Kalite Kontrol)
                    if (data.accuracy <= 20.0f) {
                        _locationState.value = data
                    }
                }
            } catch (e: Exception) {
                println("GPS Hatası: ${e.message}")
            }
        }
    }
}