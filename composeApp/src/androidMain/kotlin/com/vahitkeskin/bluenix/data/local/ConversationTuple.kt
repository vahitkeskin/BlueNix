package com.vahitkeskin.bluenix.data.local

import androidx.room.ColumnInfo
import com.vahitkeskin.bluenix.core.model.ChatMessage

/**
 * Bu sınıf bir veritabanı tablosu DEĞİLDİR.
 * ChatDao içindeki özel SQL sorgusunun sonucunu tutan bir veri kalıbıdır.
 */
data class ConversationTuple(
    val id: Long,
    val deviceAddress: String,
    val deviceName: String,
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long,
    val isRead: Boolean,

    // SQL sorgusunda hesaplayıp buraya dolduracağız
    @ColumnInfo(name = "unreadCount")
    val unreadCount: Int
) {
    // Domain modeline dönüştürücü
    fun toDomain(): ChatMessage {
        return ChatMessage(
            id = id.toString(),
            text = text,
            isFromMe = isFromMe,
            timestamp = timestamp,
            deviceName = deviceName,
            deviceAddress = deviceAddress,
            unreadCount = unreadCount // Hesaplanan değer buraya aktarılıyor
        )
    }
}