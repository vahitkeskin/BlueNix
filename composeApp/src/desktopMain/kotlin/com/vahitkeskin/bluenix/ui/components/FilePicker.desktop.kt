package com.vahitkeskin.bluenix.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePicker(onFileSelected: (String?) -> Unit): () -> Unit {
    return {
        println("Desktop File Picker not implemented yet")
        onFileSelected(null)
    }
}
