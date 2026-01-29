package com.vahitkeskin.bluenix.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun ChatImage(path: String, modifier: Modifier) {
    Text("Image not supported on iOS yet", modifier = modifier)
}
