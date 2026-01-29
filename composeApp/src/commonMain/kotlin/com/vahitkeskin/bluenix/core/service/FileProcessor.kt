package com.vahitkeskin.bluenix.core.service

interface FileProcessor {
    suspend fun processImage(uriStr: String): ByteArray?
}
