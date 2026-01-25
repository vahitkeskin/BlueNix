package com.vahitkeskin.bluenix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vahitkeskin.bluenix.core.model.BluetoothDeviceDomain
import com.vahitkeskin.bluenix.core.model.LocationData
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import com.vahitkeskin.bluenix.core.service.BluetoothService
import com.vahitkeskin.bluenix.core.service.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val locationService: LocationService,
    private val bluetoothService: BluetoothService,
    private val chatRepository: ChatRepository
) : ViewModel() {

    // Konum State
    private val _locationState = MutableStateFlow<LocationData?>(null)
    val locationState: StateFlow<LocationData?> = _locationState.asStateFlow()

    // Bluetooth Cihazları State
    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>> = _scannedDevices.asStateFlow()

    // Bluetooth Durumu
    val isBluetoothOn: StateFlow<Boolean> = bluetoothService.isBluetoothEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Okunmamış mesaj sayısı
    val unreadMessageCount: StateFlow<Int> = chatRepository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Kendi Cihaz Bilgilerimiz
    private val _myDeviceName = MutableStateFlow("Loading...")
    val myDeviceName: StateFlow<String> = _myDeviceName.asStateFlow()

    private val _myDeviceAddress = MutableStateFlow("...")
    val myDeviceAddress: StateFlow<String> = _myDeviceAddress.asStateFlow()

    init {
        fetchMyDeviceInfo()
        startTracking()
    }

    private fun fetchMyDeviceInfo() {
        _myDeviceName.value = bluetoothService.getMyDeviceName()
        _myDeviceAddress.value = bluetoothService.getMyDeviceAddress()
    }

    private fun startTracking() {
        viewModelScope.launch {

            // 1. ADIM: Eşleşmiş (Paired) Cihazları Başlangıçta Yükle
            val pairedDevices = try {
                bluetoothService.getPairedDevices()
            } catch (e: Exception) {
                emptyList()
            }
            // İlk açılışta listeyi doldur
            _scannedDevices.value = pairedDevices

            // 2. ADIM: Konum Takibi
            launch {
                try {
                    locationService.getLocationUpdates().collect { data ->
                        if (data.accuracy <= 50.0f) _locationState.value = data
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. ADIM: Canlı Bluetooth Taraması
            launch {
                try {
                    bluetoothService.scanDevices().collect { scannedList ->
                        // ÖNEMLİ: Eşleşmiş cihazlar ile yeni bulunanları BİRLEŞTİR
                        // Aynı cihaza (adrese) sahip olanları filtrele (distinctBy)
                        val combinedList = (pairedDevices + scannedList)
                            .distinctBy { it.address }

                        _scannedDevices.value = combinedList
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}