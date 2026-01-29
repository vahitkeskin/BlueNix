package com.vahitkeskin.bluenix.core.service

import android.content.Context
import android.util.Log
import com.vahitkeskin.bluenix.core.protocol.PacketType
import com.vahitkeskin.bluenix.core.protocol.TransferProtocol
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

sealed class ReceiveResult {
    data class Progress(val percent: Int) : ReceiveResult()
    data class FileReady(val file: File, val fileName: String, val type: PacketType) : ReceiveResult()
    data object None : ReceiveResult()
}

class ReceiveManager(private val context: Context) {
    private val TAG = "ReceiveManager"
    private val activeTransfers = ConcurrentHashMap<String, TransferState>()

    data class TransferState(
        val type: PacketType,
        val fileName: String,
        val totalSize: Long,
        var currentSize: Long = 0,
        val outputFile: File,
        val stream: FileOutputStream
    )

    fun processPacket(address: String, data: ByteArray): ReceiveResult {
        // 1. Check if it is a Header
        // HEAD|||...
        val dataString = String(data) // Preview
        if (dataString.startsWith("HEAD${TransferProtocol.DELIMITER}")) {
            Log.d(TAG, "📡 Header received from $address: $dataString")
            val header = TransferProtocol.parseHeader(dataString)
            if (header != null) {
                val (type, metaData) = header
                // metaData: fileName|size
                val parts = metaData.split("|")
                val fileName = parts.getOrNull(0) ?: "unknown"
                val size = parts.getOrNull(1)?.toLongOrNull() ?: 0L

                // Start new transfer
                try {
                    val file = File(context.cacheDir, "rx_${System.currentTimeMillis()}_$fileName")
                    val stream = FileOutputStream(file)
                    
                    // Close old if exists
                    activeTransfers[address]?.stream?.close()
                    
                    val state = TransferState(type, fileName, size, 0, file, stream)
                    activeTransfers[address] = state
                    return ReceiveResult.Progress(0)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create receive file", e)
                }
            }
            return ReceiveResult.None
        }

        // 2. Check active transfer
        val state = activeTransfers[address] ?: return ReceiveResult.None

        try {
            // Append chunk
            state.stream.write(data)
            state.currentSize += data.size
            
            val percent = if (state.totalSize > 0) ((state.currentSize * 100) / state.totalSize).toInt() else 0

            if (state.currentSize >= state.totalSize) {
                Log.i(TAG, "✅ File receive complete from $address")
                state.stream.close()
                activeTransfers.remove(address)
                return ReceiveResult.FileReady(state.outputFile, state.fileName, state.type)
            }
            
            return ReceiveResult.Progress(percent)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing chunk", e)
            state.stream.close()
            activeTransfers.remove(address)
            return ReceiveResult.None
        }
    }
    
    fun isActive(address: String): Boolean {
        return activeTransfers.containsKey(address)
    }
}
