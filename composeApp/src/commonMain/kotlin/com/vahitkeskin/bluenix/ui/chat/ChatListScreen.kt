package com.vahitkeskin.bluenix.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vahitkeskin.bluenix.core.model.ChatMessage
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (String, String) -> Unit
) {
    val viewModel = koinViewModel<ChatHistoryViewModel>() // ViewModel isminize göre ayarlayın
    val conversations by viewModel.conversations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BlueNix Mesajlar", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF050B14),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF050B14)
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Henüz mesajlaşma yok.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(conversations) { message ->
                    ConversationItem(
                        message = message,
                        onClick = {
                            onChatClick(message.deviceName, message.deviceAddress)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    message: ChatMessage,
    onClick: () -> Unit
) {
    // Tasarım Renkleri
    val CardBg = Color(0xFF111B2E)
    val NeonBlue = Color(0xFF00F2FF)
    val UnreadColor = Color(0xFF00C853) // Canlı Yeşil
    val TextColor = if (message.unreadCount > 0) Color.White else Color.LightGray
    val FontWeightStyle = if (message.unreadCount > 0) FontWeight.Bold else FontWeight.Normal

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AVATAR (İsim Baş Harfi)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (message.unreadCount > 0) NeonBlue else Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.deviceName.take(1).uppercase(),
                    color = if (message.unreadCount > 0) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // İSİM, MESAJ ve ZAMAN
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message.deviceName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = formatTimestamp(message.timestamp),
                        color = if (message.unreadCount > 0) NeonBlue else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeightStyle
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.isFromMe) {
                        Text("Sen: ", color = Color.Gray, fontSize = 14.sp)
                    }
                    Text(
                        text = message.text,
                        color = TextColor,
                        fontWeight = FontWeightStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                }
            }

            // OKUNMAMIŞ MESAJ SAYISI (BADGE)
            if (message.unreadCount > 0) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(UnreadColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}