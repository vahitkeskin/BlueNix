package com.vahitkeskin.bluenix.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun rememberImagePicker(onImageSelected: (String?) -> Unit): () -> Unit
