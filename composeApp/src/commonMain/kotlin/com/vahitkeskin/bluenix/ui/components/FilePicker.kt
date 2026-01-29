package com.vahitkeskin.bluenix.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFilePicker(onFileSelected: (String?) -> Unit): () -> Unit
