package com.vahitkeskin.bluenix.data.repository

import com.vahitkeskin.bluenix.core.model.ChatMessage
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import com.vahitkeskin.bluenix.core.service.AndroidChatClient
import com.vahitkeskin.bluenix.core.service.AndroidChatController
import com.vahitkeskin.bluenix.data.local.ChatDao
import com.vahitkeskin.bluenix.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AndroidChatRepository(
    private val dao: ChatDao,
    private val client: AndroidChatClient,
    private val controller: AndroidChatController
) : ChatRepository {

    // 1. Sohbet Detayı (Mesajlar)
    override fun getMessages(deviceAddress: String): Flow<List<ChatMessage>> {
        // Burada MessageEntity geliyor, unreadCount'a gerek yok
        return dao.getMessages(deviceAddress).map { list ->
            list.map { it.toDomain() }
        }
    }

    // 2. Sohbet Listesi (Burada unreadCount VAR)
    override fun getConversations(): Flow<List<ChatMessage>> {
        // Burada ConversationTuple geliyor, onun içinde unreadCount zaten var
        return dao.getLastConversations().map { tupleList ->
            tupleList.map { it.toDomain() }
        }
    }

    override fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()

    override fun isRemoteTyping(address: String): Flow<Boolean> {
        return controller.remoteTypingState.map { typingMap ->
            typingMap[address] ?: false
        }
    }

    override suspend fun sendMessage(address: String, name: String, text: String) {
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
        client.sendRawData(address, text)
    }

    override suspend fun receiveMessage(address: String, name: String, text: String) {
        dao.insert(
            MessageEntity(
                deviceAddress = address,
                deviceName = name,
                text = text,
                isFromMe = false,
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
        )
    }

    override suspend fun markAsRead(address: String) {
        dao.markAsRead(address)
    }

    override fun sendTypingSignal(address: String, isTyping: Boolean) {
        val signal = if (isTyping) "SIG_TYP_START" else "SIG_TYP_STOP"
        client.sendRawData(address, signal)
    }

    // --- DÜZELTME BURADA ---
    private fun MessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id.toString(),
            text = text,
            isFromMe = isFromMe,
            timestamp = timestamp,
            deviceName = deviceName,
            deviceAddress = deviceAddress,
            // HATA ÇÖZÜMÜ: MessageEntity tek bir mesajdır, okunmamış sayısı tutmaz.
            // Sohbet detayında badge göstermediğimiz için buraya 0 veriyoruz.
            unreadCount = 0
        )
    }
}