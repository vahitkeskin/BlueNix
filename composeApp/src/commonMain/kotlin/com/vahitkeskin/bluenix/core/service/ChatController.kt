package com.vahitkeskin.bluenix.core.service

import kotlinx.coroutines.flow.StateFlow

interface ChatController {
    val remoteTypingState: StateFlow<Map<String, Boolean>>
    fun startHosting()

    // YENİ: Aktif sohbeti ayarla (Bildirimleri engellemek için)
    fun setActiveChat(address: String?)
}