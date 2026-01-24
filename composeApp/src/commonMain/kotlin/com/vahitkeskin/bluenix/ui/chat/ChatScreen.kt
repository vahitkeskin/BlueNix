package com.vahitkeskin.bluenix.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vahitkeskin.bluenix.core.model.ChatMessage
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    targetDeviceName: String,
    targetDeviceAddress: String,
    onBackClick: () -> Unit
) {
    val viewModel = koinViewModel<ChatViewModel>()
    // Mesajlar DB'den geliyor
    val messages by viewModel.getMessages(targetDeviceAddress).collectAsState(initial = emptyList())
    // Yazıyor durumu Controller'dan geliyor
    val isRemoteTyping by viewModel.isRemoteTyping.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Mesaj gelince en alta kaydır
    LaunchedEffect(messages.size, isRemoteTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + (if (isRemoteTyping) 1 else 0))
        }
    }

    val NeonBlue = Color(0xFF00F2FF)
    val DarkBg = Color(0xFF050B14)
    val BubbleMy = Color(0xFF112240)
    val BubbleOther = Color(0xFF1A1A1A)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(targetDeviceName, color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(targetDeviceAddress, color = Color.Gray, fontSize = 10.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = NeonBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // --- MESAJ LİSTESİ ---
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg, NeonBlue, BubbleMy, BubbleOther)
                }

                // Yazıyor Animasyonu
                if (isRemoteTyping) {
                    item {
                        TypingIndicator(NeonBlue, BubbleOther)
                    }
                }
            }

            // --- GİRİŞ ALANI ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        // Her tuşlamada sinyal gönder
                        viewModel.onUserTyping(targetDeviceAddress, it.isNotEmpty())
                    },
                    placeholder = { Text("Encrypted message...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = NeonBlue,
                        focusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        viewModel.sendMessage(targetDeviceAddress, targetDeviceName, inputText)
                        inputText = ""
                    },
                    modifier = Modifier.background(NeonBlue, CircleShape)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, neon: Color, myColor: Color, otherColor: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (message.isFromMe) myColor else otherColor),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (message.isFromMe) 16.dp else 4.dp,
                bottomEnd = if (message.isFromMe) 4.dp else 16.dp
            )
        ) {
            Text(text = message.text, color = Color.White, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
fun TypingIndicator(neon: Color, bgColor: Color) {
    val alpha = rememberInfiniteTransition().animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse)
    )
    Row(
        modifier = Modifier.padding(vertical = 4.dp).background(bgColor, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Typing", color = neon, fontSize = 12.sp)
        Text("...", color = neon, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(alpha.value))
    }
}