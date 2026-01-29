package com.vahitkeskin.bluenix.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

object ImageUtils {
    
    // BLE is slow, so we target very small file sizes (~50-100KB)
    // while maintaining "WhatsApp preview" quality.
    private const val MAX_FILE_SIZE_BYTES = 100 * 1024 // 100 KB target

    fun compressImage(context: Context, uri: Uri): ByteArray? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (originalBitmap == null) return null

            // 1. Resize if too big (e.g. > 1280px)
            var scaledBitmap = scaleBitmapDown(originalBitmap, 1280)

            // 2. Compress Loop
            var quality = 90
            var stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

            while (stream.toByteArray().size > MAX_FILE_SIZE_BYTES && quality > 10) {
                stream.close()
                stream = ByteArrayOutputStream()
                quality -= 10
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            
            return stream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = originalWidth
        var resizedHeight = originalHeight

        if (originalHeight > originalWidth) {
            if (originalHeight > maxDimension) {
                resizedHeight = maxDimension
                resizedWidth = (resizedHeight * originalWidth.toFloat() / originalHeight.toFloat()).toInt()
            }
        } else {
            if (originalWidth > maxDimension) {
                resizedWidth = maxDimension
                resizedHeight = (resizedWidth * originalHeight.toFloat() / originalWidth.toFloat()).toInt()
            }
        }

        if (resizedWidth == originalWidth && resizedHeight == originalHeight) return bitmap
        
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, true)
    }
}
