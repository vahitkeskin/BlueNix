package com.vahitkeskin.bluenix.core.model

import java.util.UUID

// 1. Durum Enum'ı (Domain katmanında da olmalı)
// 1. Durum Enum'ı (Domain katmanında da olmalı)
enum class MessageStatus { SENDING, SENT, FAILED, RECEIVED }
enum class MessageType { TEXT, IMAGE, FILE, LOCATION }

// 2. ChatMessage Modeli
data class ChatMessage(
    val id: String = generateRandomId(),
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = getCurrentTime(),
    val deviceName: String = "",
    val deviceAddress: String = "",
    val unreadCount: Int = 0,
    val status: MessageStatus = MessageStatus.SENT,
    val type: MessageType = MessageType.TEXT,
    val attachmentPath: String? = null
)

// 3. Helper Fonksiyonlar (Düzeltildi)
fun generateRandomId(): String = UUID.randomUUID().toString()

fun getCurrentTime(): Long = System.currentTimeMillis()