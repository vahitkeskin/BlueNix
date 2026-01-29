package com.vahitkeskin.bluenix.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImagePicker(onImageSelected: (String?) -> Unit): () -> Unit {
    return {
        println("iOS Image Picker not implemented yet")
        onImageSelected(null)
    }
}
