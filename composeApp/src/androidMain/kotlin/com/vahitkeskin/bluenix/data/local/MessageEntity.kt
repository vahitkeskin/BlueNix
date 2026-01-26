package com.vahitkeskin.bluenix.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vahitkeskin.bluenix.core.model.MessageStatus

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val deviceName: String,
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long,
    val isRead: Boolean = false,
    val messageType: String = "TEXT",
    val status: MessageStatus = MessageStatus.SENT
)