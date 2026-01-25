package com.vahitkeskin.bluenix.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vahitkeskin.bluenix.core.model.ChatMessage
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatHistoryViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    // Veritabanından gelen sohbet listesini (Son mesaj, okunmadı sayısı vb.) canlı tutar
    val conversations: StateFlow<List<ChatMessage>> = repository.getConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Listeden bir sohbete tıklandığında okunmadı sayısını sıfırlamak için
    fun markAsRead(address: String) {
        viewModelScope.launch {
            repository.markAsRead(address)
        }
    }
}