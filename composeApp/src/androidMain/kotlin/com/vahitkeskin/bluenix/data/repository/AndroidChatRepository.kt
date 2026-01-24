package com.vahitkeskin.bluenix.data.repository

import com.vahitkeskin.bluenix.core.model.ChatMessage
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import com.vahitkeskin.bluenix.core.service.AndroidChatClient
import com.vahitkeskin.bluenix.data.local.ChatDao
import com.vahitkeskin.bluenix.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AndroidChatRepository(
    private val dao: ChatDao,
    private val client: AndroidChatClient
) : ChatRepository {

    override fun getMessages(deviceAddress: String): Flow<List<ChatMessage>> {
        return dao.getMessages(deviceAddress).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getConversations(): Flow<List<ChatMessage>> {
        return dao.getLastConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()

    override suspend fun markAsRead(address: String) {
        dao.markAsRead(address)
    }

    // GÖNDERDİĞİM MESAJ (Benim Ekranım İçin)
    override suspend fun sendMessage(address: String, name: String, text: String) {
        // Önce Bluetooth ile gönder
        client.sendRawData(address, text)

        // Sonra Local DB'ye yaz
        dao.insert(
            MessageEntity(
                deviceAddress = address,
                deviceName = name,
                text = text,
                isFromMe = true,
                timestamp = System.currentTimeMillis(),
                isRead = true
            )
        )
    }

    // ALDIĞIM MESAJ (Karşıdan Gelen)
    override suspend fun receiveMessage(address: String, name: String, text: String) {
        dao.insert(
            MessageEntity(
                deviceAddress = address,
                deviceName = name,
                text = text,
                isFromMe = false, // <-- Burası FALSE olmalı
                timestamp = System.currentTimeMillis(),
                isRead = false // <-- Burası FALSE olmalı (Okunmadı)
            )
        )
    }

    override fun sendTypingSignal(address: String, isTyping: Boolean) {
        val signal = if (isTyping) "SIG_TYP_START" else "SIG_TYP_STOP"
        client.sendRawData(address, signal)
    }

    private fun MessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id.toString(),
            text = text,
            isFromMe = isFromMe,
            timestamp = timestamp,
            deviceName = deviceName,
            deviceAddress = deviceAddress
        )
    }
}