package com.vahitkeskin.bluenix.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun ChatImage(path: String, modifier: Modifier) {
    val context = LocalContext.current
    
    // Resim yükleme durumu (Bitmap?)
    val bitmapState = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(path)
                // İçerik URI (content://) ise Resolver kullan
                if (path.startsWith("content://")) {
                     context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } else {
                    // Dosya yolu ise (cache vb.)
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    if (bitmapState.value != null) {
        Image(
            bitmap = bitmapState.value!!,
            contentDescription = "Görüntü",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
