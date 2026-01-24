package com.vahitkeskin.bluenix.core.service

import kotlinx.coroutines.flow.StateFlow

interface ChatController {

    // Karşı tarafın "Yazıyor..." bilgisini dinlemek için
    val isRemoteTyping: StateFlow<Boolean>
    val typingDeviceAddress: StateFlow<String?>
    // Sunucuyu başlatmak için (Mesaj ve Sinyal almaya hazır ol)
    fun startHosting()
}