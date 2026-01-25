package com.vahitkeskin.bluenix.core.repository

import com.vahitkeskin.bluenix.core.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // 1. Belirli bir kişiyle olan mesaj geçmişi (Anlık)
    fun getMessages(deviceAddress: String): Flow<List<ChatMessage>>

    // 2. Ana Sayfa Sohbet Listesi (Son mesaj, Tarih, Okunmadı Sayısı)
    fun getConversations(): Flow<List<ChatMessage>>

    // 3. Uygulama genelindeki toplam okunmamış mesaj sayısı (Tab/Icon Badge için)
    fun getUnreadCount(): Flow<Int>

    // 4. Karşı tarafın "Yazıyor..." durumunu dinle (Instagram Tarzı için GEREKLİ)
    // Bu fonksiyon Controller'daki anlık durumu UI'a taşır.
    fun isRemoteTyping(address: String): Flow<Boolean>

    // 5. Mesaj Gönder (Hem DB'ye kaydeder hem Bluetooth ile atar)
    suspend fun sendMessage(address: String, name: String, text: String)

    // 6. Mesaj Al (Controller tarafından çağrılır, DB'ye kaydeder)
    suspend fun receiveMessage(address: String, name: String, text: String)

    // 7. Sohbeti açınca mesajları okundu işaretle
    suspend fun markAsRead(address: String)

    // 8. "Ben Yazıyorum..." sinyalini karşıya gönder
    fun sendTypingSignal(address: String, isTyping: Boolean)
}