package com.vahitkeskin.bluenix.core.repository

import com.vahitkeskin.bluenix.core.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // Mesajları getir (DB'den)
    fun getMessages(deviceAddress: String): Flow<List<ChatMessage>>

    // Mesaj Gönder (Hem BLE'ye at hem DB'ye yaz)
    suspend fun sendMessage(address: String, name: String, text: String)

    // Mesaj Alındı (DB'ye yaz)
    suspend fun receiveMessage(address: String, name: String, text: String)

    // Yazıyor bilgisini gönder
    fun sendTypingSignal(address: String, isTyping: Boolean)

    fun getConversations(): Flow<List<ChatMessage>> // YENİ
}