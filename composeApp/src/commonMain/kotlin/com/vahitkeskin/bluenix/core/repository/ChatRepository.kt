package com.vahitkeskin.bluenix.core.repository

import com.vahitkeskin.bluenix.core.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getMessages(deviceAddress: String): Flow<List<ChatMessage>>
    fun getConversations(): Flow<List<ChatMessage>> // Liste için
    fun getUnreadCount(): Flow<Int> // Badge için

    suspend fun sendMessage(address: String, name: String, text: String)
    suspend fun receiveMessage(address: String, name: String, text: String)
    suspend fun markAsRead(address: String) // Okundu yap

    fun sendTypingSignal(address: String, isTyping: Boolean)
}