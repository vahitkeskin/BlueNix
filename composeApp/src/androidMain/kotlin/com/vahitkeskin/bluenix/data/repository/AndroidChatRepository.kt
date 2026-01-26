package com.vahitkeskin.bluenix.data.repository

import com.vahitkeskin.bluenix.core.model.ChatMessage
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import com.vahitkeskin.bluenix.core.service.AndroidChatClient
import com.vahitkeskin.bluenix.core.service.ChatController // DİKKAT: Interface import edildi
import com.vahitkeskin.bluenix.data.local.ChatDao
import com.vahitkeskin.bluenix.data.local.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AndroidChatRepository(
    private val dao: ChatDao,
    private val client: AndroidChatClient,
    private val controller: ChatController
) : ChatRepository {

    override suspend fun prepareConnection(address: String) {
        client.connect(address)
    }

    override fun getMessages(deviceAddress: String): Flow<List<ChatMessage>> {
        return dao.getMessages(deviceAddress).map { list -> list.map { it.toDomain() } }
    }

    override fun getConversations(): Flow<List<ChatMessage>> {
        return dao.getLastConversations().map { list -> list.map { it.toDomain() } }
    }

    override fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()

    override fun isRemoteTyping(address: String): Flow<Boolean> {
        return controller.remoteTypingState.map { it[address] ?: false }
    }

    override suspend fun sendMessage(address: String, name: String, text: String) {
        if (name.isNotBlank() && !name.contains("Bilinmeyen", ignoreCase = true)) {
            dao.updateDeviceName(address, name)
        }

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
        if (name.length > 5 && !name.startsWith("Cihaz") && !name.contains("Bilinmeyen")) {
            dao.updateDeviceName(address, name)
        }

        dao.insert(
            MessageEntity(
                deviceAddress = address,
                deviceName = name, // <-- Burası artık doğru isimle kaydolacak
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
        CoroutineScope(Dispatchers.IO).launch {
            client.sendRawData(address, signal)
        }
    }

    private fun MessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id.toString(),
            text = text,
            isFromMe = isFromMe,
            timestamp = timestamp,
            deviceName = deviceName,
            deviceAddress = deviceAddress,
            unreadCount = 0
        )
    }
}