package com.vahitkeskin.bluenix.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vahitkeskin.bluenix.core.model.ChatMessage
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import com.vahitkeskin.bluenix.core.service.ChatController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val chatController: ChatController
) : ViewModel() {

    init {
        // ViewModel başladığında sunucunun (GATT Server) hazır olduğundan emin ol
        chatController.startHosting()
    }

    // 1. "Yazıyor..." durumunu dinle (Adrese özel filtreleme Repository içinde yapılır)
    fun isRemoteTyping(address: String): Flow<Boolean> {
        return repository.isRemoteTyping(address)
    }

    // 2. Mesajları çek
    fun getMessages(address: String): Flow<List<ChatMessage>> {
        return repository.getMessages(address)
    }

    // 3. Mesaj Gönder
    fun sendMessage(address: String, name: String, text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Önce DB'ye kaydet ve gönder
            repository.sendMessage(address, name, text)
            // Gönderim yapıldığı için "Yazıyor" sinyalini durdur
            repository.sendTypingSignal(address, false)
        }
    }

    // 4. Kullanıcı yazarken sinyal gönder
    fun onUserTyping(address: String, isTyping: Boolean) {
        viewModelScope.launch {
            repository.sendTypingSignal(address, isTyping)
        }
    }

    // 5. Mesajları okundu olarak işaretle
    fun markAsRead(address: String) {
        viewModelScope.launch {
            repository.markAsRead(address)
        }
    }

    fun setActiveChat(address: String?) {
        chatController.setActiveChat(address)
    }
}