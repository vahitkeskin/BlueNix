package com.vahitkeskin.bluenix.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String, // Hangi cihazla konuşuyoruz?
    val deviceName: String,
    val text: String,
    val isFromMe: Boolean, // Ben mi attım?
    val timestamp: Long,
    val isRead: Boolean = false, // Okundu mu?
    val messageType: String = "TEXT" // TEXT, IMAGE, FILE vs.
)