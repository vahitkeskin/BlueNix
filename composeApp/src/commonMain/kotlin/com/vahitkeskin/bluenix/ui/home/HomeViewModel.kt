package com.vahitkeskin.bluenix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vahitkeskin.bluenix.core.model.BluetoothDeviceDomain
import com.vahitkeskin.bluenix.core.model.LocationData
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import com.vahitkeskin.bluenix.core.service.BluetoothService
import com.vahitkeskin.bluenix.core.service.ChatController
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
    private val chatRepository: ChatRepository,
    private val chatController: ChatController // Yayın başlatmak için gerekli
) : ViewModel() {

    // --- STATE TANIMLARI ---

    // Konum Bilgisi (GPS)
    private val _locationState = MutableStateFlow<LocationData?>(null)
    val locationState: StateFlow<LocationData?> = _locationState.asStateFlow()

    // Cihaz Listesi (Eşleşmiş + Taranan)
    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>> = _scannedDevices.asStateFlow()

    // Bluetooth Açık/Kapalı Durumu
    val isBluetoothOn: StateFlow<Boolean> = bluetoothService.isBluetoothEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Okunmamış Mesaj Sayısı (Badge için)
    val unreadMessageCount: StateFlow<Int> = chatRepository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Kendi Cihaz Bilgilerimiz (Radar Ortası İçin)
    private val _myDeviceName = MutableStateFlow("Yükleniyor...")
    val myDeviceName: StateFlow<String> = _myDeviceName.asStateFlow()

    private val _myDeviceAddress = MutableStateFlow("...")
    val myDeviceAddress: StateFlow<String> = _myDeviceAddress.asStateFlow()

    init {
        // 1. Kendi kimliğimizi al (Hacker ID ve İsim)
        fetchMyDeviceInfo()

        // 2. KRİTİK: Uygulama açılır açılmaz yayın yapmaya başla!
        // Böylece karşı taraf tarama yaptığında bizi hemen görür.
        chatController.startHosting()

        // 3. Tarama ve Konum işlemlerini başlat
        startTracking()
    }

    private fun fetchMyDeviceInfo() {
        // Service üzerinden platform bağımsız şekilde veriyi çekiyoruz
        _myDeviceName.value = bluetoothService.getMyDeviceName()
        _myDeviceAddress.value = bluetoothService.getMyDeviceAddress()
    }

    private fun startTracking() {
        viewModelScope.launch {
            // A. Eşleşmiş (Paired) Cihazları Önden Yükle
            // Kullanıcı beklemesin, hemen listeyi dolduralım.
            val pairedDevices = try {
                bluetoothService.getPairedDevices()
            } catch (e: Exception) {
                emptyList()
            }
            _scannedDevices.value = pairedDevices

            // B. Konum Güncellemelerini Başlat
            launch {
                try {
                    locationService.getLocationUpdates().collect { data ->
                        // Sadece yüksek doğruluklu veriyi kabul et (Görsel kirliliği önle)
                        if (data.accuracy <= 100.0f) {
                            _locationState.value = data
                        }
                    }
                } catch (e: Exception) {
                    // Konum servisi hatası (İzin yok vs.)
                    e.printStackTrace()
                }
            }

            // C. Canlı Bluetooth Taramasını Başlat
            launch {
                try {
                    bluetoothService.scanDevices().collect { scannedList ->
                        // LİSTE BİRLEŞTİRME MANTIĞI:
                        // 1. scannedList: O an havada sinyali olanlar (RSSI var)
                        // 2. pairedDevices: Eşleşmiş ama belki şu an kapalı olanlar

                        // 'scannedList'i başa koyuyoruz çünkü onlarda güncel RSSI verisi var.
                        // 'distinctBy' ile aynı adrese sahip olanların tekrarını engelliyoruz.
                        val combinedList = (scannedList + pairedDevices)
                            .distinctBy { it.address }
                            .sortedByDescending { it.rssi } // En güçlü sinyal en üstte

                        _scannedDevices.value = combinedList
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}