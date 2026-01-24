package com.vahitkeskin.bluenix.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vahitkeskin.bluenix.ui.theme.NeonBlue // Tema renginiz

@Composable
fun SignalStrengthIndicator(
    level: Int, // 0 ile 4 arası değer
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 4 Adet Çubuk Çiziyoruz
        for (i in 1..4) {
            val isActive = i <= level
            val barHeight = (i * 4).dp // Çubuk boyları: 4, 8, 12, 16 dp

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .background(
                        color = if (isActive) NeonBlue else Color.Gray.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}