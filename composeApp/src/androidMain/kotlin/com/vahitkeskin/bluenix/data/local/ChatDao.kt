package com.vahitkeskin.bluenix.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vahitkeskin.bluenix.core.model.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM messages WHERE deviceAddress = :address ORDER BY timestamp DESC")
    fun getMessages(address: String): Flow<List<MessageEntity>>

    // --- %100 ÇÖZÜM: GRUPLAMA SORGUSU ---
    // En son mesajı almak için güvenli bir alt sorgu (subquery) kullanıyoruz.
    // 'groups' yerine 'sub' ismini kullandık ki hata vermesin.
    @Query("""
        SELECT 
            m.id, m.deviceAddress, m.deviceName, m.text, m.isFromMe, m.timestamp, m.isRead,
            (SELECT COUNT(*) FROM messages AS u WHERE u.deviceAddress = m.deviceAddress AND u.isRead = 0 AND u.isFromMe = 0) AS unreadCount
        FROM messages AS m
        INNER JOIN (
            SELECT MAX(timestamp) as max_date, deviceAddress
            FROM messages
            GROUP BY deviceAddress
        ) as sub ON m.deviceAddress = sub.deviceAddress AND m.timestamp = sub.max_date
        ORDER BY m.timestamp DESC
    """)
    fun getLastConversations(): Flow<List<ConversationTuple>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus)

    @Query("UPDATE messages SET deviceName = :newName WHERE deviceAddress = :address")
    suspend fun updateDeviceName(address: String, newName: String)

    @Query("UPDATE messages SET isRead = 1 WHERE deviceAddress = :address AND isRead = 0")
    suspend fun markAsRead(address: String)

    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0 AND isFromMe = 0")
    fun getUnreadCount(): Flow<Int>
}