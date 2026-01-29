package com.vahitkeskin.bluenix.core.repository

import com.vahitkeskin.bluenix.core.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    // Sohbet ekranı açıldığında bağlantıyı tazelemek için gerekli
    suspend fun prepareConnection(address: String)

    // 1. Belirli bir kişiyle olan mesaj geçmişi (Anlık)
    fun getMessages(deviceAddress: String): Flow<List<ChatMessage>>

    // 2. Ana Sayfa Sohbet Listesi (Son mesaj, Tarih, Okunmadı Sayısı)
    fun getConversations(): Flow<List<ChatMessage>>

    // 3. Uygulama genelindeki toplam okunmamış mesaj sayısı (Tab/Icon Badge için)
    fun getUnreadCount(): Flow<Int>

    // 4. Karşı tarafın "Yazıyor..." durumunu dinle
    fun isRemoteTyping(address: String): Flow<Boolean>

    // 5. Mesaj Gönder (Hem DB'ye kaydeder hem Bluetooth ile atar)
    suspend fun sendMessage(address: String, name: String, text: String)

    // 6. Mesaj Al (Controller tarafından çağrılır, DB'ye kaydeder)
    suspend fun receiveMessage(address: String, name: String, text: String)
    suspend fun receiveFile(address: String, fileName: String, fileIdx: String /* temp path */, typeId: Int)

    // 7. Sohbeti açınca mesajları okundu işaretle
    suspend fun markAsRead(address: String)

    // 8. "Ben Yazıyorum..." sinyalini karşıya gönder
    fun sendTypingSignal(address: String, isTyping: Boolean)

    // 9. Dosya/Resim Gönder
    suspend fun sendFile(address: String, data: ByteArray, fileName: String, isImage: Boolean)

    // 10. Konum Gönder
    suspend fun sendLocation(address: String, lat: Double, lng: Double)
}