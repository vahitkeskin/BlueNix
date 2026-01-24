package com.vahitkeskin.bluenix.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Bir Composable'a Status Bar yüksekliği kadar üstten boşluk (padding) ekler.
 * Kullanım: Modifier.safeStatusBarPadding()
 */
@Composable
fun Modifier.safeStatusBarPadding(): Modifier = this.windowInsetsPadding(WindowInsets.statusBars)

/**
 * Status Bar'ın o anki yüksekliğini DP cinsinden döndürür.
 * Manuel hesaplamalar için kullanılabilir.
 * Kullanım: val topPadding = getStatusBarHeight()
 */
@Composable
fun getStatusBarHeight(): Dp {
    return WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
}