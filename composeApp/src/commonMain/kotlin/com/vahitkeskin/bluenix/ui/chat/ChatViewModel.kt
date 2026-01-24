package com.vahitkeskin.bluenix.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vahitkeskin.bluenix.core.model.ChatMessage
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import com.vahitkeskin.bluenix.core.service.ChatController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val chatController: ChatController
) : ViewModel() {

    // Controller'daki 'Yazıyor' durumunu UI'a taşı
    val isRemoteTyping: StateFlow<Boolean> = chatController.isRemoteTyping

    init {
        // ViewModel oluştuğunda sunucuyu (dinlemeyi) başlat
        chatController.startHosting()
    }

    // Mesajları DB'den anlık çek
    fun getMessages(address: String): Flow<List<ChatMessage>> {
        return repository.getMessages(address)
    }

    // Mesaj Gönder
    fun sendMessage(address: String, name: String, text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // 1. Mesajı gönder ve kaydet
            repository.sendMessage(address, name, text)
            // 2. Yazmayı durdur sinyali gönder (Temizlik)
            repository.sendTypingSignal(address, false)
        }
    }

    // Kullanıcı harflere basınca çalışır
    fun onUserTyping(address: String, isTyping: Boolean) {
        viewModelScope.launch {
            repository.sendTypingSignal(address, isTyping)
        }
    }
}