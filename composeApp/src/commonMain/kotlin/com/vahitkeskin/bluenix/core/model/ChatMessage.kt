package com.vahitkeskin.bluenix.core.model

data class ChatMessage(
    val id: String = "",
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = 0L,
    val deviceName: String = "",
    val deviceAddress: String = "",
    val unreadCount: Int = 0 // <-- YENİ: Listede rozet (badge) göstermek için
)

// Ortak platformda UUID olmadığı için basit bir random ID üreteci
fun generateRandomId(): String = (0..100000).random().toString()
// Şimdilik basit timestamp
fun getCurrentTime(): Long = 0L // Gerçek bir Clock implementasyonu eklenebilir