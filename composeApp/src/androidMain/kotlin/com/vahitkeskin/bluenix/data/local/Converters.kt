package com.vahitkeskin.bluenix.data.local

import androidx.room.TypeConverter
import com.vahitkeskin.bluenix.core.model.MessageStatus

class Converters {
    @TypeConverter
    fun fromStatus(status: MessageStatus): String {
        return status.name // Veritabanına "SENT", "FAILED" gibi yazı olarak kaydeder
    }

    @TypeConverter
    fun toStatus(value: String): MessageStatus {
        return try {
            MessageStatus.valueOf(value) // Veritabanından okurken Enum'a çevirir
        } catch (e: Exception) {
            MessageStatus.SENT // Hata olursa varsayılan değer
        }
    }
}