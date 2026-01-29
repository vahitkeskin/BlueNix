package com.vahitkeskin.bluenix.data.repository

import com.vahitkeskin.bluenix.core.model.ChatMessage
import com.vahitkeskin.bluenix.core.model.MessageStatus
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import com.vahitkeskin.bluenix.core.service.AndroidChatClient
import com.vahitkeskin.bluenix.core.service.ChatController
import com.vahitkeskin.bluenix.data.local.ChatDao
import com.vahitkeskin.bluenix.data.local.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AndroidChatRepository(
    private val dao: ChatDao,
    private val client: AndroidChatClient,
    private val controller: ChatController
) : ChatRepository {

    override suspend fun prepareConnection(address: String) {
        // Bağlantıyı başlat (Büyük harf zorunlu)
        client.connect(address.uppercase())
    }

    override fun getMessages(deviceAddress: String): Flow<List<ChatMessage>> {
        return dao.getMessages(deviceAddress.uppercase()).map { list -> list.map { it.toDomain() } }
    }

    override fun getConversations(): Flow<List<ChatMessage>> {
        return dao.getLastConversations().map { list -> list.map { it.toDomain() } }
    }

    override fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()

    override fun isRemoteTyping(address: String): Flow<Boolean> {
        return controller.remoteTypingState.map { it[address.uppercase()] ?: false }
    }

    override suspend fun sendMessage(address: String, name: String, text: String) {
        val safeAddress = address.uppercase() // <-- %100 ÇÖZÜM: Hep büyük harf

        if (name.isNotBlank() && !name.contains("Bilinmeyen", ignoreCase = true)) {
            dao.updateDeviceName(safeAddress, name)
        }

        // 1. Önce DB'ye "GÖNDERİLİYOR" olarak kaydet
        val tempMessage = MessageEntity(
            deviceAddress = safeAddress,
            deviceName = name,
            text = text,
            isFromMe = true,
            timestamp = System.currentTimeMillis(),
            isRead = true,
            status = MessageStatus.SENDING
        )

        val msgId = dao.insert(tempMessage)

        // 2. Bluetooth ile gönder (Sonucu bekle)
        val isSuccess = client.sendRawDataSuspend(safeAddress, text)

        // 3. Sonucu güncelle (Tik veya Hata)
        val finalStatus = if (isSuccess) MessageStatus.SENT else MessageStatus.FAILED
        dao.updateMessageStatus(msgId, finalStatus)
    }

    override suspend fun receiveMessage(address: String, name: String, text: String) {
        val safeAddress = address.uppercase() // <-- BURADA DA BÜYÜK HARF

        if (name.length > 5 && !name.startsWith("Cihaz") && !name.contains("Bilinmeyen")) {
            dao.updateDeviceName(safeAddress, name)
        }

        dao.insert(
            MessageEntity(
                deviceAddress = safeAddress,
                deviceName = name,
                text = text,
                isFromMe = false,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                status = MessageStatus.RECEIVED
            )
        )
    }

    override suspend fun markAsRead(address: String) {
        dao.markAsRead(address.uppercase())
    }

    override fun sendTypingSignal(address: String, isTyping: Boolean) {
        val signal = if (isTyping) "SIG_TYP_START" else "SIG_TYP_STOP"
        CoroutineScope(Dispatchers.IO).launch {
            client.sendRawData(address.uppercase(), signal)
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
            unreadCount = 0,
            status = status
        )
    }

    // ConversationTuple -> Domain çevirici

}