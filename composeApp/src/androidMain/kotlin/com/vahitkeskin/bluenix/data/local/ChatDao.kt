package com.vahitkeskin.bluenix.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM messages WHERE deviceAddress = :address ORDER BY timestamp ASC")
    fun getMessages(address: String): Flow<List<MessageEntity>>

    // LİSTE EKRANI İÇİN EN KRİTİK SORGU
    // Her cihazdan gelen en son (MAX ID) mesajı getirir.
    @Query("SELECT * FROM messages WHERE id IN (SELECT MAX(id) FROM messages GROUP BY deviceAddress) ORDER BY timestamp DESC")
    fun getLastConversations(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0 AND isFromMe = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE messages SET isRead = 1 WHERE deviceAddress = :address")
    suspend fun markAsRead(address: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)
}