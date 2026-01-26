package com.vahitkeskin.bluenix.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vahitkeskin.bluenix.core.model.ChatMessage
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    targetDeviceName: String,
    targetDeviceAddress: String,
    onBackClick: () -> Unit
) {
    val viewModel = koinViewModel<ChatViewModel>()

    // Veritabanından gelen mesajları canlı dinle
    val messages by viewModel.getMessages(targetDeviceAddress).collectAsState(initial = emptyList())

    // Karşı tarafın "Yazıyor..." bilgisini canlı dinle
    val isRemoteTyping by viewModel.isRemoteTyping(targetDeviceAddress).collectAsState(initial = false)

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // --- 1. BİLDİRİM YÖNETİMİ (KRİTİK) ---
    // Bu ekrana girildiğinde Controller'a "Aktif sohbet bu" bilgisini veriyoruz.
    // Böylece AndroidChatController, bu adresten mesaj gelirse bildirim OLUŞTURMAZ.
    DisposableEffect(Unit) {
        // Ekran açıldı -> Bağlan
        viewModel.setActiveChat(targetDeviceAddress)
        onDispose {
            // Ekran kapandı -> Aktif sohbeti temizle
            viewModel.setActiveChat(null)
        }
    }

    // --- 2. OKUNDU BİLGİSİ VE OTOMATİK KAYDIRMA ---
    LaunchedEffect(messages.size) {
        // Yeni mesaj geldiyse veya ekran açıldıysa okundu yap
        viewModel.markAsRead(targetDeviceAddress)
        if (messages.isNotEmpty()) {
            // En yeni mesaja (Liste ters olduğu için index 0) kaydır
            listState.animateScrollToItem(0)
        }
    }

    // --- 3. "YAZIYOR..." SİNYALİ GÖNDERME ---
    // Kullanıcı yazı yazarken karşı tarafa sinyal gönderir (Debounce: 3sn duraksarsa keser)
    LaunchedEffect(inputText) {
        if (inputText.isNotEmpty()) {
            viewModel.onUserTyping(targetDeviceAddress, true)
            delay(3000)
            viewModel.onUserTyping(targetDeviceAddress, false)
        } else {
            viewModel.onUserTyping(targetDeviceAddress, false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // Burada görünen isim, Home ekranından tıklanan "Gerçek İsim"dir.
                        Text(
                            text = targetDeviceName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // INSTAGRAM TARZI YAZIYOR EFEKTİ
                        AnimatedVisibility(
                            visible = isRemoteTyping,
                            enter = fadeIn(animationSpec = tween(300)),
                            exit = fadeOut(animationSpec = tween(300))
                        ) {
                            Text(
                                "yazıyor...",
                                color = Color(0xFF00F2FF), // Neon Mavi
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111B2E))
            )
        },
        containerColor = Color(0xFF050B14)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- MESAJ LİSTESİ ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                reverseLayout = true // Chat standartı: En yeni mesaj altta (LazyColumn'da 0. index)
            ) {
                // Karşı taraf yazıyorsa en alta baloncuk koy (İsteğe bağlı)
                if (isRemoteTyping) {
                    item { TypingBubble() }
                }

                items(messages) { msg ->
                    MessageBubble(msg)
                }
            }

            // --- GİRİŞ ALANI ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111B2E))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Mesaj yaz...", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(targetDeviceAddress, targetDeviceName, inputText)
                            inputText = ""
                            viewModel.onUserTyping(targetDeviceAddress, false)
                        }
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF00F2FF),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(targetDeviceAddress, targetDeviceName, inputText)
                            inputText = ""
                            viewModel.onUserTyping(targetDeviceAddress, false) // Temizlik
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gönder",
                        tint = if (inputText.isNotBlank()) Color(0xFF00F2FF) else Color.Gray
                    )
                }
            }
        }
    }
}

// --- YARDIMCI BİLEŞENLER ---

@Composable
fun MessageBubble(message: ChatMessage) {
    val isMe = message.isFromMe
    val align = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    // Renkler: Sizinki Koyu Yeşil (WhatsApp tarzı), Karşı taraf Koyu Gri
    val color = if (isMe) Color(0xFF005C4B) else Color(0xFF1F2C34)

    // Balon Şekli: Konuşma yönüne göre köşeleri yuvarla
    val shape = if (isMe) RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)
    else RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = align
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 80.dp, max = 280.dp)
                .clip(shape)
                .background(color)
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 16.sp
            )

            // Saat Bilgisi
            Text(
                text = formatTimestamp(message.timestamp),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun TypingBubble() {
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp))
            .background(Color(0xFF1F2C34))
            .padding(12.dp)
    ) {
        // Basit bir animasyonlu nokta efekti eklenebilir, şimdilik statik
        Text("...", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}