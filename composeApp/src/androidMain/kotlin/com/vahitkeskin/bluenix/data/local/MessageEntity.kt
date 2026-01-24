package com.vahitkeskin.bluenix.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val deviceName: String,
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long,
    val isRead: Boolean = true // Varsayılan: Okundu (Kendi mesajlarımız için)
)