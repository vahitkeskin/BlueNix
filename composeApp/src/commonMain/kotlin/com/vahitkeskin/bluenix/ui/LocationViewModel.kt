package com.vahitkeskin.bluenix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vahitkeskin.bluenix.core.model.LocationData
import com.vahitkeskin.bluenix.core.service.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationViewModel(
    private val locationService: LocationService
) : ViewModel() {

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    init {
        startTracking()
    }

    private fun startTracking() {
        viewModelScope.launch {
            locationService.getLocationUpdates().collect { location ->
                _currentLocation.value = location

                // BURASI ÖNEMLİ:
                // Bluetooth servisine bu konumu burada gönderebilirsin.
                // bluetoothService.broadcastLocation(location)
            }
        }
    }
}