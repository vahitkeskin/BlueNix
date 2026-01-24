package com.vahitkeskin.bluenix.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vahitkeskin.bluenix.core.model.ChatMessage
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ChatHistoryViewModel(
    repository: ChatRepository
) : ViewModel() {

    // Veritabanından gelen son konuşmaları StateFlow'a çeviriyoruz
    val conversations: StateFlow<List<ChatMessage>> = repository.getConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}