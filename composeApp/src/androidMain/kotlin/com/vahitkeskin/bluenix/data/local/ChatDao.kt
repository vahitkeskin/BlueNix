package com.vahitkeskin.bluenix.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // Tekil mesaj geçmişi için (Burada unreadCount lazım değil)
    @Query("SELECT * FROM messages WHERE deviceAddress = :address ORDER BY timestamp DESC")
    fun getMessages(address: String): Flow<List<MessageEntity>>

    // --- KRİTİK SORGU ---
    // 1. Gruplama: Her cihaz (deviceAddress) için en son eklenen mesajı (MAX(id)) bul.
    // 2. Alt Sorgu (Subquery): O cihazdan gelen, benim atmadığım (isFromMe=0) ve okunmamış (isRead=0) mesajları say.
    // 3. Sonucu 'ConversationTuple' sınıfına doldur.
    @Query("""
        SELECT 
            m.id, 
            m.deviceAddress, 
            m.deviceName, 
            m.text, 
            m.isFromMe, 
            m.timestamp, 
            m.isRead,
            (SELECT COUNT(*) FROM messages AS u WHERE u.deviceAddress = m.deviceAddress AND u.isRead = 0 AND u.isFromMe = 0) AS unreadCount
        FROM messages AS m
        WHERE m.id IN (SELECT MAX(id) FROM messages GROUP BY deviceAddress)
        ORDER BY m.timestamp DESC
    """)
    fun getLastConversations(): Flow<List<ConversationTuple>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    // Sohbeti açınca "Okundu" yap
    @Query("UPDATE messages SET isRead = 1 WHERE deviceAddress = :address AND isRead = 0")
    suspend fun markAsRead(address: String)

    // Toplam okunmamış sayısı (Badge için)
    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0 AND isFromMe = 0")
    fun getUnreadCount(): Flow<Int>
}