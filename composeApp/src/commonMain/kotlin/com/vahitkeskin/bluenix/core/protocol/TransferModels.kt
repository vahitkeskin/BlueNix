package com.vahitkeskin.bluenix.core.protocol

import kotlinx.serialization.Serializable

enum class PacketType(val id: Int) {
    TEXT(0),
    IMAGE_HEADER(1),
    AUDIO_HEADER(2),
    FILE_HEADER(3),
    LOCATION(4),
    DATA_CHUNK(5),
    ACK(6),
    END(7);

    companion object {
        fun fromId(id: Int): PacketType = entries.find { it.id == id } ?: TEXT
    }
}

@Serializable
data class FileHeader(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val checksum: Long // Simple CRC32 or similar
)

// Basit bir protokol yardımcı sınıfı
object TransferProtocol {
    const val DELIMITER = "|||"
    // Maksimum BLE Paket boyutu (MTU overhead düşüldükten sonra güvenli alan)
    const val MAX_CHUNK_SIZE = 490 

    fun createHeader(type: PacketType, metaData: String): String {
        return "HEAD$DELIMITER${type.id}$DELIMITER$metaData"
    }

    fun parseHeader(data: String): Pair<PacketType, String>? {
        if (!data.startsWith("HEAD$DELIMITER")) return null
        val parts = data.split(DELIMITER, limit = 3)
        if (parts.size < 3) return null
        val typeId = parts[1].toIntOrNull() ?: return null
        return PacketType.fromId(typeId) to parts[2]
    }
}
