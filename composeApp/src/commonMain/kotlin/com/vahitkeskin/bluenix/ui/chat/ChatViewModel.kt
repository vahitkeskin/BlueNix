package com.vahitkeskin.bluenix.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vahitkeskin.bluenix.core.model.ChatMessage
import com.vahitkeskin.bluenix.core.repository.ChatRepository

import com.vahitkeskin.bluenix.core.service.ChatController
import com.vahitkeskin.bluenix.core.service.FileProcessor
import com.vahitkeskin.bluenix.core.service.LocationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val chatController: ChatController,
    private val fileProcessor: FileProcessor,
    private val locationService: LocationService
) : ViewModel() {

    init {
        chatController.startHosting()
    }

    // Sohbet ekranı açıldığında bu fonksiyon çağrılıyor
    fun setActiveChat(address: String?) {
        chatController.setActiveChat(address)

        if (address != null) {
            println("ChatViewModel: Sohbet açıldı, bağlantı tazeleniyor -> $address")
            connectToDevice(address)
        }
    }

    private fun connectToDevice(address: String) {
        viewModelScope.launch {
            try {
                // Repository üzerinden Client'a "Bağlan" emri ver
                repository.prepareConnection(address)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // YENİ: Mesaj gönderirken de bağlantıyı kontrol et!
    fun sendMessage(address: String, name: String, text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // KRİTİK: Mesaj göndermeden önce bağlantıyı tekrar dürt!
            // Eğer zaten bağlıysa bu işlem çok hızlı geçer, değilse bağlar.
            repository.prepareConnection(address)

            repository.sendMessage(address, name, text)
            repository.sendTypingSignal(address, false)
        }
    }

    fun sendImage(address: String, uriStr: String) {
        viewModelScope.launch {
            try {
                // 1. Process Image
                val bytes = fileProcessor.processImage(uriStr) ?: return@launch
                
                // 2. Connect
                repository.prepareConnection(address)
                
                // 3. Send
                repository.sendFile(address, bytes, "image.jpg", isImage = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendLocation(address: String) {
        viewModelScope.launch {
            try {
                val location = locationService.getCurrentLocation()
                if (location != null) {
                    repository.prepareConnection(address)
                    repository.sendLocation(address, location.latitude, location.longitude)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Veritabanından mesajları anlık çeker
    fun getMessages(address: String): Flow<List<ChatMessage>> {
        return repository.getMessages(address)
    }

    // Karşı tarafın "Yazıyor..." durumunu dinler
    fun isRemoteTyping(address: String): Flow<Boolean> {
        return repository.isRemoteTyping(address)
    }

    // Kullanıcı klavyede yazarken çağrılır
    fun onUserTyping(address: String, isTyping: Boolean) {
        viewModelScope.launch {
            repository.sendTypingSignal(address, isTyping)
        }
    }

    // Mesajları okundu olarak işaretle
    fun markAsRead(address: String) {
        viewModelScope.launch {
            repository.markAsRead(address)
        }
    }
}