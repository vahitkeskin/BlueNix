package com.vahitkeskin.bluenix.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // 1. Detay Ekranı için: Belirli bir cihazla olan tüm mesajlar
    @Query("SELECT * FROM messages WHERE deviceAddress = :address ORDER BY timestamp ASC") // ASC: Eskiden yeniye sırala
    fun getMessages(address: String): Flow<List<MessageEntity>>

    // 2. Liste Ekranı için: Her cihazla yapılan SON konuşma (Gruplanmış)
    @Query("SELECT * FROM messages GROUP BY deviceAddress ORDER BY timestamp DESC")
    fun getLastConversations(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)
}