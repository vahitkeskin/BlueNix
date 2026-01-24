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

    // Veritabanındaki mesajları canlı olarak UI (ChatMessage) modeline dönüştürür
    override fun getMessages(deviceAddress: String): Flow<List<ChatMessage>> {
        return dao.getMessages(deviceAddress).map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id.toString(),
                    text = entity.text,
                    isFromMe = entity.isFromMe,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    // Mesaj GÖNDERME işlemi: Hem Bluetooth'a atar hem veritabanına yazar
    override suspend fun sendMessage(address: String, name: String, text: String) {
        // 1. Bluetooth ile karşıya gönder (Client'taki güncel metodumuz)
        client.sendRawData(address, text)

        // 2. Kendi geçmişimize kaydet
        dao.insert(
            MessageEntity(
                deviceAddress = address,
                deviceName = name,
                text = text,
                isFromMe = true,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // Mesaj ALMA işlemi: Controller'dan gelen mesajı sadece veritabanına yazar
    override suspend fun receiveMessage(address: String, name: String, text: String) {
        dao.insert(
            MessageEntity(
                deviceAddress = address,
                deviceName = name,
                text = text,
                isFromMe = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // Yazıyor Sinyali: Veritabanına YAZMAZ, sadece Bluetooth ile sinyal atar
    override fun sendTypingSignal(address: String, isTyping: Boolean) {
        val signal = if (isTyping) "SIG_TYP_START" else "SIG_TYP_STOP"
        client.sendRawData(address, signal)
    }

    override fun getConversations(): Flow<List<ChatMessage>> {
        return dao.getLastConversations().map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id.toString(),
                    text = entity.text,
                    isFromMe = entity.isFromMe,
                    timestamp = entity.timestamp,
                    deviceName = entity.deviceName,
                    deviceAddress = entity.deviceAddress
                )
            }
        }
    }
}