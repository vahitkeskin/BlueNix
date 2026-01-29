package com.vahitkeskin.bluenix.core.service

import android.content.Context
import android.net.Uri
import com.vahitkeskin.bluenix.utils.ImageUtils

class AndroidFileProcessor(private val context: Context) : FileProcessor {
    override suspend fun processImage(uriStr: String): ByteArray? {
        val uri = Uri.parse(uriStr)
        return ImageUtils.compressImage(context, uri)
    }
}
