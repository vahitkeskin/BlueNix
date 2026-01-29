package com.vahitkeskin.bluenix.core.service

import android.util.Log
import com.vahitkeskin.bluenix.core.protocol.PacketType
import com.vahitkeskin.bluenix.core.protocol.TransferProtocol
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

/**
 * Manages the logic of splitting files into chunks and sending them
 * reliably over the AndroidChatClient.
 */
class TransferManager(
    private val client: AndroidChatClient
) {
    private val TAG = "TransferManager"

    suspend fun sendData(address: String, data: ByteArray, type: PacketType, metaData: String = ""): Boolean {
        // 1. Send Header
        val headerString = TransferProtocol.createHeader(type, metaData)
        Log.d(TAG, "📡 Sending Header: $headerString")
        if (!client.sendRawDataSuspend(address, headerString.toByteArray())) {
            return false
        }
        
        // Give receiver time to process header
        delay(100) 

        // 2. Send Data Chunks
        val totalSize = data.size
        // 490 bytes is a safe payload size for 512 byte MTU (minus overhead)
        val chunkSize = TransferProtocol.MAX_CHUNK_SIZE 
        var offset = 0
        var seqNum = 0

        while (offset < totalSize) {
            val end = (offset + chunkSize).coerceAtMost(totalSize)
            val chunk = data.copyOfRange(offset, end)
            
            // Construct Packet: DATA|SEQ|PAYLOAD (Simplified for now, we just send raw bytes for payload)
            // In a pro version, we would wrap this in a protocol. For MVP, we stream raw bytes 
            // after the header, relying on the receiver to count bytes.
            
            // To ensure 100% reliability, we might need an ACK every N packets.
            // For now, we will use a "fire and wait slightly" approach or reliable write if possible.
            // Since we upgraded client to use WRITE_TYPE_DEFAULT (Request response), 
            // every packet serves as an implicit ACK at the GATT layer.
            
            if (!client.sendRawDataSuspend(address, chunk)) {
                Log.e(TAG, "❌ Chunk delivery failed at offset $offset")
                return false
            }
            
            offset += chunkSize
            seqNum++
            
            // Optional: Report progress callback here
            val percent = (offset * 100) / totalSize
            // Log.v(TAG, "Upload: $percent%")
        }
        
        Log.d(TAG, "✅ File transfer complete!")
        return true
    }
}
