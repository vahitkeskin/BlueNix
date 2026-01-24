package com.vahitkeskin.bluenix.core.model

data class ChatMessage(
    val id: String = "",
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = 0L,
    val deviceName: String = "",   // Yeni Eklendi
    val deviceAddress: String = "" // Yeni Eklendi
)

// Ortak platformda UUID olmadığı için basit bir random ID üreteci
fun generateRandomId(): String = (0..100000).random().toString()
// Şimdilik basit timestamp
fun getCurrentTime(): Long = 0L // Gerçek bir Clock implementasyonu eklenebilir